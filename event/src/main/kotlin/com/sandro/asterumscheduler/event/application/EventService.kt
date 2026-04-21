package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.common.exception.BusinessException
import com.sandro.asterumscheduler.common.exception.ErrorCode
import com.sandro.asterumscheduler.common.location.LocationReader
import com.sandro.asterumscheduler.event.domain.EventInstances
import com.sandro.asterumscheduler.event.domain.EventInstancesStatus
import com.sandro.asterumscheduler.event.infra.EventInstancesRepository
import com.sandro.asterumscheduler.event.infra.EventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val eventInstancesRepository: EventInstancesRepository,
    private val locationReader: LocationReader,
) {
    @Transactional
    fun create(creatorId: Long, request: EventCreateRequest): EventResponse {
        if (request.locationId != null && !locationReader.existsById(request.locationId))
            throw BusinessException(ErrorCode.NOT_FOUND)
        val event = eventRepository.save(request.toEntity(creatorId))
        val status = resolveStatus(event.locationId, event.startTime, event.endTime)
        eventInstancesRepository.save(
            EventInstances(
                eventId = event.id,
                dateKey = event.startTime.toLocalDate(),
                startTime = event.startTime,
                endTime = event.endTime,
                locationId = event.locationId,
                status = status,
            )
        )
        return EventResponse.from(event)
    }

    private fun resolveStatus(
        locationId: Long?,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
    ): EventInstancesStatus {
        if (locationId == null) return EventInstancesStatus.CONFIRMED
        val hasConflict = eventInstancesRepository.existsOverlapByLocation(locationId, startTime, endTime)
        return if (hasConflict) EventInstancesStatus.CONFLICT else EventInstancesStatus.CONFIRMED
    }
}
