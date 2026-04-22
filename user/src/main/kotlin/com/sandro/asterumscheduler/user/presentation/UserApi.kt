package com.sandro.asterumscheduler.user.presentation

import com.sandro.asterumscheduler.common.response.ApiResponse
import com.sandro.asterumscheduler.user.application.UserListFilter
import com.sandro.asterumscheduler.user.application.UserSummary
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam

@RequestMapping("/api/users")
@Tag(name = "Users", description = "사용자 관리 API")
interface UserApi {

    @Operation(summary = "사용자 목록 조회")
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "USER") filter: UserListFilter,
    ): ApiResponse<List<UserSummary>>
}
