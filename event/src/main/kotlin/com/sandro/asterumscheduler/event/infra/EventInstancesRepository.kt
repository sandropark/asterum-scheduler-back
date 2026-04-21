package com.sandro.asterumscheduler.event.infra

import com.sandro.asterumscheduler.event.domain.EventInstances
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.time.LocalDateTime

interface EventInstancesRepository : JpaRepository<EventInstances, Long> {

    fun findFirstByEventIdAndOverrideIdIsNull(eventId: Long): EventInstances?

    fun findByEventIdAndDateKey(eventId: Long, dateKey: LocalDate): EventInstances?

    fun findByEventId(eventId: Long): List<EventInstances>

    @Query(
        "SELECT ei.locationId FROM EventInstances ei " +
        "WHERE ei.locationId IS NOT NULL " +
        "AND ei.startTime < :end AND ei.endTime > :start"
    )
    fun findResourceIdsByOverlap(
        @Param("start") start: LocalDateTime,
        @Param("end") end: LocalDateTime,
    ): List<Long>

    @Query(
        value = """
            SELECT EXISTS (
                SELECT 1 FROM event_instances
                WHERE location_id = :locationId
                AND deleted_at IS NULL
                AND tsrange(start_time, end_time, '[)') && tsrange(:start, :end, '[)')
            )
        """,
        nativeQuery = true,
    )
    fun existsOverlapByLocation(
        @Param("locationId") locationId: Long,
        @Param("start") start: LocalDateTime,
        @Param("end") end: LocalDateTime,
    ): Boolean

    @Query(
        value = """
            SELECT EXISTS (
                SELECT 1 FROM event_instances
                WHERE location_id = :locationId
                AND deleted_at IS NULL
                AND id <> :excludingInstanceId
                AND tsrange(start_time, end_time, '[)') && tsrange(:start, :end, '[)')
            )
        """,
        nativeQuery = true,
    )
    fun existsOverlapByLocationExcludingInstance(
        @Param("locationId") locationId: Long,
        @Param("start") start: LocalDateTime,
        @Param("end") end: LocalDateTime,
        @Param("excludingInstanceId") excludingInstanceId: Long,
    ): Boolean
}
