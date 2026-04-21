package com.sandro.asterumscheduler.event.domain

import com.sandro.asterumscheduler.common.BaseEntity
import jakarta.persistence.*
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "event_overrides")
class EventOverride(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    val eventId: Long,
    @Column(nullable = false)
    val overrideDate: LocalDate,
    @Column(nullable = false)
    val isDeleted: Boolean = false,
    @Column(nullable = false, length = 255)
    val title: String,
    @Column(nullable = false)
    val startTime: LocalDateTime,
    @Column(nullable = false)
    val endTime: LocalDateTime,
    val locationId: Long? = null,
    val notes: String? = null,
) : BaseEntity()
