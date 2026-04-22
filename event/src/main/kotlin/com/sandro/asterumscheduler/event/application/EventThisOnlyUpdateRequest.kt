package com.sandro.asterumscheduler.event.application

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

data class EventThisOnlyUpdateRequest(
    @field:NotBlank
    val title: String,
    @field:NotNull
    val startAt: LocalDateTime,
    @field:NotNull
    val endAt: LocalDateTime,
)
