package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.common.event.EventReader
import com.sandro.asterumscheduler.event.infra.EventRepository
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class EventReaderImpl(private val eventRepository: EventRepository) : EventReader {
    override fun findReservedLocationIds(start: LocalDateTime, end: LocalDateTime): List<Long> =
        eventRepository.findReservedLocationIds(start, end)
}
