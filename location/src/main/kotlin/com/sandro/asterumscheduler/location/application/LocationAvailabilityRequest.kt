package com.sandro.asterumscheduler.location.application

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotNull
import org.springframework.format.annotation.DateTimeFormat
import java.time.LocalDateTime

data class LocationAvailabilityRequest(
    @field:NotNull
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    val start: LocalDateTime,
    @field:NotNull
    @field:DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    val end: LocalDateTime,
) {
    @get:JsonIgnore
    @get:AssertTrue(message = "종료 시간은 시작 시간보다 이후여야 합니다.")
    val isTimeRangeValid: Boolean
        get() = end.isAfter(start)
}
