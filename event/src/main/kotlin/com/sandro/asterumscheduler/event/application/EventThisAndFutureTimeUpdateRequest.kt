package com.sandro.asterumscheduler.event.application

import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

data class EventThisAndFutureTimeUpdateRequest(
    @field:NotNull
    val startAt: LocalDateTime,
    @field:NotNull
    val endAt: LocalDateTime,
    val rrule: String? = null,
)
