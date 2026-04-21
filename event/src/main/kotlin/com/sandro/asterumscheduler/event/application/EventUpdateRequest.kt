package com.sandro.asterumscheduler.event.application

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class EventUpdateRequest(
    @field:NotBlank
    @field:Size(max = 255)
    val title: String,
)
