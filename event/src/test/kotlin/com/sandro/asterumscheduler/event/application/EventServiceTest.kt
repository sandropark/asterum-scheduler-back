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
    private val rruleShortener = mockk<RruleShortener>()
    private val rruleSuccessor = mockk<RruleSuccessor>()
    private val service = EventService(
        eventRepository,
        eventInstanceRepository,
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

        val newStart = LocalDateTime.of(2026, 6, 10, 14, 0)
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

        val newStart = LocalDateTime.of(2026, 7, 1, 9, 0)
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
    fun `updateTitleThisAndFuture - old rrule 단축 + 신규 event 생성 + target 이후 instance 의 eventId 와 title 리셋`() {
        val startAt = LocalDateTime.of(2026, 5, 1, 10, 0)
        val endAt = startAt.plusHours(1)
        val originalRrule = "FREQ=DAILY;COUNT=5"
        val oldEvent = Event(title = "원본", startAt = startAt, endAt = endAt, rrule = originalRrule)
            .also { it.assignIdForTest(1200L) }
        val i1 = EventInstance(eventId = 1200L, startAt = startAt, endAt = endAt)
            .also { it.assignIdForTest(121L) }
        val i2 = EventInstance(eventId = 1200L, startAt = startAt.plusDays(1), endAt = endAt.plusDays(1))
            .also { it.assignIdForTest(122L) }
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

        val shortenedRrule = "FREQ=DAILY;UNTIL=20260502T095959"
        val successorRrule = "FREQ=DAILY;COUNT=3"

        every { eventInstanceRepository.findById(123L) } returns Optional.of(i3)
        every { eventRepository.findById(1200L) } returns Optional.of(oldEvent)
        every { rruleSuccessor.succeed(originalRrule, oldEvent.startAt, i3.startAt) } returns successorRrule
        every { rruleShortener.shorten(originalRrule, i3.startAt.minusSeconds(1)) } returns shortenedRrule
        every { eventRepository.save(any<Event>()) } answers {
            val e = firstArg<Event>()
            e.assignIdForTest(1300L)
            e
        }
        every {
            eventInstanceRepository.findAllByEventIdAndStartAtGreaterThanEqual(1200L, i3.startAt)
        } returns listOf(i3, i4, i5)

        service.updateTitleThisAndFuture(
            123L,
            EventThisAndFutureTitleUpdateRequest("새 제목"),
        )

        assertEquals(shortenedRrule, oldEvent.rrule)
        assertEquals("원본", oldEvent.title)

        verify(exactly = 1) { rruleSuccessor.succeed(originalRrule, oldEvent.startAt, i3.startAt) }
        verify(exactly = 1) { rruleShortener.shorten(originalRrule, i3.startAt.minusSeconds(1)) }
        verify {
            eventRepository.save(
                match<Event> {
                    it.title == "새 제목" &&
                        it.startAt == i3.startAt &&
                        it.endAt == i3.endAt &&
                        it.rrule == successorRrule
                }
            )
        }

        assertEquals(1200L, i1.eventId)
        assertEquals(1200L, i2.eventId)
        assertEquals(null, i1.title)
        assertEquals(null, i2.title)

        assertEquals(1300L, i3.eventId)
        assertEquals(1300L, i4.eventId)
        assertEquals(1300L, i5.eventId)
        assertEquals(null, i3.title)
        assertEquals(null, i4.title)
        assertEquals(null, i5.title)
    }

    @Test
    fun `updateTitleThisAndFuture - event_rrule 이 null 이면 INVALID_INPUT`() {
        val startAt = LocalDateTime.of(2026, 5, 1, 10, 0)
        val endAt = startAt.plusHours(1)
        val event = Event(title = "단일", startAt = startAt, endAt = endAt, rrule = null)
            .also { it.assignIdForTest(1400L) }
        val instance = EventInstance(eventId = 1400L, startAt = startAt, endAt = endAt)
            .also { it.assignIdForTest(141L) }
        every { eventInstanceRepository.findById(141L) } returns Optional.of(instance)
        every { eventRepository.findById(1400L) } returns Optional.of(event)

        val ex = assertFailsWith<BusinessException> {
            service.updateTitleThisAndFuture(141L, EventThisAndFutureTitleUpdateRequest("x"))
        }
        assertEquals(ErrorCode.INVALID_INPUT, ex.errorCode)
        verify(exactly = 0) { rruleSuccessor.succeed(any(), any(), any()) }
        verify(exactly = 0) { rruleShortener.shorten(any(), any()) }
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
    fun `updateTitleThisAndFuture - event 가 없으면 NOT_FOUND 예외를 던진다`() {
        val startAt = LocalDateTime.of(2026, 5, 1, 10, 0)
        val instance = EventInstance(eventId = 1500L, startAt = startAt, endAt = startAt.plusHours(1))
            .also { it.assignIdForTest(151L) }
        every { eventInstanceRepository.findById(151L) } returns Optional.of(instance)
        every { eventRepository.findById(1500L) } returns Optional.empty()

        val ex = assertFailsWith<BusinessException> {
            service.updateTitleThisAndFuture(151L, EventThisAndFutureTitleUpdateRequest("x"))
        }
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
