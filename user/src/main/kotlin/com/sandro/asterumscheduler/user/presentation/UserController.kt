package com.sandro.asterumscheduler.user.presentation

import com.sandro.asterumscheduler.common.response.ApiResponse
import com.sandro.asterumscheduler.common.response.SliceResponse
import com.sandro.asterumscheduler.user.application.UserService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class UserController(private val userService: UserService) : UserApi {

    @GetMapping("/users")
    override fun getUsers(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): ApiResponse<SliceResponse<UserResponse>> = ApiResponse.ok(userService.getUsers(page, size))
}
