package com.sandro.asterumscheduler.event.domain

import com.sandro.asterumscheduler.common.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "event_instances")
@SQLRestriction("deleted_at IS NULL")
class EventInstances(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    var eventId: Long,
    var overrideId: Long? = null,
    var locationId: Long? = null,
    @Column(nullable = false)
    var dateKey: LocalDate,
    @Column(nullable = false)
    var startTime: LocalDateTime,
    @Column(nullable = false)
    var endTime: LocalDateTime,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: EventInstancesStatus,
    var deletedAt: LocalDateTime? = null,
) : BaseEntity() {
    fun updateTime(newStartTime: LocalDateTime, newEndTime: LocalDateTime) {
        startTime = newStartTime
        endTime = newEndTime
        dateKey = newStartTime.toLocalDate()
    }

    fun updateLocation(newLocationId: Long?) {
        locationId = newLocationId
    }

    fun updateStatus(newStatus: EventInstancesStatus) {
        status = newStatus
    }

    fun setOverride(overrideId: Long) {
        this.overrideId = overrideId
    }

    fun updateEventId(newEventId: Long) {
        eventId = newEventId
    }

    fun softDelete(now: LocalDateTime = LocalDateTime.now()) {
        deletedAt = now
    }
}
