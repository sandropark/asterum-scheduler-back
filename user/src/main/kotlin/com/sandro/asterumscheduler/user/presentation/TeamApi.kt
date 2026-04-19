package com.sandro.asterumscheduler.user.presentation

import com.sandro.asterumscheduler.common.response.ApiResponse
import com.sandro.asterumscheduler.common.response.SliceResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse as SwaggerApiResponse
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Team", description = "팀 API")
interface TeamApi {

    @Operation(summary = "팀 목록 조회")
    @SwaggerApiResponse(responseCode = "200", description = "성공")
    fun getTeams(
        @Parameter(description = "페이지 번호 (0부터 시작)", example = "0") page: Int,
        @Parameter(description = "페이지 크기", example = "10") size: Int,
    ): ApiResponse<SliceResponse<TeamResponse>>
}
