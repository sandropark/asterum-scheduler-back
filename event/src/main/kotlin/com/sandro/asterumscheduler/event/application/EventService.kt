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
) {
    @Transactional
    fun create(request: EventCreateRequest): Event {
        val event = eventRepository.save(
            Event(title = request.title, startAt = request.startAt, endAt = request.endAt)
        )
        eventInstanceRepository.save(
            EventInstance(eventId = event.id!!, startAt = event.startAt, endAt = event.endAt)
        )
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
}
