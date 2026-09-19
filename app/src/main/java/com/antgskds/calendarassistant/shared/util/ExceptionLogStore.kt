package com.antgskds.calendarassistant.shared.util

import android.content.Context

/** 异常和卡顿沿用原入口，统一写入当天应用日志。 */
object ExceptionLogStore {
    @Suppress("UNUSED_PARAMETER")
    fun append(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        AppLogger.e(tag, message, throwable)
    }
}
