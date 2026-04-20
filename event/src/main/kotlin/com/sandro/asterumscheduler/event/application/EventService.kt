package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.event.domain.EventInstances
import com.sandro.asterumscheduler.event.domain.EventInstancesStatus
import com.sandro.asterumscheduler.event.infra.EventInstancesRepository
import com.sandro.asterumscheduler.event.infra.EventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val eventInstancesRepository: EventInstancesRepository,
) {
    @Transactional
    fun create(creatorId: Long, request: EventCreateRequest): EventResponse {
        val event = eventRepository.save(request.toEntity(creatorId))
        eventInstancesRepository.save(
            EventInstances(
                eventId = event.id,
                dateKey = event.startTime.toLocalDate(),
                startTime = event.startTime,
                endTime = event.endTime,
                locationId = event.locationId,
                status = EventInstancesStatus.CONFIRMED,
            )
        )
        return EventResponse.from(event)
    }
}
