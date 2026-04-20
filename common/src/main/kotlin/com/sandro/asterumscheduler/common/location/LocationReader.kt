package com.sandro.asterumscheduler.common.location

interface LocationReader {
    fun existsById(locationId: Long): Boolean
}
