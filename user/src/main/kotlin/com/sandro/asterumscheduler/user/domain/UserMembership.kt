package com.sandro.asterumscheduler.user.domain

import com.sandro.asterumscheduler.common.domain.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "user_memberships")
class UserMembership(
    @Column(name = "team_id", nullable = false)
    var teamId: Long,

    @Column(name = "member_id", nullable = false)
    var memberId: Long,
) : BaseEntity() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    var id: Long? = null
        protected set
}
