package com.sandro.asterumscheduler.location.presentation

data class LocationResponse(
    val id: Long,
    val name: String,
    val capacity: Int,
    val available: Boolean,
)
