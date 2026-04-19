package com.sandro.asterumscheduler.common.exception

class BusinessException(val errorCode: ErrorCode) : RuntimeException(errorCode.message)
