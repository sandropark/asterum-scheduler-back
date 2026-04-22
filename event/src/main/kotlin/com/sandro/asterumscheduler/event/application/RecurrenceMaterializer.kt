package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.event.domain.EventInstance
import com.sandro.asterumscheduler.event.infra.EventInstanceRepository
import com.sandro.asterumscheduler.event.infra.EventRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class RecurrenceMaterializer(
    private val eventRepository: EventRepository,
    private val eventInstanceRepository: EventInstanceRepository,
    private val recurrenceExpander: RecurrenceExpander,
) {
    @Scheduled(cron = "0 0 0 * * *")
    @Transactional
    fun runScheduled() {
        materialize(LocalDateTime.now())
    }

    fun materialize(now: LocalDateTime) {
        eventRepository.findAllByRruleIsNotNull().forEach { event ->
            val maxStart = eventInstanceRepository
                .findMaxStartAtByEventIdIncludingDeleted(event.id!!)
                ?: return@forEach
            val rrule = event.rrule ?: return@forEach
            recurrenceExpander.expand(rrule, event.startAt, event.endAt, now)
                .filter { it.startAt.isAfter(maxStart) }
                .forEach { occ ->
                    eventInstanceRepository.save(
                        EventInstance(eventId = event.id!!, startAt = occ.startAt, endAt = occ.endAt)
                    )
                }
        }
    }
}
