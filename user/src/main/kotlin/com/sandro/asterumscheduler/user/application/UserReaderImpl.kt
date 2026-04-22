package com.sandro.asterumscheduler.user.application

import com.sandro.asterumscheduler.common.user.UserInfo
import com.sandro.asterumscheduler.common.user.UserReader
import com.sandro.asterumscheduler.user.infra.UserMembershipRepository
import com.sandro.asterumscheduler.user.infra.UserRepository
import org.springframework.stereotype.Component

@Component
class UserReaderImpl(
    private val userRepository: UserRepository,
    private val userMembershipRepository: UserMembershipRepository,
) : UserReader {
    override fun findByIds(ids: Set<Long>): List<UserInfo> =
        userRepository.findAllById(ids).map { UserInfo(id = it.id!!, name = it.name, isTeam = it.isTeam) }

    override fun findExistingIds(ids: Set<Long>): Set<Long> =
        userRepository.findAllById(ids).mapNotNull { it.id }.toSet()

    override fun findMembersByTeamIds(teamIds: Set<Long>): Map<Long, List<UserInfo>> {
        val memberships = userMembershipRepository.findAllByTeamIdIn(teamIds)
        if (memberships.isEmpty()) return emptyMap()
        val memberIds = memberships.map { it.memberId }.toSet()
        val memberById = userRepository.findAllById(memberIds).associateBy { it.id!! }
        return memberships.groupBy({ it.teamId }) { membership ->
            val m = memberById[membership.memberId]!!
            UserInfo(id = m.id!!, name = m.name, isTeam = m.isTeam)
        }
    }
}
