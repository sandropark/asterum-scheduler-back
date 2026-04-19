package com.sandro.asterumscheduler.user.application

import com.sandro.asterumscheduler.common.response.SliceResponse
import com.sandro.asterumscheduler.user.infra.TeamRepository
import com.sandro.asterumscheduler.user.presentation.TeamResponse
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class TeamService(private val teamRepository: TeamRepository) {

    fun getTeams(page: Int, size: Int): SliceResponse<TeamResponse> {
        val slice = teamRepository.findAllBy(PageRequest.of(page, size))
        return SliceResponse(
            content = slice.content.map { t -> TeamResponse(t.id, t.name) },
            hasNext = slice.hasNext(),
        )
    }
}
