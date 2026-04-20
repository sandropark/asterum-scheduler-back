package com.sandro.asterumscheduler.user.application

import com.sandro.asterumscheduler.common.user.UserReader
import com.sandro.asterumscheduler.user.infra.UserRepository
import org.springframework.stereotype.Component

@Component
class UserReaderImpl(private val userRepository: UserRepository) : UserReader {
    override fun findNotExistingIds(ids: List<Long>): List<Long> {
        val existingIds = userRepository.findAllById(ids).map { it.id }.toSet()
        return ids.filter { it !in existingIds }
    }
}
