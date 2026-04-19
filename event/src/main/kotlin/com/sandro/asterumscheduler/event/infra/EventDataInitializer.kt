package com.sandro.asterumscheduler.event.infra

import com.sandro.asterumscheduler.event.domain.Event
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

        eventRepository.saveAll(
            listOf(
                Event(
                    title = "주간 개발팀 회의",
                    startTime = base,
                    endTime = base.plusHours(1),
                    locationId = 1L,
                    notes = "스프린트 현황 공유",
                    creatorId = 1L,
                ),
                Event(
                    title = "디자인 리뷰",
                    startTime = base.plusHours(2),
                    endTime = base.plusHours(3),
                    locationId = 2L,
                    notes = null,
                    creatorId = 3L,
                ),
                Event(
                    title = "신규 입사자 교육",
                    startTime = base.plusDays(1),
                    endTime = base.plusDays(1).plusHours(4),
                    locationId = 4L,
                    notes = "온보딩 프로그램",
                    creatorId = 4L,
                ),
            )
        )
    }
}
