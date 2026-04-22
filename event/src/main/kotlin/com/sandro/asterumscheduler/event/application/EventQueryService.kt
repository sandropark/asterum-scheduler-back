package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.event.infra.EventInstanceRepository
import com.sandro.asterumscheduler.event.infra.EventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventQueryService(
    private val eventRepository: EventRepository,
    private val eventInstanceRepository: EventInstanceRepository,
) {
    @Transactional(readOnly = true)
    fun findMonthly(query: MonthlyEventQuery): List<EventInstanceSummary> {
        // TODO: 추후 조인으로 처리할지 고민
        val instances = eventInstanceRepository
            .findByDeletedAtIsNullAndStartAtGreaterThanEqualAndStartAtLessThan(query.from, query.to)
        val eventIds = instances.map { it.eventId }.toSet()
        val eventTitleById = eventRepository.findAllById(eventIds).associate { it.id!! to it.title }
        return instances.map { instance ->
            EventInstanceSummary(
                id = instance.id!!,
                title = instance.title ?: eventTitleById.getValue(instance.eventId),
                startAt = instance.startAt,
                endAt = instance.endAt,
            )
        }
    }
}
