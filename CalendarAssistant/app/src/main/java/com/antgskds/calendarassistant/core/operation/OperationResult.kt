package com.antgskds.calendarassistant.core.operation

sealed class OperationResult<out T> {
    data class Success<T>(val data: T) : OperationResult<T>()
    data class Failure(val code: OperationErrorCode, val message: String? = null) : OperationResult<Nothing>()
}
