package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.event.infra.EventInstanceRepository
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
class RecurrenceMaterializerIT @Autowired constructor(
    private val service: EventService,
    private val materializer: RecurrenceMaterializer,
    private val eventInstanceRepository: EventInstanceRepository,
    private val em: EntityManager,
    private val policy: SchedulerPolicyProperties,
) {
    companion object {
        @Container
        @ServiceConnection
        @JvmStatic
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
    }

    @Test
    fun `무기한 rrule 이벤트는 정책 기간이 앞으로 이동하면 그만큼 인스턴스가 추가된다`() {
        val start = LocalDateTime.of(2026, 1, 1, 10, 0)
        val end = start.plusHours(1)

        val event = service.create(
            EventCreateRequest(title = "연간", startAt = start, endAt = end, rrule = "FREQ=YEARLY")
        )
        em.flush(); em.clear()

        val baseline = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(start, start.plusYears(policy.expansionYears + 10L))
            .filter { it.eventId == event.id }
            .sortedBy { it.startAt }
        val baselineLastYear = baseline.last().startAt.year

        val future = LocalDateTime.now().plusYears(2)
        materializer.materialize(future)
        em.flush(); em.clear()

        val extended = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(start, start.plusYears(policy.expansionYears + 10L))
            .filter { it.eventId == event.id }
            .sortedBy { it.startAt }
        val extendedLastYear = extended.last().startAt.year

        assertEquals(baselineLastYear + 2, extendedLastYear)
        assertEquals(baseline.size + 2, extended.size)
    }

    @Test
    fun `COUNT 한정 rrule 은 스케줄러가 돌아도 인스턴스가 추가되지 않는다`() {
        val start = LocalDateTime.of(2026, 2, 1, 10, 0)
        val end = start.plusHours(1)

        val event = service.create(
            EventCreateRequest(title = "3회", startAt = start, endAt = end, rrule = "FREQ=DAILY;COUNT=3")
        )
        em.flush(); em.clear()

        val baselineCount = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(start, start.plusDays(10))
            .count { it.eventId == event.id }
        assertEquals(3, baselineCount)

        materializer.materialize(LocalDateTime.now().plusYears(5))
        em.flush(); em.clear()

        val afterCount = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(start, start.plusDays(10))
            .count { it.eventId == event.id }
        assertEquals(3, afterCount)
    }
}
