package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.common.exception.BusinessException
import com.sandro.asterumscheduler.common.exception.ErrorCode
import com.sandro.asterumscheduler.event.domain.Event
import com.sandro.asterumscheduler.event.domain.EventInstance
import com.sandro.asterumscheduler.event.domain.assignIdForTest
import com.sandro.asterumscheduler.event.infra.EventInstanceRepository
import com.sandro.asterumscheduler.event.infra.EventRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class EventServiceTest {

    private val eventRepository = mockk<EventRepository>()
    private val eventInstanceRepository = mockk<EventInstanceRepository>()
    private val recurrenceExpander = mockk<RecurrenceExpander>()
    private val service = EventService(eventRepository, eventInstanceRepository, recurrenceExpander)

    @Test
    fun `일정을 생성하면 events 와 events_instances 가 동시에 저장된다`() {
        val title = "회의"
        val startAt = LocalDateTime.of(2026, 5, 1, 10, 0)
        val endAt = LocalDateTime.of(2026, 5, 1, 11, 0)

        every { eventRepository.save(any<Event>()) } answers {
            val e = firstArg<Event>()
            e.assignIdForTest(1L)
            e
        }
        every { eventInstanceRepository.save(any<EventInstance>()) } answers { firstArg() }

        val event = service.create(EventCreateRequest(title, startAt, endAt))

        assertEquals(1L, event.id)
        assertEquals(title, event.title)
        assertEquals(startAt, event.startAt)
        assertEquals(endAt, event.endAt)

        verify {
            eventInstanceRepository.save(
                match<EventInstance> {
                    it.eventId == 1L &&
                        it.title == null &&
                        it.startAt == startAt &&
                        it.endAt == endAt
                }
            )
        }
    }

    @Test
    fun `rrule 이 있으면 expander 결과 수만큼 events_instance 가 개별 저장된다`() {
        val title = "주간 회의"
        val startAt = LocalDateTime.of(2026, 5, 4, 10, 0)
        val endAt = startAt.plusHours(1)
        val rrule = "FREQ=WEEKLY;COUNT=3"

        every { eventRepository.save(any<Event>()) } answers {
            val e = firstArg<Event>()
            e.assignIdForTest(7L)
            e
        }
        every { eventInstanceRepository.save(any<EventInstance>()) } answers { firstArg() }
        every { recurrenceExpander.expand(rrule, startAt, endAt, any()) } returns listOf(
            RecurrenceExpander.Occurrence(startAt, endAt),
            RecurrenceExpander.Occurrence(startAt.plusWeeks(1), endAt.plusWeeks(1)),
            RecurrenceExpander.Occurrence(startAt.plusWeeks(2), endAt.plusWeeks(2)),
        )

        val event = service.create(EventCreateRequest(title, startAt, endAt, rrule))

        assertEquals(rrule, event.rrule)
        verify(exactly = 1) { recurrenceExpander.expand(rrule, startAt, endAt, any()) }
        verify(exactly = 3) { eventInstanceRepository.save(any<EventInstance>()) }
        verify {
            eventInstanceRepository.save(
                match<EventInstance> { it.eventId == 7L && it.startAt == startAt.plusWeeks(2) }
            )
        }
    }

    @Test
    fun `rrule 이 null 이면 expander 는 호출되지 않는다`() {
        val startAt = LocalDateTime.of(2026, 5, 1, 10, 0)
        val endAt = startAt.plusHours(1)

        every { eventRepository.save(any<Event>()) } answers {
            val e = firstArg<Event>()
            e.assignIdForTest(9L)
            e
        }
        every { eventInstanceRepository.save(any<EventInstance>()) } answers { firstArg() }

        service.create(EventCreateRequest("단일", startAt, endAt, rrule = null))

        verify(exactly = 0) { recurrenceExpander.expand(any(), any(), any(), any()) }
    }

    @Test
    fun `updateSingle - 같은 시간과 새 제목을 보내면 event_title 만 변경된다`() {
        val (event, instance) = prepareSingle()
        val origStart = event.startAt
        val origEnd = event.endAt

        service.updateSingle(
            instance.id!!,
            EventSingleUpdateRequest(title = "새 제목", startAt = origStart, endAt = origEnd),
        )

        assertEquals("새 제목", event.title)
        assertEquals(origStart, event.startAt)
        assertEquals(origEnd, event.endAt)
        assertEquals(origStart, instance.startAt)
        assertEquals(origEnd, instance.endAt)
    }

    @Test
    fun `updateSingle - 새 시간을 보내면 event 와 instance 시간이 동기화된다`() {
        val (event, instance) = prepareSingle()
        val newStart = LocalDateTime.of(2026, 5, 2, 14, 0)
        val newEnd = newStart.plusHours(2)

        service.updateSingle(
            instance.id!!,
            EventSingleUpdateRequest(title = event.title, startAt = newStart, endAt = newEnd),
        )

        assertEquals(newStart, event.startAt)
        assertEquals(newEnd, event.endAt)
        assertEquals(newStart, instance.startAt)
        assertEquals(newEnd, instance.endAt)
    }

    @Test
    fun `updateSingle - instance 가 없으면 NOT_FOUND 예외를 던진다`() {
        every { eventInstanceRepository.findById(999L) } returns Optional.empty()

        val start = LocalDateTime.of(2026, 5, 1, 10, 0)
        val ex = assertFailsWith<BusinessException> {
            service.updateSingle(
                999L,
                EventSingleUpdateRequest(title = "x", startAt = start, endAt = start.plusHours(1)),
            )
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `deleteSingle - event 와 instance 의 deletedAt 이 동일 시점으로 세팅된다`() {
        val (event, instance) = prepareSingle()
        assertEquals(null, event.deletedAt)
        assertEquals(null, instance.deletedAt)

        service.deleteSingle(instance.id!!)

        assertNotNull(event.deletedAt)
        assertNotNull(instance.deletedAt)
        assertEquals(event.deletedAt, instance.deletedAt)
    }

    @Test
    fun `deleteSingle - instance 가 없으면 NOT_FOUND 예외를 던진다`() {
        every { eventInstanceRepository.findById(999L) } returns Optional.empty()

        val ex = assertFailsWith<BusinessException> { service.deleteSingle(999L) }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }

    private fun prepareSingle(): Pair<Event, EventInstance> {
        val startAt = LocalDateTime.of(2026, 5, 1, 10, 0)
        val endAt = startAt.plusHours(1)
        val event = Event(title = "원본 제목", startAt = startAt, endAt = endAt)
            .also { it.assignIdForTest(100L) }
        val instance = EventInstance(eventId = 100L, startAt = startAt, endAt = endAt, title = null)
            .also { it.assignIdForTest(10L) }
        every { eventInstanceRepository.findById(10L) } returns Optional.of(instance)
        every { eventRepository.findById(100L) } returns Optional.of(event)
        return event to instance
    }
}
