package com.sandro.asterumscheduler.user.infra

import com.sandro.asterumscheduler.user.domain.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long>
