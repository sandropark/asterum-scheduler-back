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
    @Column(length = 500)
    var notes: String? = null,
    @Column(nullable = false)
    val creatorId: Long,
    @OneToMany(mappedBy = "event", cascade = [CascadeType.ALL], orphanRemoval = true)
    val participants: MutableList<EventParticipant> = mutableListOf(),
) : BaseEntity() {

    fun update(
        title: String,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        locationId: Long?,
        notes: String?,
        participantIds: List<Long>,
    ) {
        this.title = title
        this.startTime = startTime
        this.endTime = endTime
        this.locationId = locationId
        this.notes = notes
        replaceParticipants(participantIds)
    }

    fun replaceParticipants(userIds: List<Long>) {
        participants.clear()
        userIds.forEach { participants.add(EventParticipant(event = this, userId = it)) }
    }
}
