package com.sandro.asterumscheduler.common.advice

import com.sandro.asterumscheduler.common.exception.BusinessException
import com.sandro.asterumscheduler.common.exception.ErrorCode
import com.sandro.asterumscheduler.common.response.ApiResponse
import mu.KotlinLogging
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

private val logger = KotlinLogging.logger {}

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(e: BusinessException): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("Validation error occurred: ${e.message}", e)
        return ResponseEntity.status(e.errorCode.status).body(ApiResponse.fail(e.errorCode, e.message))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(e: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("Validation error occurred: ${e.message}", e)
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.INVALID_INPUT, e.message))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleValidationException(e: HttpMessageNotReadableException): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("Validation error occurred: ${e.message}", e)
        return ResponseEntity.badRequest().body(ApiResponse.fail(ErrorCode.INVALID_INPUT, e.message))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<ApiResponse<Nothing>> {
        logger.error("Internal server error occurred: ${e.message}", e)
        return ResponseEntity.internalServerError().body(ApiResponse.fail(ErrorCode.INTERNAL_SERVER_ERROR, e.message))
    }
}
