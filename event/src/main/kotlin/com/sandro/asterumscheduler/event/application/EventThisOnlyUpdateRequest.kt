package com.sandro.asterumscheduler.event.application

import java.time.LocalDateTime

data class EventThisOnlyUpdateRequest(
    val title: String,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
)
