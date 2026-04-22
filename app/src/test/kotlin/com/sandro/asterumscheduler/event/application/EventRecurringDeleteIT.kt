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
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@SpringBootTest
@Testcontainers
@Transactional
class EventRecurringDeleteIT @Autowired constructor(
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
    fun `deleteThisOnly - 가운데 인스턴스만 soft-delete 되고 event 는 살아있다`() {
        val start = LocalDateTime.of(2026, 5, 1, 10, 0)
        val end = start.plusHours(1)

        val event = service.create(
            EventCreateRequest(title = "일일", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=3")
        )
        em.flush(); em.clear()

        val instances = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThan(start, start.plusDays(10))
            .filter { it.eventId == event.id }
            .sortedBy { it.startAt }
        assertEquals(3, instances.size)
        val middleId = instances[1].id!!

        service.deleteThisOnly(middleId)
        em.flush(); em.clear()

        val remaining = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThan(start, start.plusDays(10))
            .filter { it.eventId == event.id }
            .sortedBy { it.startAt }
        assertEquals(2, remaining.size)
        assertEquals(start, remaining[0].startAt)
        assertEquals(start.plusDays(2), remaining[1].startAt)

        val persistedEvent = eventRepository.findById(event.id!!).orElseThrow()
        assertNull(persistedEvent.deletedAt)

        val deletedRow = em
            .createNativeQuery("SELECT deleted_at FROM events_instances WHERE id = :id")
            .setParameter("id", middleId)
            .singleResult
        assertNotNull(deletedRow)
    }
}
