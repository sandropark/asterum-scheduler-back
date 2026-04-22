package com.sandro.asterumscheduler.event.infra

import com.sandro.asterumscheduler.event.domain.EventInstance
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface EventInstanceRepository : JpaRepository<EventInstance, Long> {
    fun findByStartAtGreaterThanEqualAndStartAtLessThan(
        from: LocalDateTime,
        to: LocalDateTime,
    ): List<EventInstance>

    fun findAllByEventId(eventId: Long): List<EventInstance>

    fun findAllByEventIdAndStartAtGreaterThanEqual(
        // TODO: 조회 성능 최적화
        eventId: Long,
        startAt: LocalDateTime,
    ): List<EventInstance>

    @Query(
        value = "SELECT MAX(start_at) FROM events_instances WHERE event_id = :eventId",
        nativeQuery = true,
    )
    fun findMaxStartAtByEventIdIncludingDeleted(@Param("eventId") eventId: Long): LocalDateTime?
}
