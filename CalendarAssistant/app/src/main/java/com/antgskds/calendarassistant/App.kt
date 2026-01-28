package com.antgskds.calendarassistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import com.antgskds.calendarassistant.core.calendar.CalendarContentObserver
import com.antgskds.calendarassistant.core.calendar.CalendarPermissionHelper
import com.antgskds.calendarassistant.data.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class App : Application() {

    companion object {
        // 全局通知渠道常量
        const val CHANNEL_ID_POPUP = "calendar_assistant_popup_channel_v2"
        const val CHANNEL_ID_LIVE = "calendar_assistant_live_channel_v3"

        private const val TAG = "App"
    }

    // 全局单例 Repository (懒加载)
    val repository: AppRepository by lazy {
        AppRepository.getInstance(this)
    }

    // 日历内容观察者（可选，仅在有权限时初始化）
    private var calendarObserver: CalendarContentObserver? = null

    override fun onCreate() {
        super.onCreate()

        // 初始化通知渠道
        createNotificationChannels()

        // 初始化日历内容观察者（仅在已有权限时）
        initCalendarObserverIfPermissionGranted()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            // A. 普通提醒渠道 (High Priority, 有声音/震动)
            val popupChannel = NotificationChannel(
                CHANNEL_ID_POPUP,
                "日程提醒",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "普通日程的弹窗提醒"
                enableLights(true)
                enableVibration(true)
            }

            // B. 实况胶囊渠道 (High Priority, 但静音)
            // 胶囊通常伴随系统闹钟，或者是静默显示的 Live Activity，所以不该自己乱叫
            val liveChannel = NotificationChannel(
                CHANNEL_ID_LIVE,
                "实况胶囊",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "进行中日程的实况胶囊"
                setSound(null, null) // 静音
                setShowBadge(false)  // 不显示角标
            }

            notificationManager.createNotificationChannels(listOf(popupChannel, liveChannel))
        }
    }

    /**
     * 初始化日历内容观察者（仅在已有权限时）
     * 避免新安装未授权时崩溃或报错
     */
    private fun initCalendarObserverIfPermissionGranted() {
        if (CalendarPermissionHelper.hasAllPermissions(this)) {
            initCalendarObserver()
        } else {
            Log.d(TAG, "日历权限未授予，跳过 Observer 初始化")
        }
    }

    /**
     * 初始化日历内容观察者
     * 监听系统日历的变化，用于反向同步
     * 此方法为 public，可供外部在权限授予后调用
     */
    fun initCalendarObserver() {
        if (calendarObserver != null) {
            Log.d(TAG, "日历 Observer 已初始化，跳过")
            return
        }

        // 用于反向同步的 CoroutineScope
        val syncScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        calendarObserver = CalendarContentObserver(applicationContext) {
            Log.d(TAG, "检测到系统日历变化（已防抖 2 秒）")

            // 触发反向同步：系统日历 -> App
            syncScope.launch {
                try {
                    val result = repository.syncFromCalendar()
                    if (result.isSuccess) {
                        val count = result.getOrNull() ?: 0
                        if (count > 0) {
                            Log.d(TAG, "反向同步成功：从系统日历同步了 $count 个事件")
                        }
                    } else {
                        Log.w(TAG, "反向同步失败：${result.exceptionOrNull()?.message}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "反向同步异常", e)
                }
            }
        }
        calendarObserver?.register()
        Log.d(TAG, "日历内容观察者已初始化并注册")
    }
}
