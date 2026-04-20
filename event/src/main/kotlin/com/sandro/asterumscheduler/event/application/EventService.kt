package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.common.exception.BusinessException
import com.sandro.asterumscheduler.common.exception.ErrorCode
import com.sandro.asterumscheduler.common.location.LocationReader
import com.sandro.asterumscheduler.common.user.UserReader
import com.sandro.asterumscheduler.event.domain.Event
import com.sandro.asterumscheduler.event.domain.EventParticipant
import com.sandro.asterumscheduler.event.infra.EventRepository
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EventService(
    private val eventRepository: EventRepository,
    private val locationReader: LocationReader,
    private val userReader: UserReader,
) {

    @Transactional
    fun create(request: CreateEventRequest): EventResponse {
        if (request.locationId != null && !locationReader.existsById(request.locationId))
            throw BusinessException(ErrorCode.NOT_FOUND)
        if (request.participantIds.isNotEmpty() && userReader.findNotExistingIds(request.participantIds).isNotEmpty())
            throw BusinessException(ErrorCode.NOT_FOUND)

        val event = Event(
            title = request.title,
            startTime = request.startTime,
            endTime = request.endTime,
            locationId = request.locationId,
            notes = request.notes,
            creatorId = request.creatorId,
        )
        request.participantIds.forEach { userId ->
            event.participants.add(EventParticipant(event = event, userId = userId))
        }

        try {
            return eventRepository.save(event).toResponse()
        } catch (e: DataIntegrityViolationException) {
            throw BusinessException(ErrorCode.CONFLICT, e) // TODO: 응답 메시지 개선
        }
    }

    @Transactional
    fun delete(id: Long) {
        // TODO: soft delete??
        val event = eventRepository.findById(id)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }
        eventRepository.delete(event)
    }

    private fun Event.toResponse() = EventResponse(
        id = id,
        title = title,
        startTime = startTime,
        endTime = endTime,
        locationId = locationId,
        notes = notes,
        creatorId = creatorId,
        participants = participants.map { ParticipantResponse(userId = it.userId) },
    )
}
