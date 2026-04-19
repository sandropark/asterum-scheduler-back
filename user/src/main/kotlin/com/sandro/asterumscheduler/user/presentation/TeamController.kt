package com.sandro.asterumscheduler.user.presentation

import com.sandro.asterumscheduler.common.response.ApiResponse
import com.sandro.asterumscheduler.common.response.SliceResponse
import com.sandro.asterumscheduler.user.application.TeamService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class TeamController(private val teamService: TeamService) : TeamApi {

    @GetMapping("/teams")
    override fun getTeams(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): ApiResponse<SliceResponse<TeamResponse>> = ApiResponse.ok(teamService.getTeams(page, size))
}
