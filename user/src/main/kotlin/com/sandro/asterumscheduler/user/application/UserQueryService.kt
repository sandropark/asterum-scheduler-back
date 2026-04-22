package com.sandro.asterumscheduler.user.application

import com.sandro.asterumscheduler.user.infra.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserQueryService(
    private val userRepository: UserRepository,
) {
    fun findAll(filter: UserListFilter = UserListFilter.USER): List<UserSummary> {
        val users = when (filter) {
            UserListFilter.USER -> userRepository.findByIsTeam(false)
            UserListFilter.TEAM -> userRepository.findByIsTeam(true)
            UserListFilter.ALL  -> userRepository.findAll()
        }
        return users.map { UserSummary(it.id!!, it.name, it.isTeam) }
    }
}
