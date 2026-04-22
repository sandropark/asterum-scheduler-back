package com.sandro.asterumscheduler.event.infra

import com.sandro.asterumscheduler.event.domain.Event
import com.sandro.asterumscheduler.event.domain.EventInstance
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
import kotlin.test.assertTrue

@SpringBootTest
@Testcontainers
@Transactional
class EventInstanceRepositoryIT @Autowired constructor(
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
    fun `findByStartAtGreaterThanEqual_AndStartAtLessThan 는 from 이상 to 미만만 반환 (to 는 exclusive)`() {
        val event = newEvent()
        val monthStart = LocalDateTime.of(2026, 5, 1, 0, 0)
        val monthEnd = LocalDateTime.of(2026, 6, 1, 0, 0)

        val onFrom = saveInstance(event, monthStart)
        val middle = saveInstance(event, monthStart.plusDays(15))
        val onTo = saveInstance(event, monthEnd)
        val before = saveInstance(event, monthStart.minusSeconds(1))
        em.flush(); em.clear()

        val ids = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThan(monthStart, monthEnd)
            .map { it.id }
            .toSet()

        assertTrue(onFrom.id in ids, "from 은 inclusive")
        assertTrue(middle.id in ids)
        assertFalse(onTo.id in ids, "to 는 exclusive")
        assertFalse(before.id in ids)
    }

    @Test
    fun `EventInstance deletedAt 을 세팅하면 SQLRestriction 으로 findById·파생 메서드 모두에서 제외된다`() {
        val event = newEvent()
        val saved = saveInstance(event, event.startAt)
        em.flush(); em.clear()

        val managed = eventInstanceRepository.findById(saved.id!!).orElseThrow()
        managed.deletedAt = LocalDateTime.now()
        em.flush(); em.clear()

        assertTrue(eventInstanceRepository.findById(saved.id!!).isEmpty)
        val found = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThan(event.startAt, event.startAt.plusDays(1))
        assertTrue(found.none { it.id == saved.id })
    }

    @Test
    fun `Event deletedAt 을 세팅하면 SQLRestriction 으로 findById 에서 제외된다`() {
        val event = newEvent()
        em.flush(); em.clear()

        val managed = eventRepository.findById(event.id!!).orElseThrow()
        managed.deletedAt = LocalDateTime.now()
        em.flush(); em.clear()

        assertTrue(eventRepository.findById(event.id!!).isEmpty)
    }

    @Test
    fun `save 후 findById 로 조회하면 엔티티가 그대로 복원되고 BaseEntity 감사 컬럼이 채워진다`() {
        val event = newEvent()
        em.flush(); em.clear()

        val found = eventRepository.findById(event.id!!).orElseThrow()
        assertEquals(event.title, found.title)
        assertEquals(event.startAt, found.startAt)
        assertEquals(event.endAt, found.endAt)
    }

    private fun newEvent(
        title: String = "회의",
        startAt: LocalDateTime = LocalDateTime.of(2026, 5, 1, 10, 0),
        endAt: LocalDateTime = startAt.plusHours(1),
    ): Event = eventRepository.save(Event(title = title, startAt = startAt, endAt = endAt))

    private fun saveInstance(
        event: Event,
        startAt: LocalDateTime,
        endAt: LocalDateTime = startAt.plusHours(1),
    ): EventInstance = eventInstanceRepository.save(
        EventInstance(eventId = event.id!!, startAt = startAt, endAt = endAt),
    )
}
