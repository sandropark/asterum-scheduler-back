package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.event.infra.EventInstanceRepository
import com.sandro.asterumscheduler.event.infra.EventRepository
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@SpringBootTest
@Testcontainers
@Transactional
@TestPropertySource(properties = ["scheduler.policy.expansion-years=1"])
class EventRecurringCreateIT @Autowired constructor(
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
    fun `FREQ=DAILY COUNT=3 으로 생성하면 events 1건 + events_instances 3건이 저장된다`() {
        val start = LocalDateTime.of(2026, 5, 1, 10, 0)
        val end = start.plusHours(1)

        val event = service.create(
            EventCreateRequest(title = "일일", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=3")
        )
        em.flush(); em.clear()

        val persisted = eventRepository.findById(event.id!!).orElseThrow()
        assertEquals("FREQ=DAILY;COUNT=3", persisted.rrule)

        val instances = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(start, start.plusDays(10))
            .filter { it.eventId == event.id }
            .sortedBy { it.startAt }
        assertEquals(3, instances.size)
        assertEquals(start, instances[0].startAt)
        assertEquals(start.plusDays(1), instances[1].startAt)
        assertEquals(start.plusDays(2), instances[2].startAt)
    }

    @Test
    fun `FREQ=WEEKLY UNTIL 로 생성하면 UNTIL 이전 인스턴스만 저장된다`() {
        val start = LocalDateTime.of(2026, 5, 4, 10, 0) // Mon
        val end = start.plusHours(1)

        val event = service.create(
            EventCreateRequest(
                title = "주간",
                startAt = start,
                endAt = end,
                rrule = "FREQ=WEEKLY;UNTIL=20260601T000000",
            )
        )
        em.flush(); em.clear()

        val instances = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(start, start.plusMonths(2))
            .filter { it.eventId == event.id }
            .sortedBy { it.startAt }
        // 5/4, 5/11, 5/18, 5/25
        assertEquals(4, instances.size)
        assertEquals(LocalDateTime.of(2026, 5, 25, 10, 0), instances.last().startAt)
    }

    @Test
    fun `rrule 이 null 이면 events_instances 가 1건만 저장된다`() {
        val start = LocalDateTime.of(2026, 7, 1, 10, 0)
        val end = start.plusHours(1)

        val event = service.create(EventCreateRequest(title = "단일", startAt = start, endAt = end))
        em.flush(); em.clear()

        val instances = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(start, start.plusDays(1))
            .filter { it.eventId == event.id }
        assertEquals(1, instances.size)
    }

    @Test
    fun `무기한 FREQ=DAILY 는 정책 상한 (expansionYears=1) 내에서만 저장된다`() {
        val start = LocalDateTime.of(2026, 8, 1, 10, 0)
        val end = start.plusHours(1)

        val event = service.create(
            EventCreateRequest(title = "무기한", startAt = start, endAt = end, rrule = "FREQ=DAILY")
        )
        em.flush(); em.clear()

        val instances = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(start, start.plusYears(3))
            .filter { it.eventId == event.id }
        // policyLimit 은 now(테스트 실행 시점) + 1년 이므로 개수는 실행 시점에 따라 달라진다
        // — 여기서는 "무한이 아니라 상한에 의해 잘렸는가" 만 검증
        assertTrue(instances.size in 1..400, "정책 상한으로 끊겨야 하는데 size=${instances.size}")
    }
}
