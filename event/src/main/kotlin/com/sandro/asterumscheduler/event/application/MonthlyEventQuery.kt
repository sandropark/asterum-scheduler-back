package com.sandro.asterumscheduler.event.application

import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotNull
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDateTime

data class MonthlyEventQuery(
    @field:NotNull
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    val from: LocalDateTime,
    @field:NotNull
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    val to: LocalDateTime,
) {
    @get:AssertTrue(message = "from 은 to 보다 이전이어야 합니다")
    val isRangeValid: Boolean
        get() = from.isBefore(to)
}
