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
    val eventId: Long,
    @Column(nullable = false)
    val overrideDate: LocalDate,
    @Column(nullable = false, length = 255)
    val title: String,
    @Column(nullable = false)
    val startTime: LocalDateTime,
    @Column(nullable = false)
    val endTime: LocalDateTime,
    val locationId: Long? = null,
    val notes: String? = null,
    var deletedAt: LocalDateTime? = null,
) : BaseEntity() {
    fun softDelete(now: LocalDateTime = LocalDateTime.now()) {
        deletedAt = now
    }
}
