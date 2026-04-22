package com.sandro.asterumscheduler.event.application

import java.time.LocalDateTime

data class EventAllTimeUpdateRequest(
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val rrule: String? = null,
)
