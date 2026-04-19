package com.sandro.asterumscheduler.common.response

import com.sandro.asterumscheduler.common.exception.ErrorCode

data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val error: ErrorResponse?,
) {
    companion object {
        fun <T> ok(data: T): ApiResponse<T> = ApiResponse(true, data, null)
        fun <T> ok(): ApiResponse<T> = ApiResponse(true, null, null)
        fun <T> fail(errorCode: ErrorCode): ApiResponse<T> =
            ApiResponse(false, null, ErrorResponse(errorCode.code, errorCode.message))
        fun <T> fail(errorCode: ErrorCode, message: String?): ApiResponse<T> =
            ApiResponse(false, null, ErrorResponse(errorCode.code, message))
    }
}

data class ErrorResponse(
    val code: String,
    val message: String?,
)
