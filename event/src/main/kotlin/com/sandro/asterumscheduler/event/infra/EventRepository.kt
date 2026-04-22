package com.sandro.asterumscheduler.event.infra

import com.sandro.asterumscheduler.event.domain.Event
import org.springframework.data.jpa.repository.JpaRepository

interface EventRepository : JpaRepository<Event, Long> {
    fun findAllByRruleIsNotNull(): List<Event>
}
