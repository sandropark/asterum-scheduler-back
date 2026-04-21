package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.common.exception.BusinessException
import com.sandro.asterumscheduler.common.exception.ErrorCode
import com.sandro.asterumscheduler.common.location.LocationReader
import com.sandro.asterumscheduler.event.domain.Event
import com.sandro.asterumscheduler.event.domain.EventInstances
import com.sandro.asterumscheduler.event.domain.EventInstancesStatus
import com.sandro.asterumscheduler.event.infra.EventInstancesRepository
import com.sandro.asterumscheduler.event.infra.EventRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDateTime

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val eventInstancesRepository: EventInstancesRepository,
    private val locationReader: LocationReader,
    @Value("\${asterum.event.max-recurrence-years}") private val maxRecurrenceYears: Long,
) {
    @Transactional
    fun update(eventId: Long, request: EventUpdateRequest) {
        val event = eventRepository.findById(eventId)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }
        event.updateTitle(request.title)
    }

    @Transactional
    fun create(creatorId: Long, request: EventCreateRequest): EventResponse {
        if (request.locationId != null && !locationReader.existsById(request.locationId))
            throw BusinessException(ErrorCode.NOT_FOUND)
        val event = eventRepository.save(request.toEntity(creatorId))
        if (event.isRecurring()) saveRecurringInstances(event) else saveSingleInstance(event)
        return EventResponse.from(event)
    }

    private fun saveSingleInstance(event: Event) {
        saveInstance(event, event.startTime, event.endTime)
    }

    private fun saveRecurringInstances(event: Event) {
        val duration = Duration.between(event.startTime, event.endTime)
        val rangeEnd = event.startTime.plusYears(maxRecurrenceYears)
        val occurrences = RRuleExpander.expand(event.rrule!!, event.startTime, event.startTime, rangeEnd)
        occurrences.forEach { start ->
            saveInstance(event, start, start.plus(duration))
        }
    }

    private fun saveInstance(event: Event, startTime: LocalDateTime, endTime: LocalDateTime) {
        val status = resolveStatus(event.locationId, startTime, endTime)
        eventInstancesRepository.save(
            EventInstances(
                eventId = event.id,
                dateKey = startTime.toLocalDate(),
                startTime = startTime,
                endTime = endTime,
                locationId = event.locationId,
                status = status,
            )
        )
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
