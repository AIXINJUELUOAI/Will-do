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
 * 使用 Handler.postDelayed 防抖，避免同步写入日历后再次触发 → 无限循环
 */
class CalendarContentObserver(
    private val context: Context,
    private val onCalendarChanged: () -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    companion object {
        private const val TAG = "CalendarContentObserver"
        /** 防抖窗口：Android 日历 Provider 一次逻辑变更通常在 500ms-1s 内发完所有通知 */
        private const val DEBOUNCE_MS = 3000L
    }

    private var isRegistered = false
    private val handler = Handler(Looper.getMainLooper())
    private val debounceRunnable = Runnable {
        Log.d(TAG, "防抖窗口结束，触发反向同步")
        onCalendarChanged()
    }

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

    fun unregister() {
        if (isRegistered) {
            handler.removeCallbacks(debounceRunnable)
            context.contentResolver.unregisterContentObserver(this)
            isRegistered = false
            Log.d(TAG, "日历内容观察者已取消注册")
        }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.d(TAG, "检测到日历变化: selfChange=$selfChange, uri=$uri")
        // 移除上一次的 pending 回调，重新计时，实现防抖
        handler.removeCallbacks(debounceRunnable)
        handler.postDelayed(debounceRunnable, DEBOUNCE_MS)
    }
}
