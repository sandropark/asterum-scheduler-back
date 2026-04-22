package com.sandro.asterumscheduler.event.infra

import com.sandro.asterumscheduler.event.domain.InstanceParticipant
import org.springframework.data.jpa.repository.JpaRepository

interface InstanceParticipantRepository : JpaRepository<InstanceParticipant, Long> {
    fun findAllByInstanceId(instanceId: Long): List<InstanceParticipant>
    fun deleteAllByInstanceId(instanceId: Long)
}
