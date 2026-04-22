package com.sandro.asterumscheduler.event.application

import java.time.LocalDateTime

data class EventThisAndFutureTimeUpdateRequest(
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val rrule: String? = null,
)
