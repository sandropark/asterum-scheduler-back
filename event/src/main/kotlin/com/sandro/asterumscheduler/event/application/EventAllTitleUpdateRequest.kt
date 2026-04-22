package com.sandro.asterumscheduler.event.application

import jakarta.validation.constraints.NotBlank

data class EventAllTitleUpdateRequest(
    @field:NotBlank
    val title: String,
)
