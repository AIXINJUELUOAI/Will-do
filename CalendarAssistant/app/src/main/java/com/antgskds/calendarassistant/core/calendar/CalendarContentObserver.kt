package com.antgskds.calendarassistant.core.calendar

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 日历内容观察者
 * 监听系统日历的变化，触发反向同步
 *
 * 修复：使用 Job.cancel() 机制实现正确的防抖逻辑
 */
class CalendarContentObserver(
    private val context: Context,
    private val onCalendarChanged: () -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {

    companion object {
        private const val TAG = "CalendarContentObserver"
        private const val DEBOUNCE_DELAY_MS = 2000L // 防抖延迟：2秒
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRegistered = false

    // 🔥 修复：保存当前的防抖 Job，用于取消之前的任务
    private var debounceJob: Job? = null

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
            // 取消防抖任务
            debounceJob?.cancel()
            debounceJob = null
            Log.d(TAG, "日历内容观察者已取消注册")
        }
    }

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        super.onChange(selfChange, uri)
        Log.d(TAG, "检测到日历变化: $uri")

        // 🔥 修复：取消之前的防抖任务，确保只有最后一次变更会触发回调
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_DELAY_MS)
            onCalendarChanged()
        }
    }
}
