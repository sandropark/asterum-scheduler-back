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

    @Test
    fun `updateAllTime - 활성 instance 가 삭제되고 새로운 rrule 기준으로 재생성되며 soft-delete 된 instance 는 건드리지 않는다`() {
        val start = LocalDateTime.of(2026, 9, 1, 10, 0)
        val end = start.plusHours(1)

        val event = service.create(
            EventCreateRequest(title = "원본", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=3")
        )
        em.flush(); em.clear()

        val original = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThan(start, start.plusDays(10))
            .filter { it.eventId == event.id }
            .sortedBy { it.startAt }
        assertEquals(3, original.size)

        service.deleteThisOnly(original[2].id!!)
        em.flush(); em.clear()

        val newStart = LocalDateTime.of(2026, 10, 5, 14, 0)
        val newEnd = newStart.plusHours(2)
        val newRrule = "FREQ=WEEKLY;COUNT=2"
        service.updateAllTime(
            original[0].id!!,
            EventAllTimeUpdateRequest(newStart, newEnd, newRrule),
        )
        em.flush(); em.clear()

        val persistedEvent = eventRepository.findById(event.id!!).orElseThrow()
        assertEquals(newStart, persistedEvent.startAt)
        assertEquals(newEnd, persistedEvent.endAt)
        assertEquals(newRrule, persistedEvent.rrule)

        val regenerated = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThan(newStart, newStart.plusDays(30))
            .filter { it.eventId == event.id }
            .sortedBy { it.startAt }
        assertEquals(2, regenerated.size)
        assertEquals(newStart, regenerated[0].startAt)
        assertEquals(newEnd, regenerated[0].endAt)
        assertEquals(newStart.plusWeeks(1), regenerated[1].startAt)
        assertEquals(newEnd.plusWeeks(1), regenerated[1].endAt)

        val previouslyDeletedAt = em
            .createNativeQuery("SELECT deleted_at FROM events_instances WHERE id = :id")
            .setParameter("id", original[2].id)
            .singleResult
        kotlin.test.assertNotNull(previouslyDeletedAt)

        @Suppress("UNCHECKED_CAST")
        val allRowsForEvent = em
            .createNativeQuery("SELECT id FROM events_instances WHERE event_id = :eid")
            .setParameter("eid", event.id)
            .resultList as List<Any?>
        assertEquals(3, allRowsForEvent.size)
    }

    @Test
    fun `updateTimeThisAndFuture - old event rrule 단축 + 신규 event 생성 + target 이후 활성 instance hard-delete 후 재생성, soft-delete 된 instance 는 유지`() {
        val start = LocalDateTime.of(2026, 12, 1, 10, 0)
        val end = start.plusHours(1)

        val oldEvent = service.create(
            EventCreateRequest(title = "원본", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=5")
        )
        em.flush(); em.clear()

        val instances = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThan(start, start.plusDays(10))
            .filter { it.eventId == oldEvent.id }
            .sortedBy { it.startAt }
        assertEquals(5, instances.size)

        val softDeletedId = instances[4].id!!
        service.deleteThisOnly(softDeletedId)
        em.flush(); em.clear()

        val targetId = instances[2].id!!
        val targetStart = instances[2].startAt

        val newStart = LocalDateTime.of(2027, 1, 5, 14, 0)
        val newEnd = newStart.plusHours(2)
        val newRrule = "FREQ=WEEKLY;COUNT=2"
        service.updateTimeThisAndFuture(
            targetId,
            EventThisAndFutureTimeUpdateRequest(newStart, newEnd, newRrule),
        )
        em.flush(); em.clear()

        val persistedOld = eventRepository.findById(oldEvent.id!!).orElseThrow()
        val expectedUntil = targetStart.minusSeconds(1)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
        kotlin.test.assertTrue(persistedOld.rrule!!.contains("FREQ=DAILY"))
        kotlin.test.assertTrue(persistedOld.rrule!!.contains("UNTIL=$expectedUntil"))
        kotlin.test.assertFalse(persistedOld.rrule!!.contains("COUNT="))
        assertEquals("원본", persistedOld.title)

        val newEvents = eventRepository.findAll().filter { it.id != oldEvent.id }
        assertEquals(1, newEvents.size)
        val newEvent = newEvents.single()
        assertEquals("원본", newEvent.title)
        assertEquals(newStart, newEvent.startAt)
        assertEquals(newEnd, newEvent.endAt)
        assertEquals(newRrule, newEvent.rrule)

        val oldSide = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThan(start, start.plusDays(30))
            .filter { it.eventId == oldEvent.id }
            .sortedBy { it.startAt }
        assertEquals(2, oldSide.size)
        assertEquals(start, oldSide[0].startAt)
        assertEquals(start.plusDays(1), oldSide[1].startAt)

        val newSide = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThan(newStart, newStart.plusDays(30))
            .filter { it.eventId == newEvent.id }
            .sortedBy { it.startAt }
        assertEquals(2, newSide.size)
        assertEquals(newStart, newSide[0].startAt)
        assertEquals(newEnd, newSide[0].endAt)
        assertEquals(newStart.plusWeeks(1), newSide[1].startAt)
        assertEquals(newEnd.plusWeeks(1), newSide[1].endAt)

        val softDeletedRow = em
            .createNativeQuery("SELECT deleted_at FROM events_instances WHERE id = :id")
            .setParameter("id", softDeletedId)
            .singleResult
        kotlin.test.assertNotNull(softDeletedRow)

        @Suppress("UNCHECKED_CAST")
        val remainingOldSideRows = em
            .createNativeQuery("SELECT id FROM events_instances WHERE event_id = :eid")
            .setParameter("eid", oldEvent.id)
            .resultList as List<Any?>
        assertEquals(3, remainingOldSideRows.size)
    }

    @Test
    fun `updateTitleThisAndFuture - old event rrule 단축 + 신규 event 생성 + target 이후 instance 가 신규 event 로 이동하고 title null 리셋`() {
        val start = LocalDateTime.of(2026, 11, 1, 10, 0)
        val end = start.plusHours(1)

        val oldEvent = service.create(
            EventCreateRequest(title = "원본", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=5")
        )
        em.flush(); em.clear()

        val instances = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThan(start, start.plusDays(10))
            .filter { it.eventId == oldEvent.id }
            .sortedBy { it.startAt }
        assertEquals(5, instances.size)

        service.updateThisOnly(
            instances[2].id!!,
            EventThisOnlyUpdateRequest("오버라이드", instances[2].startAt, instances[2].endAt),
        )
        em.flush(); em.clear()

        val targetId = instances[2].id!!
        val targetStart = instances[2].startAt
        service.updateTitleThisAndFuture(
            targetId,
            EventThisAndFutureTitleUpdateRequest("새 제목"),
        )
        em.flush(); em.clear()

        val persistedOld = eventRepository.findById(oldEvent.id!!).orElseThrow()
        val expectedUntil = targetStart.minusSeconds(1)
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
        kotlin.test.assertTrue(persistedOld.rrule!!.contains("FREQ=DAILY"))
        kotlin.test.assertTrue(persistedOld.rrule!!.contains("UNTIL=$expectedUntil"))
        kotlin.test.assertFalse(persistedOld.rrule!!.contains("COUNT="))
        assertEquals("원본", persistedOld.title)

        val newEvents = eventRepository.findAll().filter { it.id != oldEvent.id }
        assertEquals(1, newEvents.size)
        val newEvent = newEvents.single()
        assertEquals("새 제목", newEvent.title)
        assertEquals(targetStart, newEvent.startAt)
        assertEquals(instances[2].endAt, newEvent.endAt)
        kotlin.test.assertTrue(newEvent.rrule!!.contains("FREQ=DAILY"))
        kotlin.test.assertTrue(newEvent.rrule!!.contains("COUNT=3"))

        val reloadedOldSide = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThan(start, start.plusDays(30))
            .filter { it.eventId == oldEvent.id }
            .sortedBy { it.startAt }
        assertEquals(2, reloadedOldSide.size)

        val reloadedNewSide = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThan(start, start.plusDays(30))
            .filter { it.eventId == newEvent.id }
            .sortedBy { it.startAt }
        assertEquals(3, reloadedNewSide.size)
        kotlin.test.assertTrue(reloadedNewSide.all { it.title == null })
        assertEquals(targetStart, reloadedNewSide[0].startAt)
    }

    @Test
    fun `updateAllTitle - event 제목이 변경되고 단일 수정한 instance 의 title 까지 null 로 리셋된다`() {
        val start = LocalDateTime.of(2026, 8, 1, 10, 0)
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
        val middleId = instances[1].id!!

        service.updateThisOnly(
            middleId,
            EventThisOnlyUpdateRequest("오버라이드", instances[1].startAt, instances[1].endAt),
        )
        em.flush(); em.clear()

        service.updateAllTitle(middleId, EventAllTitleUpdateRequest("새 제목"))
        em.flush(); em.clear()

        val persistedEvent = eventRepository.findById(event.id!!).orElseThrow()
        assertEquals("새 제목", persistedEvent.title)

        val reloaded = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThan(start, start.plusDays(10))
            .filter { it.eventId == event.id }
            .sortedBy { it.startAt }
        assertEquals(3, reloaded.size)
        assertEquals(listOf(null, null, null), reloaded.map { it.title })
    }
}
