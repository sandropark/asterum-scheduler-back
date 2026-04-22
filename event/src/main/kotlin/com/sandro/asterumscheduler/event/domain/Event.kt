package com.sandro.asterumscheduler.event.domain

import com.sandro.asterumscheduler.common.domain.BaseEntity
import jakarta.persistence.*
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

@Entity
@Table(name = "events")
@SQLRestriction("deleted_at IS NULL")
class Event(
    @Column(name = "title", nullable = false)
    var title: String,

    @Column(name = "start_at", nullable = false)
    var startAt: LocalDateTime,

    @Column(name = "end_at", nullable = false)
    var endAt: LocalDateTime,

    @Column(name = "rrule")
    var rrule: String? = null,

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
        protected set
}
