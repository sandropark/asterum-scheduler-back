package com.sandro.asterumscheduler.location.infra

import com.sandro.asterumscheduler.location.domain.Location
import org.springframework.data.jpa.repository.JpaRepository

interface LocationRepository : JpaRepository<Location, Long>
