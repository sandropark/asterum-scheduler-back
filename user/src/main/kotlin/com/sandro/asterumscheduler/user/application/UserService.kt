package com.sandro.asterumscheduler.user.application

import com.sandro.asterumscheduler.common.response.SliceResponse
import com.sandro.asterumscheduler.user.infra.UserRepository
import com.sandro.asterumscheduler.user.presentation.UserResponse
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserService(private val userRepository: UserRepository) {

    fun getUsers(page: Int, size: Int): SliceResponse<UserResponse> {
        val slice = userRepository.findAllWithTeam(PageRequest.of(page, size))
        return SliceResponse(
            content = slice.content.map { u -> UserResponse(u.id, u.name, u.email, u.team.id, u.team.name) },
            hasNext = slice.hasNext(),
        )
    }
}
