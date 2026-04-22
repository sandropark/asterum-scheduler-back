package com.sandro.asterumscheduler.event.application

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class EventAllTimeUpdateRequest(
    @field:NotNull
    val startAt: LocalDateTime,
    @field:NotNull
    val endAt: LocalDateTime,
    @field:Size(max = 500)
    val rrule: String? = null,
) {
    @get:AssertTrue(message = "startAt 은 endAt 보다 이전이어야 합니다")
    val isTimeRangeValid: Boolean
        get() = startAt.isBefore(endAt)
}
