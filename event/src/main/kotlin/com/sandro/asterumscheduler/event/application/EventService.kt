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
    private val rruleShortener: RruleShortener,
    private val rruleSuccessor: RruleSuccessor,
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

    @Transactional
    fun updateThisOnly(instanceId: Long, request: EventThisOnlyUpdateRequest) {
        val instance = eventInstanceRepository.findById(instanceId)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }
        instance.title = request.title
        instance.startAt = request.startAt
        instance.endAt = request.endAt
    }

    @Transactional
    fun updateAllTitle(instanceId: Long, request: EventAllTitleUpdateRequest) {
        val instance = eventInstanceRepository.findById(instanceId)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }
        val event = eventRepository.findById(instance.eventId)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }

        event.title = request.title
        eventInstanceRepository.findAllByEventId(event.id!!).forEach { it.title = null }
    }

    @Transactional
    fun updateTitleThisAndFuture(instanceId: Long, request: EventThisAndFutureTitleUpdateRequest) {
        val target = eventInstanceRepository.findById(instanceId)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }
        val oldEvent = eventRepository.findById(target.eventId)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }
        val originalRrule = oldEvent.rrule ?: throw BusinessException(ErrorCode.INVALID_INPUT)

        val newRrule = rruleSuccessor.succeed(originalRrule, oldEvent.startAt, target.startAt)
        oldEvent.rrule = rruleShortener.shorten(originalRrule, target.startAt.minusSeconds(1))

        val newEvent = eventRepository.save(
            Event(
                title = request.title,
                startAt = target.startAt,
                endAt = target.endAt,
                rrule = newRrule,
            )
        )

        eventInstanceRepository
            .findAllByEventIdAndStartAtGreaterThanEqual(oldEvent.id!!, target.startAt)
            .forEach {
                it.eventId = newEvent.id!!
                it.title = null
            }
    }

    @Transactional
    fun updateAllTime(instanceId: Long, request: EventAllTimeUpdateRequest) {
        val instance = eventInstanceRepository.findById(instanceId)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }
        val event = eventRepository.findById(instance.eventId)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }

        event.startAt = request.startAt
        event.endAt = request.endAt
        event.rrule = request.rrule

        val active = eventInstanceRepository.findAllByEventId(event.id!!)
        eventInstanceRepository.deleteAll(active)

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
    }

    @Transactional
    fun deleteAll(instanceId: Long) {
        val instance = eventInstanceRepository.findById(instanceId)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }
        val event = eventRepository.findById(instance.eventId)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }

        val now = LocalDateTime.now()
        event.deletedAt = now
        eventInstanceRepository.findAllByEventId(event.id!!).forEach { it.deletedAt = now }
    }

    @Transactional
    fun deleteThisAndFuture(instanceId: Long) {
        val instance = eventInstanceRepository.findById(instanceId)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }
        val event = eventRepository.findById(instance.eventId)
            .orElseThrow { BusinessException(ErrorCode.NOT_FOUND) }
        val rrule = event.rrule ?: throw BusinessException(ErrorCode.INVALID_INPUT)

        event.rrule = rruleShortener.shorten(rrule, instance.startAt.minusSeconds(1))

        val now = LocalDateTime.now()
        eventInstanceRepository
            .findAllByEventIdAndStartAtGreaterThanEqual(event.id!!, instance.startAt)
            .forEach { it.deletedAt = now }
    }
}
