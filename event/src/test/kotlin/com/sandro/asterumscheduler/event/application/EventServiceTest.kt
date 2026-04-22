package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.common.exception.BusinessException
import com.sandro.asterumscheduler.common.exception.ErrorCode
import com.sandro.asterumscheduler.event.domain.Event
import com.sandro.asterumscheduler.event.domain.EventInstance
import com.sandro.asterumscheduler.event.domain.assignIdForTest
import com.sandro.asterumscheduler.common.user.UserInfo
import com.sandro.asterumscheduler.common.user.UserReader
import com.sandro.asterumscheduler.event.domain.EventParticipant
import com.sandro.asterumscheduler.event.domain.InstanceParticipant
import com.sandro.asterumscheduler.event.infra.EventInstanceRepository
import com.sandro.asterumscheduler.event.infra.EventParticipantRepository
import com.sandro.asterumscheduler.event.infra.EventRepository
import com.sandro.asterumscheduler.event.infra.InstanceParticipantRepository
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
    private val eventParticipantRepository = mockk<EventParticipantRepository>()
    private val instanceParticipantRepository = mockk<InstanceParticipantRepository>()
    private val userReader = mockk<UserReader>()
    private val recurrenceExpander = mockk<RecurrenceExpander>()
    private val rruleShortener = mockk<RruleShortener>()
    private val rruleSuccessor = mockk<RruleSuccessor>()
    private val service = EventService(
        eventRepository,
        eventInstanceRepository,
        eventParticipantRepository,
        instanceParticipantRepository,
        userReader,
        recurrenceExpander,
        rruleShortener,
        rruleSuccessor,
    )

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

    @Test
    fun `deleteThisOnly - instance 의 deletedAt 만 세팅되고 event 는 건드리지 않는다`() {
        val (event, instance) = prepareSingle()
        assertEquals(null, event.deletedAt)
        assertEquals(null, instance.deletedAt)

        service.deleteThisOnly(instance.id!!)

        assertNotNull(instance.deletedAt)
        assertEquals(null, event.deletedAt)
    }

    @Test
    fun `deleteThisOnly - instance 가 없으면 NOT_FOUND 예외를 던진다`() {
        every { eventInstanceRepository.findById(999L) } returns Optional.empty()

        val ex = assertFailsWith<BusinessException> { service.deleteThisOnly(999L) }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `deleteAll - event 와 모든 instance 의 deletedAt 이 동일 시점으로 세팅된다`() {
        val startAt = LocalDateTime.of(2026, 5, 1, 10, 0)
        val endAt = startAt.plusHours(1)
        val event = Event(title = "반복", startAt = startAt, endAt = endAt, rrule = "FREQ=DAILY;COUNT=3")
            .also { it.assignIdForTest(200L) }
        val i1 = EventInstance(eventId = 200L, startAt = startAt, endAt = endAt)
            .also { it.assignIdForTest(21L) }
        val i2 = EventInstance(eventId = 200L, startAt = startAt.plusDays(1), endAt = endAt.plusDays(1))
            .also { it.assignIdForTest(22L) }
        val i3 = EventInstance(eventId = 200L, startAt = startAt.plusDays(2), endAt = endAt.plusDays(2))
            .also { it.assignIdForTest(23L) }
        every { eventInstanceRepository.findById(22L) } returns Optional.of(i2)
        every { eventRepository.findById(200L) } returns Optional.of(event)
        every { eventInstanceRepository.findAllByEventId(200L) } returns listOf(i1, i2, i3)

        service.deleteAll(22L)

        assertNotNull(event.deletedAt)
        assertNotNull(i1.deletedAt)
        assertNotNull(i2.deletedAt)
        assertNotNull(i3.deletedAt)
        assertEquals(event.deletedAt, i1.deletedAt)
        assertEquals(event.deletedAt, i2.deletedAt)
        assertEquals(event.deletedAt, i3.deletedAt)
    }

    @Test
    fun `deleteAll - instance 가 없으면 NOT_FOUND 예외를 던진다`() {
        every { eventInstanceRepository.findById(999L) } returns Optional.empty()

        val ex = assertFailsWith<BusinessException> { service.deleteAll(999L) }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `deleteThisAndFuture - target 및 이후 instance 가 soft-delete 되고 이전 instance 와 event 는 건드리지 않는다`() {
        val startAt = LocalDateTime.of(2026, 5, 1, 10, 0)
        val endAt = startAt.plusHours(1)
        val originalRrule = "FREQ=DAILY;COUNT=5"
        val event = Event(title = "반복", startAt = startAt, endAt = endAt, rrule = originalRrule)
            .also { it.assignIdForTest(300L) }
        val i1 = EventInstance(eventId = 300L, startAt = startAt, endAt = endAt)
            .also { it.assignIdForTest(31L) }
        val i2 = EventInstance(eventId = 300L, startAt = startAt.plusDays(1), endAt = endAt.plusDays(1))
            .also { it.assignIdForTest(32L) }
        val i3 = EventInstance(eventId = 300L, startAt = startAt.plusDays(2), endAt = endAt.plusDays(2))
            .also { it.assignIdForTest(33L) }
        val i4 = EventInstance(eventId = 300L, startAt = startAt.plusDays(3), endAt = endAt.plusDays(3))
            .also { it.assignIdForTest(34L) }
        val i5 = EventInstance(eventId = 300L, startAt = startAt.plusDays(4), endAt = endAt.plusDays(4))
            .also { it.assignIdForTest(35L) }
        val newRrule = "FREQ=DAILY;UNTIL=20260503T095959"
        every { eventInstanceRepository.findById(33L) } returns Optional.of(i3)
        every { eventRepository.findById(300L) } returns Optional.of(event)
        every { rruleShortener.shorten(originalRrule, i3.startAt.minusSeconds(1)) } returns newRrule
        every {
            eventInstanceRepository.findAllByEventIdAndStartAtGreaterThanEqual(300L, i3.startAt)
        } returns listOf(i3, i4, i5)

        service.deleteThisAndFuture(33L)

        assertEquals(null, i1.deletedAt)
        assertEquals(null, i2.deletedAt)
        assertNotNull(i3.deletedAt)
        assertNotNull(i4.deletedAt)
        assertNotNull(i5.deletedAt)
        assertEquals(null, event.deletedAt)
        assertEquals(newRrule, event.rrule)
        verify(exactly = 1) { rruleShortener.shorten(originalRrule, i3.startAt.minusSeconds(1)) }
    }

    @Test
    fun `deleteThisAndFuture - event_rrule 이 null 이면 INVALID_INPUT`() {
        val startAt = LocalDateTime.of(2026, 5, 1, 10, 0)
        val endAt = startAt.plusHours(1)
        val event = Event(title = "단일", startAt = startAt, endAt = endAt, rrule = null)
            .also { it.assignIdForTest(400L) }
        val instance = EventInstance(eventId = 400L, startAt = startAt, endAt = endAt)
            .also { it.assignIdForTest(41L) }
        every { eventInstanceRepository.findById(41L) } returns Optional.of(instance)
        every { eventRepository.findById(400L) } returns Optional.of(event)

        val ex = assertFailsWith<BusinessException> { service.deleteThisAndFuture(41L) }
        assertEquals(ErrorCode.INVALID_INPUT, ex.errorCode)
        verify(exactly = 0) { rruleShortener.shorten(any(), any()) }
    }

    @Test
    fun `deleteThisAndFuture - instance 가 없으면 NOT_FOUND 예외를 던진다`() {
        every { eventInstanceRepository.findById(999L) } returns Optional.empty()

        val ex = assertFailsWith<BusinessException> { service.deleteThisAndFuture(999L) }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `updateThisOnly - title 과 시간이 instance 에만 반영되고 event 는 건드리지 않는다`() {
        val origStart = LocalDateTime.of(2026, 5, 1, 10, 0)
        val origEnd = origStart.plusHours(1)
        val event = Event(title = "원본", startAt = origStart, endAt = origEnd, rrule = "FREQ=DAILY;COUNT=3")
            .also { it.assignIdForTest(600L) }
        val instance = EventInstance(eventId = 600L, startAt = origStart, endAt = origEnd, title = null)
            .also { it.assignIdForTest(61L) }
        every { eventInstanceRepository.findById(61L) } returns Optional.of(instance)

        val newStart = LocalDateTime.of(2026, 5, 2, 14, 0)
        val newEnd = newStart.plusHours(2)
        service.updateThisOnly(61L, EventThisOnlyUpdateRequest("오버라이드", newStart, newEnd))

        assertEquals("오버라이드", instance.title)
        assertEquals(newStart, instance.startAt)
        assertEquals(newEnd, instance.endAt)
        assertEquals("원본", event.title)
        assertEquals(origStart, event.startAt)
        assertEquals(origEnd, event.endAt)
    }

    @Test
    fun `updateThisOnly - instance 가 없으면 NOT_FOUND 예외를 던진다`() {
        every { eventInstanceRepository.findById(999L) } returns Optional.empty()

        val start = LocalDateTime.of(2026, 5, 1, 10, 0)
        val ex = assertFailsWith<BusinessException> {
            service.updateThisOnly(
                999L,
                EventThisOnlyUpdateRequest("x", start, start.plusHours(1)),
            )
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `updateAllTitle - event_title 이 변경되고 모든 instance 의 title 이 null 로 리셋된다`() {
        val start = LocalDateTime.of(2026, 5, 1, 10, 0)
        val end = start.plusHours(1)
        val event = Event(title = "원본", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=3")
            .also { it.assignIdForTest(700L) }
        val i1 = EventInstance(eventId = 700L, startAt = start, endAt = end, title = null)
            .also { it.assignIdForTest(71L) }
        val i2 = EventInstance(
            eventId = 700L,
            startAt = start.plusDays(1),
            endAt = end.plusDays(1),
            title = "오버라이드",
        ).also { it.assignIdForTest(72L) }
        val i3 = EventInstance(eventId = 700L, startAt = start.plusDays(2), endAt = end.plusDays(2), title = null)
            .also { it.assignIdForTest(73L) }
        every { eventInstanceRepository.findById(72L) } returns Optional.of(i2)
        every { eventRepository.findById(700L) } returns Optional.of(event)
        every { eventInstanceRepository.findAllByEventId(700L) } returns listOf(i1, i2, i3)

        service.updateAllTitle(72L, EventAllTitleUpdateRequest("새 제목"))

        assertEquals("새 제목", event.title)
        assertEquals(start, event.startAt)
        assertEquals(end, event.endAt)
        assertEquals(null, i1.title)
        assertEquals(null, i2.title)
        assertEquals(null, i3.title)
    }

    @Test
    fun `updateAllTitle - instance 가 없으면 NOT_FOUND 예외를 던진다`() {
        every { eventInstanceRepository.findById(999L) } returns Optional.empty()

        val ex = assertFailsWith<BusinessException> {
            service.updateAllTitle(999L, EventAllTitleUpdateRequest("x"))
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `updateAllTitle - event 가 없으면 NOT_FOUND 예외를 던진다`() {
        val start = LocalDateTime.of(2026, 5, 1, 10, 0)
        val instance = EventInstance(eventId = 800L, startAt = start, endAt = start.plusHours(1))
            .also { it.assignIdForTest(81L) }
        every { eventInstanceRepository.findById(81L) } returns Optional.of(instance)
        every { eventRepository.findById(800L) } returns Optional.empty()

        val ex = assertFailsWith<BusinessException> {
            service.updateAllTitle(81L, EventAllTitleUpdateRequest("x"))
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `updateAllTime - event 필드가 갱신되고 활성 instance 가 삭제된 뒤 rrule 로 재생성된다`() {
        val origStart = LocalDateTime.of(2026, 5, 1, 10, 0)
        val origEnd = origStart.plusHours(1)
        val event = Event(
            title = "원본",
            startAt = origStart,
            endAt = origEnd,
            rrule = "FREQ=DAILY;COUNT=3",
        ).also { it.assignIdForTest(900L) }
        val active1 = EventInstance(eventId = 900L, startAt = origStart, endAt = origEnd)
            .also { it.assignIdForTest(91L) }
        val active2 = EventInstance(eventId = 900L, startAt = origStart.plusDays(1), endAt = origEnd.plusDays(1))
            .also { it.assignIdForTest(92L) }

        val newStart = LocalDateTime.of(2026, 5, 1, 14, 0)
        val newEnd = newStart.plusHours(2)
        val newRrule = "FREQ=WEEKLY;COUNT=2"

        every { eventInstanceRepository.findById(91L) } returns Optional.of(active1)
        every { eventRepository.findById(900L) } returns Optional.of(event)
        every { eventInstanceRepository.findAllByEventId(900L) } returns listOf(active1, active2)
        every { eventInstanceRepository.deleteAll(listOf(active1, active2)) } returns Unit
        every { recurrenceExpander.expand(newRrule, newStart, newEnd, any()) } returns listOf(
            RecurrenceExpander.Occurrence(newStart, newEnd),
            RecurrenceExpander.Occurrence(newStart.plusWeeks(1), newEnd.plusWeeks(1)),
        )
        every { eventInstanceRepository.save(any<EventInstance>()) } answers { firstArg() }

        service.updateAllTime(91L, EventAllTimeUpdateRequest(newStart, newEnd, newRrule))

        assertEquals(newStart, event.startAt)
        assertEquals(newEnd, event.endAt)
        assertEquals(newRrule, event.rrule)
        verify(exactly = 1) { eventInstanceRepository.deleteAll(listOf(active1, active2)) }
        verify(exactly = 2) { eventInstanceRepository.save(any<EventInstance>()) }
        verify {
            eventInstanceRepository.save(
                match<EventInstance> { it.eventId == 900L && it.title == null && it.startAt == newStart && it.endAt == newEnd }
            )
        }
        verify {
            eventInstanceRepository.save(
                match<EventInstance> { it.eventId == 900L && it.startAt == newStart.plusWeeks(1) && it.endAt == newEnd.plusWeeks(1) }
            )
        }
    }

    @Test
    fun `updateAllTime - rrule 이 null 이면 expander 호출 없이 단일 인스턴스로 재생성된다`() {
        val origStart = LocalDateTime.of(2026, 5, 1, 10, 0)
        val origEnd = origStart.plusHours(1)
        val event = Event(
            title = "원본",
            startAt = origStart,
            endAt = origEnd,
            rrule = "FREQ=DAILY;COUNT=3",
        ).also { it.assignIdForTest(1000L) }
        val active = EventInstance(eventId = 1000L, startAt = origStart, endAt = origEnd)
            .also { it.assignIdForTest(101L) }

        val newStart = LocalDateTime.of(2026, 5, 1, 9, 0)
        val newEnd = newStart.plusHours(1)

        every { eventInstanceRepository.findById(101L) } returns Optional.of(active)
        every { eventRepository.findById(1000L) } returns Optional.of(event)
        every { eventInstanceRepository.findAllByEventId(1000L) } returns listOf(active)
        every { eventInstanceRepository.deleteAll(listOf(active)) } returns Unit
        every { eventInstanceRepository.save(any<EventInstance>()) } answers { firstArg() }

        service.updateAllTime(101L, EventAllTimeUpdateRequest(newStart, newEnd, rrule = null))

        assertEquals(newStart, event.startAt)
        assertEquals(newEnd, event.endAt)
        assertEquals(null, event.rrule)
        verify(exactly = 0) { recurrenceExpander.expand(any(), any(), any(), any()) }
        verify(exactly = 1) { eventInstanceRepository.deleteAll(listOf(active)) }
        verify(exactly = 1) {
            eventInstanceRepository.save(
                match<EventInstance> { it.eventId == 1000L && it.title == null && it.startAt == newStart && it.endAt == newEnd }
            )
        }
    }

    @Test
    fun `updateAllTime - instance 가 없으면 NOT_FOUND 예외를 던진다`() {
        every { eventInstanceRepository.findById(999L) } returns Optional.empty()

        val start = LocalDateTime.of(2026, 5, 1, 10, 0)
        val ex = assertFailsWith<BusinessException> {
            service.updateAllTime(999L, EventAllTimeUpdateRequest(start, start.plusHours(1), rrule = null))
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `updateAllTime - event 가 없으면 NOT_FOUND 예외를 던진다`() {
        val start = LocalDateTime.of(2026, 5, 1, 10, 0)
        val instance = EventInstance(eventId = 1100L, startAt = start, endAt = start.plusHours(1))
            .also { it.assignIdForTest(111L) }
        every { eventInstanceRepository.findById(111L) } returns Optional.of(instance)
        every { eventRepository.findById(1100L) } returns Optional.empty()

        val ex = assertFailsWith<BusinessException> {
            service.updateAllTime(111L, EventAllTimeUpdateRequest(start, start.plusHours(1), rrule = null))
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `deleteThisAndFuture - event 가 없으면 NOT_FOUND 예외를 던진다`() {
        val startAt = LocalDateTime.of(2026, 5, 1, 10, 0)
        val instance = EventInstance(eventId = 500L, startAt = startAt, endAt = startAt.plusHours(1))
            .also { it.assignIdForTest(51L) }
        every { eventInstanceRepository.findById(51L) } returns Optional.of(instance)
        every { eventRepository.findById(500L) } returns Optional.empty()

        val ex = assertFailsWith<BusinessException> { service.deleteThisAndFuture(51L) }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `updateTitleThisAndFuture - target 이후 instance 들의 title 을 일괄 변경한다`() {
        val startAt = LocalDateTime.of(2026, 5, 1, 10, 0)
        val endAt = startAt.plusHours(1)
        val i3 = EventInstance(eventId = 1200L, startAt = startAt.plusDays(2), endAt = endAt.plusDays(2))
            .also { it.assignIdForTest(123L) }
        val i4 = EventInstance(
            eventId = 1200L,
            startAt = startAt.plusDays(3),
            endAt = endAt.plusDays(3),
            title = "오버라이드",
        ).also { it.assignIdForTest(124L) }
        val i5 = EventInstance(eventId = 1200L, startAt = startAt.plusDays(4), endAt = endAt.plusDays(4))
            .also { it.assignIdForTest(125L) }

        every { eventInstanceRepository.findById(123L) } returns Optional.of(i3)
        every {
            eventInstanceRepository.findAllByEventIdAndStartAtGreaterThanEqual(1200L, i3.startAt)
        } returns listOf(i3, i4, i5)

        service.updateTitleThisAndFuture(123L, EventThisAndFutureTitleUpdateRequest("새 제목"))

        assertEquals("새 제목", i3.title)
        assertEquals("새 제목", i4.title)
        assertEquals("새 제목", i5.title)
    }

    @Test
    fun `updateTitleThisAndFuture - instance 가 없으면 NOT_FOUND 예외를 던진다`() {
        every { eventInstanceRepository.findById(999L) } returns Optional.empty()

        val ex = assertFailsWith<BusinessException> {
            service.updateTitleThisAndFuture(999L, EventThisAndFutureTitleUpdateRequest("x"))
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `updateTimeThisAndFuture - old rrule 단축 + 신규 event 생성 + target 이후 instance hard-delete 후 expander 결과로 재생성`() {
        val origStart = LocalDateTime.of(2026, 5, 1, 10, 0)
        val origEnd = origStart.plusHours(1)
        val originalRrule = "FREQ=DAILY;COUNT=5"
        val oldEvent = Event(title = "원본", startAt = origStart, endAt = origEnd, rrule = originalRrule)
            .also { it.assignIdForTest(1600L) }
        val i3 = EventInstance(eventId = 1600L, startAt = origStart.plusDays(2), endAt = origEnd.plusDays(2))
            .also { it.assignIdForTest(163L) }
        val i4 = EventInstance(eventId = 1600L, startAt = origStart.plusDays(3), endAt = origEnd.plusDays(3))
            .also { it.assignIdForTest(164L) }
        val i5 = EventInstance(eventId = 1600L, startAt = origStart.plusDays(4), endAt = origEnd.plusDays(4))
            .also { it.assignIdForTest(165L) }

        val newStart = LocalDateTime.of(2026, 6, 1, 14, 0)
        val newEnd = newStart.plusHours(2)
        val newRrule = "FREQ=WEEKLY;COUNT=2"
        val shortenedRrule = "FREQ=DAILY;UNTIL=20260502T095959"

        every { eventInstanceRepository.findById(163L) } returns Optional.of(i3)
        every { eventRepository.findById(1600L) } returns Optional.of(oldEvent)
        every { rruleShortener.shorten(originalRrule, i3.startAt.minusSeconds(1)) } returns shortenedRrule
        every { eventRepository.save(any<Event>()) } answers {
            val e = firstArg<Event>()
            e.assignIdForTest(1700L)
            e
        }
        every {
            eventInstanceRepository.findAllByEventIdAndStartAtGreaterThanEqual(1600L, i3.startAt)
        } returns listOf(i3, i4, i5)
        every { eventInstanceRepository.deleteAll(listOf(i3, i4, i5)) } returns Unit
        every { recurrenceExpander.expand(newRrule, newStart, newEnd, any()) } returns listOf(
            RecurrenceExpander.Occurrence(newStart, newEnd),
            RecurrenceExpander.Occurrence(newStart.plusWeeks(1), newEnd.plusWeeks(1)),
        )
        every { eventInstanceRepository.save(any<EventInstance>()) } answers { firstArg() }

        service.updateTimeThisAndFuture(
            163L,
            EventThisAndFutureTimeUpdateRequest(newStart, newEnd, newRrule),
        )

        assertEquals(shortenedRrule, oldEvent.rrule)
        assertEquals("원본", oldEvent.title)

        verify(exactly = 1) { eventInstanceRepository.deleteAll(listOf(i3, i4, i5)) }
        verify {
            eventRepository.save(
                match<Event> {
                    it.title == "원본" &&
                        it.startAt == newStart &&
                        it.endAt == newEnd &&
                        it.rrule == newRrule
                }
            )
        }
        verify(exactly = 2) { eventInstanceRepository.save(any<EventInstance>()) }
        verify {
            eventInstanceRepository.save(
                match<EventInstance> {
                    it.eventId == 1700L && it.title == null && it.startAt == newStart && it.endAt == newEnd
                }
            )
        }
        verify {
            eventInstanceRepository.save(
                match<EventInstance> {
                    it.eventId == 1700L && it.startAt == newStart.plusWeeks(1) && it.endAt == newEnd.plusWeeks(1)
                }
            )
        }
    }

    @Test
    fun `updateTimeThisAndFuture - request_rrule 이 null 이면 expander 호출 없이 단일 instance 로 재생성`() {
        val origStart = LocalDateTime.of(2026, 5, 1, 10, 0)
        val origEnd = origStart.plusHours(1)
        val originalRrule = "FREQ=DAILY;COUNT=3"
        val oldEvent = Event(title = "원본", startAt = origStart, endAt = origEnd, rrule = originalRrule)
            .also { it.assignIdForTest(1800L) }
        val i2 = EventInstance(eventId = 1800L, startAt = origStart.plusDays(1), endAt = origEnd.plusDays(1))
            .also { it.assignIdForTest(182L) }
        val i3 = EventInstance(eventId = 1800L, startAt = origStart.plusDays(2), endAt = origEnd.plusDays(2))
            .also { it.assignIdForTest(183L) }

        val newStart = LocalDateTime.of(2026, 7, 1, 9, 0)
        val newEnd = newStart.plusHours(1)
        val shortenedRrule = "FREQ=DAILY;UNTIL=20260501T095959"

        every { eventInstanceRepository.findById(182L) } returns Optional.of(i2)
        every { eventRepository.findById(1800L) } returns Optional.of(oldEvent)
        every { rruleShortener.shorten(originalRrule, i2.startAt.minusSeconds(1)) } returns shortenedRrule
        every { eventRepository.save(any<Event>()) } answers {
            val e = firstArg<Event>()
            e.assignIdForTest(1900L)
            e
        }
        every {
            eventInstanceRepository.findAllByEventIdAndStartAtGreaterThanEqual(1800L, i2.startAt)
        } returns listOf(i2, i3)
        every { eventInstanceRepository.deleteAll(listOf(i2, i3)) } returns Unit
        every { eventInstanceRepository.save(any<EventInstance>()) } answers { firstArg() }

        service.updateTimeThisAndFuture(
            182L,
            EventThisAndFutureTimeUpdateRequest(newStart, newEnd, rrule = null),
        )

        verify(exactly = 0) { recurrenceExpander.expand(any(), any(), any(), any()) }
        verify(exactly = 1) {
            eventInstanceRepository.save(
                match<EventInstance> { it.eventId == 1900L && it.startAt == newStart && it.endAt == newEnd }
            )
        }
        verify {
            eventRepository.save(match<Event> { it.rrule == null && it.title == "원본" })
        }
    }

    @Test
    fun `updateTimeThisAndFuture - oldEvent_rrule 이 null 이면 INVALID_INPUT`() {
        val startAt = LocalDateTime.of(2026, 5, 1, 10, 0)
        val endAt = startAt.plusHours(1)
        val event = Event(title = "단일", startAt = startAt, endAt = endAt, rrule = null)
            .also { it.assignIdForTest(2000L) }
        val instance = EventInstance(eventId = 2000L, startAt = startAt, endAt = endAt)
            .also { it.assignIdForTest(201L) }
        every { eventInstanceRepository.findById(201L) } returns Optional.of(instance)
        every { eventRepository.findById(2000L) } returns Optional.of(event)

        val ex = assertFailsWith<BusinessException> {
            service.updateTimeThisAndFuture(
                201L,
                EventThisAndFutureTimeUpdateRequest(startAt, endAt, rrule = null),
            )
        }
        assertEquals(ErrorCode.INVALID_INPUT, ex.errorCode)
        verify(exactly = 0) { rruleShortener.shorten(any(), any()) }
    }

    @Test
    fun `updateTimeThisAndFuture - instance 가 없으면 NOT_FOUND 예외를 던진다`() {
        every { eventInstanceRepository.findById(999L) } returns Optional.empty()

        val start = LocalDateTime.of(2026, 5, 1, 10, 0)
        val ex = assertFailsWith<BusinessException> {
            service.updateTimeThisAndFuture(
                999L,
                EventThisAndFutureTimeUpdateRequest(start, start.plusHours(1), rrule = null),
            )
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `updateTimeThisAndFuture - event 가 없으면 NOT_FOUND 예외를 던진다`() {
        val startAt = LocalDateTime.of(2026, 5, 1, 10, 0)
        val instance = EventInstance(eventId = 2100L, startAt = startAt, endAt = startAt.plusHours(1))
            .also { it.assignIdForTest(211L) }
        every { eventInstanceRepository.findById(211L) } returns Optional.of(instance)
        every { eventRepository.findById(2100L) } returns Optional.empty()

        val ex = assertFailsWith<BusinessException> {
            service.updateTimeThisAndFuture(
                211L,
                EventThisAndFutureTimeUpdateRequest(startAt, startAt.plusHours(1), rrule = null),
            )
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }

    private fun recurringEventAndInstance(rrule: String?): Pair<Event, EventInstance> {
        val startAt = LocalDateTime.of(2026, 5, 1, 10, 0)
        val endAt = startAt.plusHours(1)
        val event = Event(title = "반복 일정", startAt = startAt, endAt = endAt, rrule = rrule)
            .also { it.assignIdForTest(100L) }
        val instance = EventInstance(eventId = 100L, startAt = startAt, endAt = endAt)
            .also { it.assignIdForTest(10L) }
        every { eventInstanceRepository.findById(10L) } returns Optional.of(instance)
        every { eventRepository.findById(100L) } returns Optional.of(event)
        return event to instance
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

    @Test
    fun `참여자 포함 생성 — eventParticipantRepository save 가 userIds 수만큼 호출된다`() {
        val startAt = LocalDateTime.of(2026, 5, 1, 10, 0)
        val endAt = startAt.plusHours(1)

        every { eventRepository.save(any<Event>()) } answers {
            firstArg<Event>().also { it.assignIdForTest(1L) }
        }
        every { eventInstanceRepository.save(any<EventInstance>()) } answers { firstArg() }
        every { eventParticipantRepository.save(any<EventParticipant>()) } answers { firstArg() }
        every { userReader.findExistingIds(setOf(10L, 20L)) } returns setOf(10L, 20L)

        service.create(EventCreateRequest(title = "회의", startAt = startAt, endAt = endAt, userIds = setOf(10L, 20L)))

        verify(exactly = 1) { eventParticipantRepository.save(match { it.eventId == 1L && it.userId == 10L }) }
        verify(exactly = 1) { eventParticipantRepository.save(match { it.eventId == 1L && it.userId == 20L }) }
    }

    @Test
    fun `THIS_AND_FUTURE 참여자 수정 성공 — old rrule 단축 + 신규 event 생성 + save N회 + target 이후 instance eventId 변경 + hasOverrideParticipants=false`() {
        val startAt = LocalDateTime.of(2026, 5, 1, 10, 0)
        val endAt = startAt.plusHours(1)
        val originalRrule = "FREQ=DAILY;COUNT=5"
        val oldEvent = Event(title = "반복", startAt = startAt, endAt = endAt, rrule = originalRrule)
            .also { it.assignIdForTest(100L) }
        val i1 = EventInstance(eventId = 100L, startAt = startAt, endAt = endAt)
            .also { it.assignIdForTest(10L) }
        val i2 = EventInstance(eventId = 100L, startAt = startAt.plusDays(1), endAt = endAt.plusDays(1))
            .also { it.assignIdForTest(11L) }
        val i3 = EventInstance(eventId = 100L, startAt = startAt.plusDays(2), endAt = endAt.plusDays(2))
            .also { it.assignIdForTest(12L); it.hasOverrideParticipants = true }
        val i4 = EventInstance(eventId = 100L, startAt = startAt.plusDays(3), endAt = endAt.plusDays(3))
            .also { it.assignIdForTest(13L) }

        val shortenedRrule = "FREQ=DAILY;UNTIL=20260502T095959"
        val successorRrule = "FREQ=DAILY;COUNT=3"

        every { eventInstanceRepository.findById(12L) } returns Optional.of(i3)
        every { eventRepository.findById(100L) } returns Optional.of(oldEvent)
        every { userReader.findExistingIds(setOf(10L, 20L)) } returns setOf(10L, 20L)
        every { rruleSuccessor.succeed(originalRrule, oldEvent.startAt, i3.startAt) } returns successorRrule
        every { rruleShortener.shorten(originalRrule, i3.startAt.minusSeconds(1)) } returns shortenedRrule
        every { eventRepository.save(any<Event>()) } answers {
            firstArg<Event>().also { it.assignIdForTest(200L) }
        }
        every { eventParticipantRepository.save(any<EventParticipant>()) } answers { firstArg() }
        every {
            eventInstanceRepository.findAllByEventIdAndStartAtGreaterThanEqual(100L, i3.startAt)
        } returns listOf(i3, i4)
        every { instanceParticipantRepository.deleteAllByInstanceId(12L) } returns Unit
        every { instanceParticipantRepository.deleteAllByInstanceId(13L) } returns Unit

        service.updateParticipantsThisAndFuture(12L, EventThisAndFutureParticipantsUpdateRequest(setOf(10L, 20L)))

        assertEquals(shortenedRrule, oldEvent.rrule)
        verify(exactly = 1) { rruleSuccessor.succeed(originalRrule, oldEvent.startAt, i3.startAt) }
        verify(exactly = 1) { rruleShortener.shorten(originalRrule, i3.startAt.minusSeconds(1)) }
        verify {
            eventRepository.save(match<Event> {
                it.title == "반복" && it.startAt == i3.startAt && it.rrule == successorRrule
            })
        }
        verify(exactly = 1) { eventParticipantRepository.save(match { it.eventId == 200L && it.userId == 10L }) }
        verify(exactly = 1) { eventParticipantRepository.save(match { it.eventId == 200L && it.userId == 20L }) }
        assertEquals(200L, i3.eventId)
        assertEquals(200L, i4.eventId)
        assertEquals(100L, i1.eventId)
        assertEquals(100L, i2.eventId)
        assertEquals(false, i3.hasOverrideParticipants)
        assertEquals(false, i4.hasOverrideParticipants)
        verify(exactly = 1) { instanceParticipantRepository.deleteAllByInstanceId(12L) }
        verify(exactly = 1) { instanceParticipantRepository.deleteAllByInstanceId(13L) }
    }

    @Test
    fun `THIS_AND_FUTURE 참여자 수정 — rrule == null → INVALID_INPUT`() {
        recurringEventAndInstance(rrule = null)

        val ex = assertFailsWith<BusinessException> {
            service.updateParticipantsThisAndFuture(10L, EventThisAndFutureParticipantsUpdateRequest(setOf(1L)))
        }
        assertEquals(ErrorCode.INVALID_INPUT, ex.errorCode)
    }

    @Test
    fun `THIS_AND_FUTURE 참여자 수정 — 존재하지 않는 instance → NOT_FOUND`() {
        every { eventInstanceRepository.findById(999L) } returns Optional.empty()

        val ex = assertFailsWith<BusinessException> {
            service.updateParticipantsThisAndFuture(999L, EventThisAndFutureParticipantsUpdateRequest(setOf(1L)))
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `THIS_AND_FUTURE 참여자 수정 — 존재하지 않는 userId → NOT_FOUND`() {
        recurringEventAndInstance(rrule = "FREQ=DAILY;COUNT=3")
        every { userReader.findExistingIds(setOf(10L, 99L)) } returns setOf(10L)

        val ex = assertFailsWith<BusinessException> {
            service.updateParticipantsThisAndFuture(10L, EventThisAndFutureParticipantsUpdateRequest(setOf(10L, 99L)))
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `ALL 참여자 수정 성공 — deleteAllByEventId + save N회 + 모든 instance hasOverrideParticipants=false + deleteAllByInstanceIdIn`() {
        val startAt = LocalDateTime.of(2026, 5, 1, 10, 0)
        val endAt = startAt.plusHours(1)
        val event = Event(title = "반복", startAt = startAt, endAt = endAt, rrule = "FREQ=DAILY;COUNT=3")
            .also { it.assignIdForTest(100L) }
        val i1 = EventInstance(eventId = 100L, startAt = startAt, endAt = endAt)
            .also { it.assignIdForTest(10L); it.hasOverrideParticipants = true }
        val i2 = EventInstance(eventId = 100L, startAt = startAt.plusDays(1), endAt = endAt.plusDays(1))
            .also { it.assignIdForTest(11L); it.hasOverrideParticipants = true }
        every { eventInstanceRepository.findById(10L) } returns Optional.of(i1)
        every { eventRepository.findById(100L) } returns Optional.of(event)
        every { userReader.findExistingIds(setOf(10L, 20L)) } returns setOf(10L, 20L)
        every { eventParticipantRepository.deleteAllByEventId(100L) } returns Unit
        every { eventParticipantRepository.save(any<EventParticipant>()) } answers { firstArg() }
        every { eventInstanceRepository.findAllByEventId(100L) } returns listOf(i1, i2)
        every { instanceParticipantRepository.deleteAllByInstanceIdIn(listOf(10L, 11L)) } returns Unit

        service.updateParticipantsAll(10L, EventAllParticipantsUpdateRequest(setOf(10L, 20L)))

        verify(exactly = 1) { eventParticipantRepository.deleteAllByEventId(100L) }
        verify(exactly = 1) { eventParticipantRepository.save(match { it.eventId == 100L && it.userId == 10L }) }
        verify(exactly = 1) { eventParticipantRepository.save(match { it.eventId == 100L && it.userId == 20L }) }
        verify(exactly = 1) { instanceParticipantRepository.deleteAllByInstanceIdIn(listOf(10L, 11L)) }
        assertEquals(false, i1.hasOverrideParticipants)
        assertEquals(false, i2.hasOverrideParticipants)
    }

    @Test
    fun `ALL 참여자 수정 — 존재하지 않는 instance → NOT_FOUND`() {
        every { eventInstanceRepository.findById(999L) } returns Optional.empty()

        val ex = assertFailsWith<BusinessException> {
            service.updateParticipantsAll(999L, EventAllParticipantsUpdateRequest(setOf(1L)))
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `ALL 참여자 수정 — 존재하지 않는 userId → NOT_FOUND`() {
        recurringEventAndInstance(rrule = "FREQ=DAILY;COUNT=3")
        every { userReader.findExistingIds(setOf(10L, 99L)) } returns setOf(10L)

        val ex = assertFailsWith<BusinessException> {
            service.updateParticipantsAll(10L, EventAllParticipantsUpdateRequest(setOf(10L, 99L)))
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `THIS_ONLY 참여자 수정 성공 — deleteAllByInstanceId 1회 + save N회 + hasOverrideParticipants=true`() {
        val (event, instance) = recurringEventAndInstance(rrule = "FREQ=DAILY;COUNT=3")
        every { userReader.findExistingIds(setOf(10L, 20L)) } returns setOf(10L, 20L)
        every { instanceParticipantRepository.deleteAllByInstanceId(10L) } returns Unit
        every { instanceParticipantRepository.save(any<InstanceParticipant>()) } answers { firstArg() }

        service.updateParticipantsThisOnly(10L, EventThisOnlyParticipantsUpdateRequest(setOf(10L, 20L)))

        verify(exactly = 1) { instanceParticipantRepository.deleteAllByInstanceId(10L) }
        verify(exactly = 1) { instanceParticipantRepository.save(match { it.instanceId == 10L && it.userId == 10L }) }
        verify(exactly = 1) { instanceParticipantRepository.save(match { it.instanceId == 10L && it.userId == 20L }) }
        assertEquals(true, instance.hasOverrideParticipants)
    }

    @Test
    fun `단일 일정 THIS_ONLY 참여자 수정 — INVALID_INPUT`() {
        recurringEventAndInstance(rrule = null)

        val ex = assertFailsWith<BusinessException> {
            service.updateParticipantsThisOnly(10L, EventThisOnlyParticipantsUpdateRequest(setOf(1L)))
        }
        assertEquals(ErrorCode.INVALID_INPUT, ex.errorCode)
    }

    @Test
    fun `존재하지 않는 instance THIS_ONLY 참여자 수정 — NOT_FOUND`() {
        every { eventInstanceRepository.findById(999L) } returns Optional.empty()

        val ex = assertFailsWith<BusinessException> {
            service.updateParticipantsThisOnly(999L, EventThisOnlyParticipantsUpdateRequest(setOf(1L)))
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `존재하지 않는 userId THIS_ONLY 참여자 수정 — NOT_FOUND`() {
        recurringEventAndInstance(rrule = "FREQ=DAILY;COUNT=3")
        every { userReader.findExistingIds(setOf(10L, 99L)) } returns setOf(10L)

        val ex = assertFailsWith<BusinessException> {
            service.updateParticipantsThisOnly(10L, EventThisOnlyParticipantsUpdateRequest(setOf(10L, 99L)))
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }

    @Test
    fun `존재하지 않는 userId 포함 생성 시 NOT_FOUND`() {
        val startAt = LocalDateTime.of(2026, 5, 1, 10, 0)
        val endAt = startAt.plusHours(1)

        every { eventRepository.save(any<Event>()) } answers {
            firstArg<Event>().also { it.assignIdForTest(1L) }
        }
        every { eventInstanceRepository.save(any<EventInstance>()) } answers { firstArg() }
        every { userReader.findExistingIds(setOf(10L, 99L)) } returns setOf(10L)

        val ex = assertFailsWith<BusinessException> {
            service.create(EventCreateRequest(title = "회의", startAt = startAt, endAt = endAt, userIds = setOf(10L, 99L)))
        }
        assertEquals(ErrorCode.NOT_FOUND, ex.errorCode)
    }
}
