package com.sandro.asterumscheduler.event.application

import jakarta.validation.constraints.NotNull

data class EventThisAndFutureParticipantsUpdateRequest(
    @field:NotNull
    val userIds: Set<Long>,
)
