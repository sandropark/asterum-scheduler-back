package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.common.exception.BusinessException
import com.sandro.asterumscheduler.common.exception.ErrorCode
import com.sandro.asterumscheduler.event.infra.EventInstanceRepository
import com.sandro.asterumscheduler.event.infra.EventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class EventQueryService(
    private val eventRepository: EventRepository,
    private val eventInstanceRepository: EventInstanceRepository,
) {
    fun findDetail(instanceId: Long): EventInstanceDetail {
        val instance = eventInstanceRepository.findById(instanceId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND)
        }
        val event = eventRepository.findById(instance.eventId).orElseThrow {
            BusinessException(ErrorCode.NOT_FOUND)
        }
        return EventInstanceDetail(
            id = instance.id!!,
            title = instance.title ?: event.title,
            startAt = instance.startAt,
            endAt = instance.endAt,
            rrule = event.rrule,
        )
    }

    fun findMonthly(query: MonthlyEventQuery): List<EventInstanceSummary> {
        // TODO: 추후 조인으로 처리할지 고민
        val instances = eventInstanceRepository
            .findByStartAtGreaterThanEqualAndStartAtLessThan(query.from, query.to)
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
