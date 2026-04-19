package com.sandro.asterumscheduler.common.response

data class SliceResponse<T>(
    val content: List<T>,
    val hasNext: Boolean,
)
