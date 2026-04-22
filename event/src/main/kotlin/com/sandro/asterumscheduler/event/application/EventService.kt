package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.event.domain.Event
import com.sandro.asterumscheduler.event.domain.EventInstance
import com.sandro.asterumscheduler.event.infra.EventInstanceRepository
import com.sandro.asterumscheduler.event.infra.EventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
}
