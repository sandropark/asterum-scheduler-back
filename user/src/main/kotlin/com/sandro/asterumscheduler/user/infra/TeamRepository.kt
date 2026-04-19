package com.sandro.asterumscheduler.user.infra

import com.sandro.asterumscheduler.user.domain.Team
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository

interface TeamRepository : JpaRepository<Team, Long> {
    fun findAllBy(pageable: Pageable): Slice<Team>
}
