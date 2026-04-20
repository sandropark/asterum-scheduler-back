package com.sandro.asterumscheduler.event.application

import com.sandro.asterumscheduler.event.domain.Event
import java.time.LocalDateTime

data class EventResponse(
    val id: Long,
    val title: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val locationId: Long?,
    val notes: String?,
    val creatorId: Long,
) {
    companion object {
        fun from(event: Event): EventResponse = EventResponse(
            id = event.id,
            title = event.title,
            startTime = event.startTime,
            endTime = event.endTime,
            locationId = event.locationId,
            notes = event.notes,
            creatorId = event.creatorId,
        )
    }
}
