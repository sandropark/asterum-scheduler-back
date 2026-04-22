package com.sandro.asterumscheduler.common.user

interface UserReader {
    fun findByIds(ids: Set<Long>): List<UserInfo>
    fun findExistingIds(ids: Set<Long>): Set<Long>
}
