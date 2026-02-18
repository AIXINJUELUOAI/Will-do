package com.antgskds.calendarassistant.service.capsule

import android.app.Notification
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.core.capsule.CapsuleStateManager
import com.antgskds.calendarassistant.core.util.FlymeUtils
import com.antgskds.calendarassistant.data.state.CapsuleUiState
import com.antgskds.calendarassistant.service.capsule.provider.FlymeCapsuleProvider
import com.antgskds.calendarassistant.service.capsule.provider.ICapsuleProvider
import com.antgskds.calendarassistant.service.capsule.provider.NativeCapsuleProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 实况胶囊前台服务
 */
class CapsuleService : Service() {

    companion object {
        const val TAG = "CapsuleService"
        const val TYPE_SCHEDULE = 1
        const val TYPE_PICKUP = 2
        const val TYPE_PICKUP_EXPIRED = 3
        const val TYPE_NETWORK_SPEED = 4
        private const val PLACEHOLDER_FOREGROUND_ID = -1

        @Volatile
        var isServiceRunning = false
            private set
    }

    private data class CapsuleMetadata(
        val notificationId: Int,
        val originalId: String,
        val notification: Notification,
        val type: Int,
        val startTime: Long,
        val endTime: Long
    )

    private val activeCapsules = mutableMapOf<Int, CapsuleMetadata>()
    private var currentForegroundId = -1
    private lateinit var provider: ICapsuleProvider
    private lateinit var notificationManager: NotificationManager

    private var stateCollectJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var monitorJob: Job? = null
    private var isAggregateMode = false

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        provider = if (FlymeUtils.isFlyme()) FlymeCapsuleProvider() else NativeCapsuleProvider()
        startObservingCapsuleState()
        Log.d(TAG, "CapsuleService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (currentForegroundId == -1) {
            val placeholderNotification = createPlaceholderNotification()
            startForeground(1, placeholderNotification)
            currentForegroundId = PLACEHOLDER_FOREGROUND_ID
        }
        return START_NOT_STICKY
    }

    private fun createPlaceholderNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, App.CHANNEL_ID_LIVE)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val icon = Icon.createWithResource(this, R.drawable.ic_notification_small)
        return builder.setSmallIcon(icon)
            .setContentTitle("胶囊服务")
            .setContentText("初始化中...")
            .setOngoing(true)
            .setWhen(System.currentTimeMillis())
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setPriority(Notification.PRIORITY_MIN)
            .build()
    }

    private fun startObservingCapsuleState() {
        stateCollectJob = serviceScope.launch {
            val repository = (applicationContext as App).repository
            repository.capsuleStateManager.uiState.collect { capsuleState ->
                Log.d(TAG, "收到胶囊状态变化: $capsuleState")
                handleCapsuleStateChange(capsuleState)
            }
        }
    }

    private fun handleCapsuleStateChange(state: CapsuleUiState) {
        when (state) {
            is CapsuleUiState.None -> {
                Log.d(TAG, "无胶囊，停止服务")
                monitorJob?.cancel()
                isAggregateMode = false
                stopServiceSafely()
            }
            is CapsuleUiState.Active -> {
                Log.d(TAG, "活跃胶囊数量: ${state.capsules.size}")
                updateCapsules(state.capsules)
            }
        }
    }

    private fun updateCapsules(newCapsules: List<CapsuleUiState.Active.CapsuleItem>) {
        val validIds = newCapsules.map { it.notifId }.toSet()
        Log.d(TAG, "白名单 validIds: $validIds")

        val newAggregateMode = newCapsules.any { it.id == CapsuleStateManager.AGGREGATE_PICKUP_ID }

        if (newAggregateMode && !isAggregateMode) {
            isAggregateMode = true
            startMonitoring()
            Log.d(TAG, "启动聚合模式监控")
        } else if (!newAggregateMode && isAggregateMode) {
            isAggregateMode = false
            monitorJob?.cancel()
            Log.d(TAG, "停止聚合模式监控")
        }

        newCapsules.forEach { capsuleItem ->
            upsertCapsule(capsuleItem)
        }
        Log.d(TAG, "新胶囊已创建到内存")

        if (newAggregateMode) {
            serviceScope.launch {
                delay(50)
                Log.d(TAG, "聚合模式：延迟清除独立胶囊")
                cleanupStaleCapsules(validIds)
            }
        } else {
            cleanupInvalidNotifications(validIds)
        }

        refreshForegroundState(validIds)
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (true) {
                delay(3000)  // 每3秒检查一次，减少资源消耗
                if (isAggregateMode) {
                    cleanupStaleCapsules(activeCapsules.keys.toSet())
                }
            }
        }
    }

    private fun cleanupStaleCapsules(validIds: Set<Int>) {
        try {
            // 先清理内存
            val toRemove = activeCapsules.keys.filter { it !in validIds }
            toRemove.forEach { notifId ->
                activeCapsules.remove(notifId)
                Log.d(TAG, "从内存移除: $notifId")
            }

            // 清理系统通知
            val activeNotifications = notificationManager.activeNotifications
            activeNotifications.forEach { sb ->
                val notificationId = sb.id
                val channelId = sb.notification.channelId
                val groupName = sb.notification.group

                val channelMatch = channelId != null && channelId.contains("live", ignoreCase = true)
                val groupMatch = "LIVE_CAPSULE_GROUP" == groupName

                if (channelMatch && groupMatch && notificationId !in validIds) {
                    Log.w(TAG, "🗑️ 清除过期胶囊: id=$notificationId")
                    notificationManager.cancel(notificationId)
                }
            }

            // ✅ 关键修复：清除后刷新前台通知，确保剩余胶囊能正确显示
            if (activeCapsules.isNotEmpty()) {
                refreshForegroundForRemainingCapsules(validIds)
            }
        } catch (e: Exception) {
            Log.e(TAG, "清除过期胶囊失败", e)
        }
    }

    private fun cleanupInvalidNotifications(validIds: Set<Int>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        if (isAggregateMode) return

        try {
            val activeNotifications = notificationManager.activeNotifications
            activeNotifications.forEach { sb ->
                val notificationId = sb.id
                val channelId = sb.notification.channelId
                val groupName = sb.notification.group

                val channelMatch = channelId != null && channelId.contains("live", ignoreCase = true)
                val groupMatch = "LIVE_CAPSULE_GROUP" == groupName

                if (channelMatch && groupMatch && notificationId !in validIds) {
                    Log.w(TAG, "🗑️ 清除无效胶囊: id=$notificationId")
                    notificationManager.cancel(notificationId)
                }
            }

            // ✅ 修复：清理无效胶囊后刷新前台通知
            refreshForegroundForRemainingCapsules(validIds)
        } catch (e: Exception) {
            Log.e(TAG, "清理无效通知时出错", e)
        }
    }

    private fun upsertCapsule(item: CapsuleUiState.Active.CapsuleItem) {
        val notification = provider.buildNotification(
            this,
            item.id,
            item.title,
            item.content,
            item.color,
            item.type,
            item.eventType
        )

        val metadata = CapsuleMetadata(
            notificationId = item.notifId,
            originalId = item.id,
            notification = notification,
            type = item.type,
            startTime = item.startMillis,
            endTime = item.endMillis
        )

        activeCapsules[item.notifId] = metadata

        if (currentForegroundId == -1 || currentForegroundId == PLACEHOLDER_FOREGROUND_ID) {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(item.notifId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(item.notifId, notification)
            }
            currentForegroundId = item.notifId
        } else {
            notificationManager.notify(item.notifId, notification)
        }

        Log.d(TAG, "胶囊已更新: ${item.title}")
    }

    private fun refreshForegroundState(validIds: Set<Int>) {
        if (activeCapsules.isEmpty()) {
            stopServiceSafely()
            return
        }

        val now = System.currentTimeMillis()

        val candidates = activeCapsules.values.filter { capsule ->
            if (capsule.notificationId !in validIds) return@filter false

            val isPickup = capsule.type == TYPE_PICKUP || capsule.type == TYPE_PICKUP_EXPIRED
            if (isPickup) {
                now < capsule.endTime + (5 * 60 * 1000)
            } else {
                now < capsule.endTime
            }
        }

        if (candidates.isEmpty()) {
            Log.d(TAG, "所有胶囊已过期，停止服务")
            stopServiceSafely()
            return
        }

        candidates.forEach { capsule ->
            Log.d(TAG, ">>> refreshForegroundState notify: ${capsule.originalId}")
            notificationManager.notify(capsule.notificationId, capsule.notification)
        }
    }

    private fun stopServiceSafely() {
        cleanupAllCapsuleNotifications()
        if (activeCapsules.isNotEmpty()) {
            activeCapsules.clear()
        }
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
            currentForegroundId = -1
            stopSelf()
            Log.d(TAG, "前台服务已停止")
        } catch (e: Exception) {
            Log.e(TAG, "停止服务时出错", e)
        }
    }

    private fun cleanupAllCapsuleNotifications() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val activeNotifications = notificationManager.activeNotifications
                activeNotifications.forEach { sb ->
                    if (sb.notification.channelId == App.CHANNEL_ID_LIVE) {
                        notificationManager.cancel(sb.id)
                        Log.d(TAG, "清理胶囊通知: id=${sb.id}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理所有胶囊通知时出错", e)
        }
    }

    /**
     * 刷新前台通知，确保剩余胶囊中有一个被设置为前台
     * 解决多胶囊场景下，删除一个胶囊后其他胶囊不显示的问题
     */
    private fun refreshForegroundForRemainingCapsules(validIds: Set<Int>) {
        if (activeCapsules.isEmpty()) {
            Log.d(TAG, "无剩余胶囊，停止服务")
            stopServiceSafely()
            return
        }

        // 优先使用聚合胶囊作为前台
        val aggregateCapsule = activeCapsules.values.find { 
            it.originalId == CapsuleStateManager.AGGREGATE_PICKUP_ID && it.notificationId in validIds 
        }

        val foregroundCapsule = aggregateCapsule ?: activeCapsules.values.firstOrNull { 
            it.notificationId in validIds 
        }

        if (foregroundCapsule != null && foregroundCapsule.notificationId != currentForegroundId) {
            Log.d(TAG, "刷新前台通知: ${foregroundCapsule.originalId}, id=${foregroundCapsule.notificationId}")
            currentForegroundId = foregroundCapsule.notificationId
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(foregroundCapsule.notificationId, foregroundCapsule.notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(foregroundCapsule.notificationId, foregroundCapsule.notification)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        monitorJob?.cancel()
        stateCollectJob?.cancel()
        serviceScope.cancel()
        activeCapsules.clear()
        Log.d(TAG, "CapsuleService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
