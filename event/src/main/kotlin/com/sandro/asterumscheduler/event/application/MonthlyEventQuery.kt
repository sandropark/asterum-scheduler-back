package com.sandro.asterumscheduler.event.application

import java.time.LocalDateTime

data class MonthlyEventQuery(
    val from: LocalDateTime,
    val to: LocalDateTime,
)
