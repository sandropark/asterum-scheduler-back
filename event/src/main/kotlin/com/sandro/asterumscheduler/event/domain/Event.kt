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
    val title: String,
    @Column(nullable = false)
    val startTime: LocalDateTime,
    @Column(nullable = false)
    val endTime: LocalDateTime,
    val locationId: Long? = null,
    @Column(length = 500)
    val notes: String? = null,
    @Column(nullable = false)
    val creatorId: Long,
    @OneToMany(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true)
    val participants: MutableList<EventParticipant> = mutableListOf(),
) : BaseEntity()
