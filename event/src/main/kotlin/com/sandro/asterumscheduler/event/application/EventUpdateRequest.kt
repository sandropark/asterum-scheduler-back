package com.sandro.asterumscheduler.event.application

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDate
import java.time.LocalDateTime

data class EventUpdateRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val title: String,
    @field:NotNull
    val startTime: LocalDateTime,
    @field:NotNull
    val endTime: LocalDateTime,
    val locationId: Long? = null,
    @field:Size(max = 500)
    val notes: String? = null,
    val targetDate: LocalDate? = null,
) {
    @get:JsonIgnore
    @get:AssertTrue(message = "시작일시는 종료일시보다 이후일 수 없습니다.")
    val isTimeRangeValid: Boolean
        get() = !startTime.isAfter(endTime)
}
