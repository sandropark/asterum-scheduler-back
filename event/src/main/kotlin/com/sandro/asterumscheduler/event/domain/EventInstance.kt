package com.sandro.asterumscheduler.event.domain

import com.sandro.asterumscheduler.common.domain.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

@Entity
@Table(name = "events_instances")
@SQLRestriction("deleted_at IS NULL")
class EventInstance(
    @Column(name = "event_id", nullable = false)
    var eventId: Long,

    @Column(name = "start_at", nullable = false)
    var startAt: LocalDateTime,

    @Column(name = "end_at", nullable = false)
    var endAt: LocalDateTime,

    @Column(name = "title")
    var title: String? = null,

    @Column(name = "has_override_participants", nullable = false)
    var hasOverrideParticipants: Boolean = false,

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
        protected set
}
