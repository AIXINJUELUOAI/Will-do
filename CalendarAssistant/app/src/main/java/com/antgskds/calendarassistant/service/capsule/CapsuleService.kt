package com.antgskds.calendarassistant.service.capsule

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.antgskds.calendarassistant.core.util.FlymeUtils
import com.antgskds.calendarassistant.service.capsule.provider.FlymeCapsuleProvider
import com.antgskds.calendarassistant.service.capsule.provider.ICapsuleProvider
import com.antgskds.calendarassistant.service.capsule.provider.NativeCapsuleProvider

/**
 * 实况胶囊前台服务 (裁判员版) - 已增强同步机制
 */
class CapsuleService : Service() {

    companion object {
        const val TAG = "CapsuleService"
        const val ACTION_START = "ACTION_CAPSULE_START"
        const val ACTION_STOP = "ACTION_CAPSULE_STOP"
        const val ACTION_SYNC = "ACTION_CAPSULE_SYNC" // 新增：全量同步指令

        const val TYPE_SCHEDULE = 1
        const val TYPE_PICKUP = 2

        @Volatile
        var isServiceRunning = false
            private set
    }

    // 新增 originalId 字段，用于比对白名单
    private data class CapsuleMetadata(
        val notificationId: Int,
        val originalId: String, // 事件的 String UUID 或 聚合 ID
        val notification: Notification,
        val type: Int,
        val startTime: Long,
        val endTime: Long
    )

    // Key: Notification ID (Int)
    private val activeCapsules = mutableMapOf<Int, CapsuleMetadata>()

    private var currentForegroundId = -1
    private lateinit var provider: ICapsuleProvider
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        provider = if (FlymeUtils.isFlyme()) FlymeCapsuleProvider() else NativeCapsuleProvider()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        // 通用参数解析
        val explicitId = intent?.getIntExtra("NOTIF_ID", -1) ?: -1
        val eventIdStr = intent?.getStringExtra("EVENT_ID") ?: ""
        // 如果没有指定 Int ID，则使用 String Hash
        val notificationId = if (explicitId != -1) explicitId else eventIdStr.hashCode()

        when (action) {
            ACTION_START -> handleStart(intent, notificationId, eventIdStr)
            ACTION_STOP -> handleStop(notificationId)
            ACTION_SYNC -> handleSync(intent) // 处理同步
        }

        return START_NOT_STICKY
    }

    /**
     * 【核心修复】处理全量同步
     * 接收一份“白名单”，杀死所有不在白名单里的胶囊
     */
    private fun handleSync(intent: Intent?) {
        val activeIdsList = intent?.getStringArrayListExtra("ACTIVE_IDS") ?: return
        val activeIdsSet = activeIdsList.toSet()

        Log.d(TAG, "执行全量同步，白名单ID: $activeIdsSet")

        // 找出所有“不在白名单中”的僵尸胶囊 Key
        val zombieKeys = activeCapsules.filter { entry ->
            val metadata = entry.value
            // 如果 metadata.originalId 不在白名单里，它就是僵尸
            !activeIdsSet.contains(metadata.originalId)
        }.keys

        if (zombieKeys.isNotEmpty()) {
            Log.d(TAG, "发现僵尸胶囊，正在清除: $zombieKeys")
            zombieKeys.forEach { key ->
                handleStop(key)
            }
        } else {
            // 即使没有僵尸，也要刷新一下前台状态，确保排序正确
            refreshForegroundState()
        }
    }

    private fun handleStart(intent: Intent?, notificationId: Int, eventId: String) {
        val title = intent?.getStringExtra("EVENT_TITLE") ?: "日程进行中"
        val location = intent?.getStringExtra("EVENT_LOCATION") ?: ""
        val startTimeStr = intent?.getStringExtra("EVENT_START_TIME") ?: ""
        val endTimeStr = intent?.getStringExtra("EVENT_END_TIME") ?: ""
        val color = intent?.getIntExtra("EVENT_COLOR", Color.GREEN) ?: Color.GREEN
        val type = intent?.getIntExtra("TYPE", TYPE_SCHEDULE) ?: TYPE_SCHEDULE
        val startMillis = intent?.getLongExtra("START_MILLIS", 0L) ?: System.currentTimeMillis()
        val endMillis = intent?.getLongExtra("END_MILLIS", Long.MAX_VALUE) ?: Long.MAX_VALUE

        val content = if (intent?.hasExtra("EVENT_CONTENT") == true) {
            intent.getStringExtra("EVENT_CONTENT") ?: ""
        } else {
            if (startTimeStr.isNotEmpty()) "$startTimeStr - $endTimeStr\n$location" else location
        }

        val notification = provider.buildNotification(this, eventId, title, content.trim(), color)

        // 存入元数据 (包含 originalId)
        val metadata = CapsuleMetadata(notificationId, eventId, notification, type, startMillis, endMillis)
        activeCapsules[notificationId] = metadata

        refreshForegroundState()
    }

    private fun handleStop(notificationId: Int) {
        if (activeCapsules.containsKey(notificationId)) {
            activeCapsules.remove(notificationId)

            // 无论它是 winner 还是 loser，都先从 NotificationManager 移除
            // 避免“降级显示”逻辑导致的残留
            try {
                notificationManager.cancel(notificationId)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            if (notificationId == currentForegroundId) {
                if (activeCapsules.isNotEmpty()) {
                    refreshForegroundState()
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun refreshForegroundState() {
        if (activeCapsules.isEmpty()) return

        val now = System.currentTimeMillis()
        // 筛选未过期的
        val candidates = activeCapsules.values.filter { now < it.endTime }

        if (candidates.isEmpty()) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }

        // 排序：开始时间晚(新) > 类型大(Pickup > Schedule)
        val winner = candidates.sortedWith(
            compareByDescending<CapsuleMetadata> { it.startTime }
                .thenByDescending { it.type }
        ).first()

        // 晋升 Winner
        if (currentForegroundId != winner.notificationId) {
            promoteToForeground(winner.notificationId, winner.notification)
        }

        // 处理 Losers (降级显示)
        activeCapsules.values.forEach { capsule ->
            if (capsule.notificationId != winner.notificationId) {
                if (now < capsule.endTime) {
                    notificationManager.notify(capsule.notificationId, capsule.notification)
                }
            }
        }
    }

    private fun promoteToForeground(id: Int, notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(id, notification)
            }
            currentForegroundId = id
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            notificationManager.notify(id, notification)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        activeCapsules.clear()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}