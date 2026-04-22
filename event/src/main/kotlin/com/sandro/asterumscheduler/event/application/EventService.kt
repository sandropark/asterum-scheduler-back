package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.common.exception.BusinessException
import com.sandro.asterumscheduler.common.exception.ErrorCode
import com.sandro.asterumscheduler.event.domain.Event
import com.sandro.asterumscheduler.event.domain.EventInstance
import com.sandro.asterumscheduler.event.infra.EventInstanceRepository
import com.sandro.asterumscheduler.event.infra.EventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val eventInstanceRepository: EventInstanceRepository,
    private val recurrenceExpander: RecurrenceExpander,
) {
    @Transactional
    fun create(request: EventCreateRequest): Event {
        val event = eventRepository.save(
            Event(
                title = request.title,
                startAt = request.startAt,
                endAt = request.endAt,
                rrule = request.rrule,
            )
        )
        val occurrences = if (request.rrule == null) {
            listOf(RecurrenceExpander.Occurrence(request.startAt, request.endAt))
        } else {
            recurrenceExpander.expand(request.rrule, request.startAt, request.endAt)
        }
        occurrences.forEach { occ ->
            eventInstanceRepository.save(
                EventInstance(eventId = event.id!!, startAt = occ.startAt, endAt = occ.endAt)
            )
        }
        return event
    }

    @Transactional
    fun updateSingle(instanceId: Long, request: EventSingleUpdateRequest) {
        val instance = eventInstanceRepository.findById(instanceId)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }
        val event = eventRepository.findById(instance.eventId)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }

        event.title = request.title
        event.startAt = request.startAt
        event.endAt = request.endAt
        instance.startAt = request.startAt
        instance.endAt = request.endAt
    }

    @Transactional
    fun deleteSingle(instanceId: Long) {
        val instance = eventInstanceRepository.findById(instanceId)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }
        val event = eventRepository.findById(instance.eventId)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }

        val now = LocalDateTime.now()
        event.deletedAt = now
        instance.deletedAt = now
    }

    @Transactional
    fun deleteThisOnly(instanceId: Long) {
        val instance = eventInstanceRepository.findById(instanceId)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }
        instance.deletedAt = LocalDateTime.now()
    }
}
