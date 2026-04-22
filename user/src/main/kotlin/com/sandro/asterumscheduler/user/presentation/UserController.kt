package com.sandro.asterumscheduler.user.presentation

import com.sandro.asterumscheduler.common.response.ApiResponse
import com.sandro.asterumscheduler.user.application.UserListFilter
import com.sandro.asterumscheduler.user.application.UserQueryService
import com.sandro.asterumscheduler.user.application.UserSummary
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class UserController(
    private val userQueryService: UserQueryService,
) : UserApi {

    override fun list(
        @RequestParam(defaultValue = "USER") filter: UserListFilter,
    ): ApiResponse<List<UserSummary>> =
        ApiResponse.ok(userQueryService.findAll(filter))
}
