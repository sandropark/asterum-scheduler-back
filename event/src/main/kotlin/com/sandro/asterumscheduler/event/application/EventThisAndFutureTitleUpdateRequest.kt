package com.sandro.asterumscheduler.event.application

import jakarta.validation.constraints.NotBlank

data class EventThisAndFutureTitleUpdateRequest(
    @field:NotBlank
    val title: String,
)
