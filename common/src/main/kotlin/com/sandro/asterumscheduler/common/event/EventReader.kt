package com.sandro.asterumscheduler.common.event

import java.time.LocalDateTime

interface EventReader {
    fun findReservedLocationIds(start: LocalDateTime, end: LocalDateTime): List<Long>
}
