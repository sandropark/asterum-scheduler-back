package com.sandro.asterumscheduler.event.domain

import com.sandro.asterumscheduler.common.BaseEntity
import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "event_instances")
class EventInstances(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    val eventId: Long,
    val overrideId: Long? = null,
    val locationId: Long? = null,
    @Column(nullable = false)
    var dateKey: LocalDate,
    @Column(nullable = false)
    var startTime: LocalDateTime,
    @Column(nullable = false)
    var endTime: LocalDateTime,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: EventInstancesStatus,
) : BaseEntity() {
    fun updateTime(newStartTime: LocalDateTime, newEndTime: LocalDateTime) {
        startTime = newStartTime
        endTime = newEndTime
        dateKey = newStartTime.toLocalDate()
    }
}
