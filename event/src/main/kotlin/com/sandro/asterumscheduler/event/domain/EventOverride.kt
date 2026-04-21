package com.sandro.asterumscheduler.event.domain

import com.sandro.asterumscheduler.common.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "event_overrides")
@SQLRestriction("deleted_at IS NULL")
class EventOverride(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    var eventId: Long,
    @Column(nullable = false)
    val overrideDate: LocalDate,
    @Column(nullable = false, length = 255)
    var title: String,
    @Column(nullable = false)
    val startTime: LocalDateTime,
    @Column(nullable = false)
    val endTime: LocalDateTime,
    val locationId: Long? = null,
    var notes: String? = null,
    var deletedAt: LocalDateTime? = null,
) : BaseEntity() {
    fun softDelete(now: LocalDateTime = LocalDateTime.now()) {
        deletedAt = now
    }

    fun updateContents(newTitle: String, newNotes: String?) {
        title = newTitle
        notes = newNotes
    }

    fun updateEventId(newEventId: Long) {
        eventId = newEventId
    }
}
