package com.sandro.asterumscheduler.event.infra

import com.sandro.asterumscheduler.event.domain.EventInstance
import org.springframework.data.jpa.repository.JpaRepository

interface EventInstanceRepository : JpaRepository<EventInstance, Long>
