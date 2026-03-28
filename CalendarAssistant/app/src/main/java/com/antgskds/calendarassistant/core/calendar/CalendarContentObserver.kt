package com.antgskds.calendarassistant.core.calendar

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log

/**
 * 日历内容观察者
 * 监听系统日历的变化，触发反向同步
 *
 * 使用 WorkManager 进行反向同步防抖
 */
class CalendarContentObserver(
    private val context: Context,
    private val onCalendarChanged: () -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    companion object {
        private const val TAG = "CalendarContentObserver"
    }

    private var isRegistered = false

    /**
     * 注册监听
     */
    fun register() {
        if (!isRegistered) {
            context.contentResolver.registerContentObserver(
                CalendarContract.Events.CONTENT_URI,
                true, // notifyForDescendants - 监听所有子 URI
                this
            )
            isRegistered = true
            Log.d(TAG, "日历内容观察者已注册")
        }
    }

    /**
     * 取消监听
     */
    fun unregister() {
        if (isRegistered) {
            context.contentResolver.unregisterContentObserver(this)
            isRegistered = false
            Log.d(TAG, "日历内容观察者已取消注册")
        }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.d(TAG, "检测到日历变化: selfChange=$selfChange, uri=$uri")
        onCalendarChanged()
    }
}
