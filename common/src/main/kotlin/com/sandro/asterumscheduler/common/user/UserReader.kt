package com.sandro.asterumscheduler.common.user

interface UserReader {
    fun findByIds(ids: Set<Long>): List<UserInfo>
}
