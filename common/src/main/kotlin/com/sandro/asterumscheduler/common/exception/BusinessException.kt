package com.sandro.asterumscheduler.common.exception

class BusinessException(val errorCode: ErrorCode, val e: Throwable? = null) : RuntimeException(errorCode.message, e)
