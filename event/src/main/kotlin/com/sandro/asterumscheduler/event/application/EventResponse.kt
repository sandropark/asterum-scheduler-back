package com.sandro.asterumscheduler.event.application

import java.time.LocalDateTime

data class EventResponse(
    val id: Long,
    val title: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val locationId: Long?,
    val notes: String?,
    val creatorId: Long,
    val participants: List<ParticipantResponse>,
)

data class ParticipantResponse(
    val userId: Long,
)
