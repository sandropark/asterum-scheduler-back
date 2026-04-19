package com.sandro.asterumscheduler.event.domain

import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "events")
class Event(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false, length = 255)
    val title: String,
    @Column(nullable = false)
    val startTime: LocalDateTime,
    @Column(nullable = false)
    val endTime: LocalDateTime,
    val locationId: Long? = null,
    val notes: String? = null,
    @Column(nullable = false)
    val creatorId: Long,
)
