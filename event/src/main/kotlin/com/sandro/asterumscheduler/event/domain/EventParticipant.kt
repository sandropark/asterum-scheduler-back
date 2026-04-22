package com.sandro.asterumscheduler.event.domain

import com.sandro.asterumscheduler.common.domain.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "event_participants")
open class EventParticipant(
    @Column(name = "event_id", nullable = false)
    var eventId: Long,

    @Column(name = "user_id", nullable = false)
    var userId: Long,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
        protected set
}
