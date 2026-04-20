package com.sandro.asterumscheduler.event.infra

import com.sandro.asterumscheduler.event.domain.Event
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface EventRepository : JpaRepository<Event, Long> {

    @Query(
        """
        SELECT DISTINCT e.locationId FROM Event e
        WHERE e.locationId IS NOT NULL
          AND e.startTime < :end
          AND e.endTime > :start
        """
    )
    fun findReservedLocationIds(@Param("start") start: LocalDateTime, @Param("end") end: LocalDateTime): List<Long>
}
