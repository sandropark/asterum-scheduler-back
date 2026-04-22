package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.event.domain.Event
import com.sandro.asterumscheduler.event.domain.EventInstance
import com.sandro.asterumscheduler.event.domain.assignIdForTest
import com.sandro.asterumscheduler.event.infra.EventInstanceRepository
import com.sandro.asterumscheduler.event.infra.EventRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals

class EventQueryServiceTest {

    private val eventRepository = mockk<EventRepository>()
    private val eventInstanceRepository = mockk<EventInstanceRepository>()
    private val service = EventQueryService(eventRepository, eventInstanceRepository)

    @Test
    fun `월간 조회 시 instance_title 이 있으면 그 값을, 없으면 events_title 을 반환한다`() {
        val from = LocalDateTime.of(2026, 5, 1, 0, 0)
        val to = LocalDateTime.of(2026, 6, 1, 0, 0)

        val event1 = Event(title = "시리즈 제목", startAt = from, endAt = from.plusHours(1))
            .also { it.assignIdForTest(10L) }
        val event2 = Event(title = "다른 시리즈", startAt = from.plusDays(1), endAt = from.plusDays(1).plusHours(1))
            .also { it.assignIdForTest(20L) }

        val instance1 = EventInstance(
            eventId = 10L,
            startAt = from,
            endAt = from.plusHours(1),
            title = "오버라이드",
        ).also { it.assignIdForTest(1L) }
        val instance2 = EventInstance(
            eventId = 20L,
            startAt = from.plusDays(1),
            endAt = from.plusDays(1).plusHours(1),
            title = null,
        ).also { it.assignIdForTest(2L) }

        every {
            eventInstanceRepository.findByDeletedAtIsNullAndStartAtGreaterThanEqualAndStartAtLessThan(from, to)
        } returns listOf(instance1, instance2)
        every {
            eventRepository.findAllById(match<Iterable<Long>> { it.toSet() == setOf(10L, 20L) })
        } returns listOf(event1, event2)

        val result = service.findMonthly(MonthlyEventQuery(from, to))

        assertEquals(2, result.size)
        assertEquals(
            EventInstanceSummary(id = 1L, title = "오버라이드", startAt = from, endAt = from.plusHours(1)),
            result[0],
        )
        assertEquals(
            EventInstanceSummary(
                id = 2L,
                title = "다른 시리즈",
                startAt = from.plusDays(1),
                endAt = from.plusDays(1).plusHours(1),
            ),
            result[1],
        )
    }
}
