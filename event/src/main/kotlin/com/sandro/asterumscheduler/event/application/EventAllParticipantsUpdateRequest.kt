package com.sandro.asterumscheduler.event.application

import jakarta.validation.constraints.NotNull

data class EventAllParticipantsUpdateRequest(
    @field:NotNull
    val userIds: Set<Long>,
)
