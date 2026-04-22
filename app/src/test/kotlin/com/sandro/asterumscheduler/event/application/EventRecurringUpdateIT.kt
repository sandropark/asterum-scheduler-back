package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.event.infra.EventInstanceRepository
import com.sandro.asterumscheduler.event.infra.EventRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime
import kotlin.test.assertEquals

@SpringBootTest
@Testcontainers
@Transactional
class EventRecurringUpdateIT @Autowired constructor(
    private val service: EventService,
    private val eventRepository: EventRepository,
    private val eventInstanceRepository: EventInstanceRepository,
    private val em: EntityManager,
) {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
    }

    @Test
    fun `updateThisOnly - 가운데 instance 의 title 과 시간만 오버라이드되고 event 와 다른 instance 는 원본 유지`() {
        val start = LocalDateTime.of(2026, 5, 1, 10, 0)
        val end = start.plusHours(1)

        val event = service.create(
            EventCreateRequest(title = "원본", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=3")
        )
        em.flush(); em.clear()

        val instances = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThan(start, start.plusDays(10))
            .filter { it.eventId == event.id }
            .sortedBy { it.startAt }
        assertEquals(3, instances.size)
        val middle = instances[1]
        val middleId = middle.id!!

        val newStart = LocalDateTime.of(2026, 5, 2, 14, 0)
        val newEnd = newStart.plusHours(2)
        service.updateThisOnly(middleId, EventThisOnlyUpdateRequest("오버라이드", newStart, newEnd))
        em.flush(); em.clear()

        val reloadedMiddle = eventInstanceRepository.findById(middleId).orElseThrow()
        assertEquals("오버라이드", reloadedMiddle.title)
        assertEquals(newStart, reloadedMiddle.startAt)
        assertEquals(newEnd, reloadedMiddle.endAt)

        val others = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThan(start, start.plusDays(10))
            .filter { it.eventId == event.id && it.id != middleId }
            .sortedBy { it.startAt }
        assertEquals(2, others.size)
        assertEquals(null, others[0].title)
        assertEquals(start, others[0].startAt)
        assertEquals(end, others[0].endAt)
        assertEquals(null, others[1].title)
        assertEquals(start.plusDays(2), others[1].startAt)
        assertEquals(end.plusDays(2), others[1].endAt)

        val persistedEvent = eventRepository.findById(event.id!!).orElseThrow()
        assertEquals("원본", persistedEvent.title)
        assertEquals(start, persistedEvent.startAt)
        assertEquals(end, persistedEvent.endAt)
    }
}
