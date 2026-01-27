package com.antgskds.calendarassistant

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.antgskds.calendarassistant.data.repository.AppRepository

class App : Application() {

    // 全局单例 Repository (懒加载)
    val repository: AppRepository by lazy {
        AppRepository.getInstance(this)
    }

    companion object {
        // 全局通知渠道常量
        const val CHANNEL_ID_POPUP = "calendar_assistant_popup_channel_v2"
        const val CHANNEL_ID_LIVE = "calendar_assistant_live_channel_v3"
    }

    override fun onCreate() {
        super.onCreate()

        // 初始化通知渠道
        createNotificationChannels()
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
}