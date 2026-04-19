package com.sandro.asterumscheduler.user.infra

import com.sandro.asterumscheduler.user.domain.User
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface UserRepository : JpaRepository<User, Long> {
    @Query("SELECT u FROM User u JOIN FETCH u.team")
    fun findAllWithTeam(pageable: Pageable): Slice<User>
}
