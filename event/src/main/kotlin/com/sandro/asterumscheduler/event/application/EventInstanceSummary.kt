package com.sandro.asterumscheduler.event.application

import java.time.LocalDateTime

data class EventInstanceSummary(
    val id: Long,
    val title: String,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
)
