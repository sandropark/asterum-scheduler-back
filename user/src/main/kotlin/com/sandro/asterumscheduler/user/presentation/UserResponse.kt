package com.sandro.asterumscheduler.user.presentation

data class UserResponse(
    val id: Long,
    val name: String,
    val email: String,
    val teamId: Long,
    val teamName: String,
)
