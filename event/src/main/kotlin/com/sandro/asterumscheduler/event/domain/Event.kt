package com.sandro.asterumscheduler.event.domain

import com.sandro.asterumscheduler.common.BaseEntity
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "events")
class Event(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false, length = 255)
    var title: String,
    @Column(nullable = false)
    var startTime: LocalDateTime,
    @Column(nullable = false)
    var endTime: LocalDateTime,
    var locationId: Long? = null,
    var notes: String? = null,
    val rrule: String? = null,
    @Column(nullable = false)
    val creatorId: Long,
) : BaseEntity() {
    fun isRecurring(): Boolean = rrule != null

    fun updateTitle(newTitle: String) {
        title = newTitle
    }

    fun updateTime(newStartTime: LocalDateTime, newEndTime: LocalDateTime) {
        startTime = newStartTime
        endTime = newEndTime
    }

    fun updateNotes(newNotes: String?) {
        notes = newNotes
    }

    fun updateLocation(newLocationId: Long?) {
        locationId = newLocationId
    }
}
