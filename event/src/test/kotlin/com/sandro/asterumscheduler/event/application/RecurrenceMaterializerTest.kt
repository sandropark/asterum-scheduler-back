package com.sandro.asterumscheduler.event.application

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

class RecurrenceMaterializerTest {

    private val eventRepository = mockk<EventRepository>()
    private val eventInstanceRepository = mockk<EventInstanceRepository>()
    private val recurrenceExpander = mockk<RecurrenceExpander>()
    private val materializer = RecurrenceMaterializer(
        eventRepository,
        eventInstanceRepository,
        recurrenceExpander,
    )

    @Test
    fun `무기한 rrule 에 대해 maxStart 이후 occurrence 만 새로 저장한다`() {
        val start = LocalDateTime.of(2026, 1, 1, 10, 0)
        val end = start.plusHours(1)
        val event = Event(title = "무기한", startAt = start, endAt = end, rrule = "FREQ=DAILY")
            .also { it.assignIdForTest(10L) }
        val now = LocalDateTime.of(2027, 1, 1, 0, 0)
        val maxStart = LocalDateTime.of(2026, 6, 1, 10, 0)

        every { eventRepository.findAllByRruleIsNotNull() } returns listOf(event)
        every { eventInstanceRepository.findMaxStartAtByEventIdIncludingDeleted(10L) } returns maxStart
        every { recurrenceExpander.expand("FREQ=DAILY", start, end, now) } returns listOf(
            RecurrenceExpander.Occurrence(maxStart.minusDays(1), maxStart.minusDays(1).plusHours(1)),
            RecurrenceExpander.Occurrence(maxStart, maxStart.plusHours(1)),
            RecurrenceExpander.Occurrence(maxStart.plusDays(1), maxStart.plusDays(1).plusHours(1)),
            RecurrenceExpander.Occurrence(maxStart.plusDays(2), maxStart.plusDays(2).plusHours(1)),
        )
        every { eventInstanceRepository.save(any<EventInstance>()) } answers { firstArg() }

        materializer.materialize(now)

        verify(exactly = 2) { eventInstanceRepository.save(any<EventInstance>()) }
        verify {
            eventInstanceRepository.save(
                match<EventInstance> {
                    it.eventId == 10L && it.startAt == maxStart.plusDays(1)
                }
            )
        }
        verify {
            eventInstanceRepository.save(
                match<EventInstance> {
                    it.eventId == 10L && it.startAt == maxStart.plusDays(2)
                }
            )
        }
    }

    @Test
    fun `COUNT 한정 rrule 이 이미 다 생성되어 있으면 추가 저장 없음`() {
        val start = LocalDateTime.of(2026, 1, 1, 10, 0)
        val end = start.plusHours(1)
        val event = Event(title = "한정", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=3")
            .also { it.assignIdForTest(20L) }
        val now = LocalDateTime.of(2027, 1, 1, 0, 0)
        val maxStart = start.plusDays(2)

        every { eventRepository.findAllByRruleIsNotNull() } returns listOf(event)
        every { eventInstanceRepository.findMaxStartAtByEventIdIncludingDeleted(20L) } returns maxStart
        every { recurrenceExpander.expand("FREQ=DAILY;COUNT=3", start, end, now) } returns listOf(
            RecurrenceExpander.Occurrence(start, end),
            RecurrenceExpander.Occurrence(start.plusDays(1), end.plusDays(1)),
            RecurrenceExpander.Occurrence(start.plusDays(2), end.plusDays(2)),
        )

        materializer.materialize(now)

        verify(exactly = 0) { eventInstanceRepository.save(any<EventInstance>()) }
    }

    @Test
    fun `rrule null event 는 쿼리 단계에서 필터링되어 처리되지 않는다`() {
        val now = LocalDateTime.of(2027, 1, 1, 0, 0)
        every { eventRepository.findAllByRruleIsNotNull() } returns emptyList()

        materializer.materialize(now)

        verify(exactly = 0) { recurrenceExpander.expand(any(), any(), any(), any()) }
        verify(exactly = 0) { eventInstanceRepository.save(any<EventInstance>()) }
    }

    @Test
    fun `maxStart 조회 결과가 null 이면 해당 event 는 스킵`() {
        val start = LocalDateTime.of(2026, 1, 1, 10, 0)
        val end = start.plusHours(1)
        val event = Event(title = "외톨이", startAt = start, endAt = end, rrule = "FREQ=DAILY")
            .also { it.assignIdForTest(30L) }
        val now = LocalDateTime.of(2027, 1, 1, 0, 0)

        every { eventRepository.findAllByRruleIsNotNull() } returns listOf(event)
        every { eventInstanceRepository.findMaxStartAtByEventIdIncludingDeleted(30L) } returns null

        materializer.materialize(now)

        verify(exactly = 0) { recurrenceExpander.expand(any(), any(), any(), any()) }
        verify(exactly = 0) { eventInstanceRepository.save(any<EventInstance>()) }
    }
}
