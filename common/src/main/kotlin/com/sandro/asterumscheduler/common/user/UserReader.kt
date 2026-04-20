package com.sandro.asterumscheduler.common.user

interface UserReader {
    fun findNotExistingIds(ids: List<Long>): List<Long>
}
