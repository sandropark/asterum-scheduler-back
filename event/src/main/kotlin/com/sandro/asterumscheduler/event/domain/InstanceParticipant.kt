package com.sandro.asterumscheduler.event.domain

import com.sandro.asterumscheduler.common.domain.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "instance_participants")
class InstanceParticipant(
    @Column(name = "instance_id", nullable = false)
    var instanceId: Long,

    @Column(name = "user_id", nullable = false)
    var userId: Long,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
        protected set
}
