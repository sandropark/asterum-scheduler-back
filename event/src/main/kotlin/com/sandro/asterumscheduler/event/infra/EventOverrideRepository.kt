package com.sandro.asterumscheduler.event.infra

import com.sandro.asterumscheduler.event.domain.EventOverride
import org.springframework.data.jpa.repository.JpaRepository

interface EventOverrideRepository : JpaRepository<EventOverride, Long> {
    fun findByEventId(eventId: Long): List<EventOverride>
}
