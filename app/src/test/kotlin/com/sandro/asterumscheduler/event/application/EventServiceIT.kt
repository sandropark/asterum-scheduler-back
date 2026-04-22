package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.event.domain.Event
import com.sandro.asterumscheduler.event.domain.EventInstance
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
import java.sql.Timestamp
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@Testcontainers
@Transactional
class EventServiceIT @Autowired constructor(
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
    fun `updateSingle 로 시간 변경 시 UPDATE 가 발행돼 DB 에 반영된다`() {
        val (eventId, instanceId) = seedSingle()
        em.flush(); em.clear()

        val newStart = LocalDateTime.of(2026, 7, 1, 9, 0)
        val newEnd = newStart.plusHours(3)
        service.updateSingle(
            instanceId,
            EventSingleUpdateRequest(title = "변경된 제목", startAt = newStart, endAt = newEnd),
        )
        em.flush(); em.clear()

        val event = eventRepository.findById(eventId).orElseThrow()
        val instance = eventInstanceRepository.findById(instanceId).orElseThrow()
        assertEquals("변경된 제목", event.title)
        assertEquals(newStart, event.startAt)
        assertEquals(newEnd, event.endAt)
        assertEquals(newStart, instance.startAt)
        assertEquals(newEnd, instance.endAt)
    }

    @Test
    fun `deleteSingle 호출 후 event 와 instance 모두 SQLRestriction 에 걸려 findById 가 empty 를 반환한다`() {
        val (eventId, instanceId) = seedSingle()
        em.flush(); em.clear()

        service.deleteSingle(instanceId)
        em.flush(); em.clear()

        assertTrue(eventRepository.findById(eventId).isEmpty)
        assertTrue(eventInstanceRepository.findById(instanceId).isEmpty)
    }

    @Test
    fun `deleteSingle 에서 세팅된 deletedAt 은 event 와 instance 가 동일 값이다`() {
        val (eventId, instanceId) = seedSingle()
        em.flush(); em.clear()

        service.deleteSingle(instanceId)
        em.flush(); em.clear()

        val eventDeletedAt = em.createNativeQuery(
            "SELECT deleted_at FROM events WHERE id = :id"
        ).setParameter("id", eventId).singleResult as Timestamp
        val instanceDeletedAt = em.createNativeQuery(
            "SELECT deleted_at FROM events_instances WHERE id = :id"
        ).setParameter("id", instanceId).singleResult as Timestamp
        assertEquals(eventDeletedAt, instanceDeletedAt)
    }

    private fun seedSingle(): Pair<Long, Long> {
        val startAt = LocalDateTime.of(2026, 5, 1, 10, 0)
        val endAt = startAt.plusHours(1)
        val event = eventRepository.save(Event(title = "원본", startAt = startAt, endAt = endAt))
        val instance = eventInstanceRepository.save(
            EventInstance(eventId = event.id!!, startAt = startAt, endAt = endAt),
        )
        return event.id!! to instance.id!!
    }
}
