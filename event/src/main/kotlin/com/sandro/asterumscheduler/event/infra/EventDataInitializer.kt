package com.sandro.asterumscheduler.event.infra

import com.sandro.asterumscheduler.event.domain.Event
import com.sandro.asterumscheduler.event.domain.EventParticipant
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Profile("dev")
@Component
class EventDataInitializer(
    private val eventRepository: EventRepository,
) : ApplicationRunner {

    override fun run(args: ApplicationArguments) {
        if (eventRepository.count() > 0) return

        val base = LocalDateTime.of(2026, 4, 21, 9, 0)

        fun event(
            title: String,
            startTime: LocalDateTime,
            endTime: LocalDateTime,
            locationId: Long?,
            notes: String?,
            creatorId: Long,
            userIds: List<Long>
        ): Event {
            val e = Event(
                title = title,
                startTime = startTime,
                endTime = endTime,
                locationId = locationId,
                notes = notes,
                creatorId = creatorId
            )
            userIds.forEach { e.participants.add(EventParticipant(event = e, userId = it)) }
            return e
        }

        eventRepository.saveAll(
            listOf(
                event("주간 개발팀 회의", base, base.plusHours(1), 1L, "스프린트 현황 공유", 1L, listOf(2L, 3L)),
                event("디자인 리뷰", base.plusHours(2), base.plusHours(3), 2L, null, 3L, listOf(1L, 2L)),
                event(
                    "신규 입사자 교육",
                    base.plusDays(1),
                    base.plusDays(1).plusHours(4),
                    4L,
                    "온보딩 프로그램",
                    4L,
                    listOf(1L, 2L, 3L)
                ),
            )
        )
    }
}
