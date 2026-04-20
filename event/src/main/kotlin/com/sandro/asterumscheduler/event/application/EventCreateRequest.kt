package com.sandro.asterumscheduler.event.application

import com.fasterxml.jackson.annotation.JsonIgnore
import com.sandro.asterumscheduler.event.domain.Event
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class EventCreateRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val title: String,
    @field:NotNull
    val startTime: LocalDateTime,
    @field:NotNull
    val endTime: LocalDateTime,
    val locationId: Long? = null,
    val notes: String? = null,
) {
    @get:JsonIgnore
    @get:AssertTrue(message = "시작일시는 종료일시보다 이후일 수 없습니다.")
    val isTimeRangeValid: Boolean
        get() = !startTime.isAfter(endTime)

    fun toEntity(creatorId: Long): Event = Event(
        title = title,
        startTime = startTime,
        endTime = endTime,
        locationId = locationId,
        notes = notes,
        creatorId = creatorId,
    )
}
