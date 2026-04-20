package com.sandro.asterumscheduler.event.application

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Size
import org.hibernate.validator.constraints.Length
import java.time.LocalDateTime

data class CreateEventRequest(
    @field:Length(min = 1, max = 255)
    val title: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val locationId: Long? = null,
    @field:Length(max = 500)
    val notes: String? = null,
    val creatorId: Long, // TODO: 인증 컨텍스트에서 꺼내기
    @field:Size(max = 100)
    val participantIds: List<Long> = emptyList(),
) {
    @get:JsonIgnore
    @get:AssertTrue(message = "종료 시간은 시작 시간과 같거나 이후여야 합니다.")
    val isTimeRangeValid: Boolean
        get() = !endTime.isBefore(startTime)
}
