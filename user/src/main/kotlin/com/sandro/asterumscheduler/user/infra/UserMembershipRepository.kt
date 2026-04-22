package com.sandro.asterumscheduler.user.infra

import com.sandro.asterumscheduler.user.domain.UserMembership
import org.springframework.data.jpa.repository.JpaRepository

interface UserMembershipRepository : JpaRepository<UserMembership, Long> {
    fun findAllByTeamIdIn(teamIds: Collection<Long>): List<UserMembership>
}
