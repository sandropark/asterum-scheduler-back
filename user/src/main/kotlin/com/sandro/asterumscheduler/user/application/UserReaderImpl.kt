package com.sandro.asterumscheduler.user.application

import com.sandro.asterumscheduler.common.user.UserInfo
import com.sandro.asterumscheduler.common.user.UserReader
import com.sandro.asterumscheduler.user.infra.UserRepository
import org.springframework.stereotype.Component

@Component
class UserReaderImpl(private val userRepository: UserRepository) : UserReader {
    override fun findByIds(ids: Set<Long>): List<UserInfo> =
        userRepository.findAllById(ids).map { UserInfo(id = it.id!!, name = it.name) }

    override fun findExistingIds(ids: Set<Long>): Set<Long> =
        userRepository.findAllById(ids).mapNotNull { it.id }.toSet()
}
