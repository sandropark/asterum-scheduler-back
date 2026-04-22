package com.sandro.asterumscheduler.event.application

data class ParticipantSummary(
    val id: Long,
    val name: String,
    val isTeam: Boolean = false,
    val members: List<ParticipantSummary> = emptyList(),
)
