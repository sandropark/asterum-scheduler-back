package com.sandro.asterumscheduler.event.infra

import com.sandro.asterumscheduler.event.domain.EventParticipant
import org.springframework.data.jpa.repository.JpaRepository

interface EventParticipantRepository : JpaRepository<EventParticipant, Long> {
    fun findAllByEventId(eventId: Long): List<EventParticipant>
    fun deleteAllByEventId(eventId: Long)
}
