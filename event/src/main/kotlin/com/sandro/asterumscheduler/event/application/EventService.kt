package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.common.exception.BusinessException
import com.sandro.asterumscheduler.common.exception.ErrorCode
import com.sandro.asterumscheduler.common.location.LocationReader
import com.sandro.asterumscheduler.event.domain.Event
import com.sandro.asterumscheduler.event.domain.EventInstances
import com.sandro.asterumscheduler.event.domain.EventInstancesStatus
import com.sandro.asterumscheduler.event.domain.EventOverride
import com.sandro.asterumscheduler.event.domain.RecurrenceScope
import com.sandro.asterumscheduler.event.infra.EventInstancesRepository
import com.sandro.asterumscheduler.event.infra.EventOverrideRepository
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
    private val eventOverrideRepository: EventOverrideRepository,
    @Value("\${asterum.event.max-recurrence-years}") private val maxRecurrenceYears: Long,
) {
    @Transactional
    fun update(
        eventId: Long,
        request: EventUpdateRequest,
        scope: RecurrenceScope = RecurrenceScope.ALL,
    ) {
        val event = eventRepository.findById(eventId)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }

        if (!event.isRecurring()) {
            updateSingleEvent(event, request)
            return
        }

        when (scope) {
            RecurrenceScope.THIS_ONLY -> updateSingleOccurrence(event, request)
            else -> throw BusinessException(ErrorCode.INVALID_INPUT)
        }
    }

    private fun updateSingleEvent(event: Event, request: EventUpdateRequest) {
        if (request.locationId != null && !locationReader.existsById(request.locationId))
            throw BusinessException(ErrorCode.NOT_FOUND)

        event.updateTitle(request.title)
        event.updateTime(request.startTime, request.endTime)
        event.updateNotes(request.notes)
        event.updateLocation(request.locationId)

        val newStatus = resolveStatusExcluding(request.locationId, request.startTime, request.endTime, event.id)
        eventInstancesRepository.findFirstByEventIdAndOverrideIdIsNull(event.id)?.also {
            it.updateTime(request.startTime, request.endTime)
            it.updateLocation(request.locationId)
            it.updateStatus(newStatus)
        }
    }

    private fun updateSingleOccurrence(event: Event, request: EventUpdateRequest) {
        val targetDate = request.targetDate ?: throw BusinessException(ErrorCode.INVALID_INPUT)
        val instance = eventInstancesRepository.findByEventIdAndDateKey(event.id, targetDate)
            ?: throw BusinessException(ErrorCode.NOT_FOUND)

        val override = eventOverrideRepository.save(
            EventOverride(
                eventId = event.id,
                overrideDate = targetDate,
                isDeleted = false,
                title = request.title,
                startTime = request.startTime,
                endTime = request.endTime,
                locationId = request.locationId,
                notes = request.notes,
            )
        )
        instance.setOverride(override.id)
    }

    private fun resolveStatusExcluding(
        locationId: Long?,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        excludingEventId: Long,
    ): EventInstancesStatus {
        if (locationId == null) return EventInstancesStatus.CONFIRMED
        val hasConflict = eventInstancesRepository.existsOverlapByLocationExcludingEvent(
            locationId, startTime, endTime, excludingEventId,
        )
        return if (hasConflict) EventInstancesStatus.CONFLICT else EventInstancesStatus.CONFIRMED
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
