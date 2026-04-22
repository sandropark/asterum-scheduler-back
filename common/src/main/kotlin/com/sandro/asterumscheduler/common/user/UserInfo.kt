package com.sandro.asterumscheduler.common.user

data class UserInfo(
    val id: Long,
    val name: String,
    val isTeam: Boolean = false,
)
