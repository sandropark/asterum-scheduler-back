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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
    fun `deleteAll - event 와 해당 eventId 의 모든 instance 가 soft-delete 된다`() {
        val start = LocalDateTime.of(2026, 6, 1, 10, 0)
        val end = start.plusHours(1)

        val event = service.create(
            EventCreateRequest(title = "일일", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=3")
        )
        em.flush(); em.clear()

        service.deleteAll(
            eventInstanceRepository
                .findByStartAtGreaterThanEqualAndStartAtLessThan(start, start.plusDays(10))
                .first { it.eventId == event.id }.id!!
        )
        em.flush(); em.clear()

        val remaining = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThan(start, start.plusDays(10))
            .filter { it.eventId == event.id }
        assertEquals(0, remaining.size)
        assertFalse(eventRepository.findById(event.id!!).isPresent)

        val eventDeletedAt = em
            .createNativeQuery("SELECT deleted_at FROM events WHERE id = :id")
            .setParameter("id", event.id)
            .singleResult
        assertNotNull(eventDeletedAt)

        @Suppress("UNCHECKED_CAST")
        val instanceDeletedAts = em
            .createNativeQuery("SELECT deleted_at FROM events_instances WHERE event_id = :eid")
            .setParameter("eid", event.id)
            .resultList as List<Any?>
        assertEquals(3, instanceDeletedAts.size)
        assertTrue(instanceDeletedAts.all { it != null })
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

    @Test
    fun `deleteThisAndFuture - 3번째 회차부터 soft-delete 되고 event_rrule 이 단축된다`() {
        val start = LocalDateTime.of(2026, 7, 1, 10, 0)
        val end = start.plusHours(1)

        val event = service.create(
            EventCreateRequest(title = "일일", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=5")
        )
        em.flush(); em.clear()

        val instances = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThan(start, start.plusDays(10))
            .filter { it.eventId == event.id }
            .sortedBy { it.startAt }
        assertEquals(5, instances.size)
        val targetId = instances[2].id!!
        val targetStart = instances[2].startAt

        service.deleteThisAndFuture(targetId)
        em.flush(); em.clear()

        val remaining = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThan(start, start.plusDays(10))
            .filter { it.eventId == event.id }
            .sortedBy { it.startAt }
        assertEquals(2, remaining.size)
        assertEquals(start, remaining[0].startAt)
        assertEquals(start.plusDays(1), remaining[1].startAt)

        val persistedEvent = eventRepository.findById(event.id!!).orElseThrow()
        assertNull(persistedEvent.deletedAt)
        val expectedUntil = targetStart.minusSeconds(1)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
        assertTrue(persistedEvent.rrule!!.contains("FREQ=DAILY"))
        assertTrue(persistedEvent.rrule!!.contains("UNTIL=$expectedUntil"))
        assertFalse(persistedEvent.rrule!!.contains("COUNT="))

        @Suppress("UNCHECKED_CAST")
        val futureDeletedAts = em
            .createNativeQuery("SELECT deleted_at FROM events_instances WHERE event_id = :eid AND start_at >= :from ORDER BY start_at")
            .setParameter("eid", event.id)
            .setParameter("from", targetStart)
            .resultList as List<Any?>
        assertEquals(3, futureDeletedAts.size)
        assertTrue(futureDeletedAts.all { it != null })
    }
}
