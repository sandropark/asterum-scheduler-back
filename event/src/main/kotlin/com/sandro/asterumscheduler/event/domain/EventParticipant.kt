package com.sandro.asterumscheduler.event.domain

import com.sandro.asterumscheduler.common.BaseEntity
import jakarta.persistence.*

@Entity
@Table(
    name = "event_participants",
    uniqueConstraints = [UniqueConstraint(columnNames = ["event_id", "user_id"])],
)
class EventParticipant(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    val event: Event,
    @Column(nullable = false)
    val userId: Long,
    // TODO: 초대 상태 추가. (요청, 참여, 거부 등)
) : BaseEntity()
