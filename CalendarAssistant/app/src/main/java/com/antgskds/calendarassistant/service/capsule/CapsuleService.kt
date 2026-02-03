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
import kotlinx.coroutines.launch

/**
 * 实况胶囊前台服务 (Dumb Service 版本 - 白名单机制)
 *
 * 职责：
 * 1. 监听 capsuleStateManager.uiState
 * 2. 收到 None → 停止服务
 * 3. 收到 Active → 显示胶囊通知
 * 4. ✅ 白名单机制：只保留 Repository 下发的合法通知，其他一律清除
 *
 * 核心思想：
 * 不管是单体、聚合还是其他类型的胶囊，只要不在当前白名单里，就必须立即从状态栏消失。
 *
 * 所有业务逻辑已移至 CapsuleStateManager
 */
class CapsuleService : Service() {

    companion object {
        const val TAG = "CapsuleService"
        const val TYPE_SCHEDULE = 1
        const val TYPE_PICKUP = 2
        // ✅ 新增：明确的过期取件码类型
        const val TYPE_PICKUP_EXPIRED = 3
        // ✅ 新增：网速胶囊类型
        const val TYPE_NETWORK_SPEED = 4

        // 占位通知的标记 ID（不实际使用，用于标记占位状态）
        private const val PLACEHOLDER_FOREGROUND_ID = -1

        @Volatile
        var isServiceRunning = false
            private set
    }

    // 胶囊元数据（保留用于排序和前台通知管理）
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

    // 监听胶囊状态的 Job
    private var stateCollectJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        isServiceRunning = true
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        provider = if (FlymeUtils.isFlyme()) FlymeCapsuleProvider() else NativeCapsuleProvider()

        // 开始监听胶囊状态
        startObservingCapsuleState()
        Log.d(TAG, "CapsuleService created, 开始监听胶囊状态")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ✅ 关键修复：立即启动前台服务（使用占位通知），避免启动超时
        // 使用 PLACEHOLDER_FOREGROUND_ID 标记占位状态
        if (currentForegroundId == -1) {
            Log.d(TAG, "启动前台服务（占位通知）")
            val placeholderNotification = createPlaceholderNotification()
            startForeground(1, placeholderNotification)
            currentForegroundId = PLACEHOLDER_FOREGROUND_ID  // 标记为占位状态
        }
        return START_NOT_STICKY
    }

    /**
     * 创建占位通知（不显示在状态栏）
     */
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
            .setVisibility(Notification.VISIBILITY_SECRET)  // 隐藏通知
            .build()
    }

    /**
     * 核心逻辑：监听 CapsuleStateManager 的状态变化
     */
    private fun startObservingCapsuleState() {
        stateCollectJob = serviceScope.launch {
            val repository = (applicationContext as App).repository
            repository.capsuleStateManager.uiState.collect { capsuleState ->
                Log.d(TAG, "收到胶囊状态变化: $capsuleState")
                handleCapsuleStateChange(capsuleState)
            }
        }
    }

    /**
     * 处理胶囊状态变化
     */
    private fun handleCapsuleStateChange(state: CapsuleUiState) {
        when (state) {
            is CapsuleUiState.None -> {
                // 无胶囊显示 → 停止服务
                Log.d(TAG, "无胶囊，停止服务")
                stopServiceSafely()
            }
            is CapsuleUiState.Active -> {
                // 有胶囊显示 → 更新通知
                Log.d(TAG, "活跃胶囊数量: ${state.capsules.size}")
                updateCapsules(state.capsules)
            }
        }
    }

    /**
     * ✅ 更新胶囊通知（白名单机制）
     *
     * 核心思想：只有 Repository 下发的胶囊才是合法的，不在白名单的一律清除。
     * 不管是单体、聚合还是其他类型的胶囊，只要不在当前合法名单里，就必须立即消失。
     *
     * @param newCapsules 新的胶囊列表
     */
    private fun updateCapsules(newCapsules: List<CapsuleUiState.Active.CapsuleItem>) {
        // 1. 定义白名单：当前应该显示的所有 Notification ID
        val validIds = newCapsules.map { it.notifId }.toSet()
        Log.d(TAG, "白名单 validIds: $validIds")

        // 2. 执行清洗：清除不在白名单中的通知
        cleanupInvalidNotifications(validIds)

        // 3. 更新内存：移除不在白名单中的数据
        val toRemoveFromMemory = activeCapsules.keys.filter { it !in validIds }
        toRemoveFromMemory.forEach { notifId ->
            activeCapsules.remove(notifId)
            Log.d(TAG, "从内存移除: $notifId")
        }

        // 4. 更新显示：添加或更新新胶囊
        newCapsules.forEach { capsuleItem ->
            upsertCapsule(capsuleItem)
        }

        // 5. 刷新前台状态
        Log.d(TAG, ">>> updateCapsules 完成，调用 refreshForegroundState")
        refreshForegroundState()
    }

    /**
     * ✅ 强力清洗非法通知 (修正版 - 跳过系统聚合摘要)
     *
     * 关键修正：
     * - 只清理我们自己创建的通知（Channel 匹配 AND Group 匹配）
     * - 跳过系统自动生成的聚合摘要（id=0, group=Aggregate_AlertingSection）
     * - 避免"删除 -> 系统重新生成"的无限循环
     */
    private fun cleanupInvalidNotifications(validIds: Set<Int>) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return

        val LIVE_CAPSULE_GROUP = "LIVE_CAPSULE_GROUP"
        val SYSTEM_AGGREGATE_GROUP = "Aggregate_AlertingSection"

        try {
            val activeNotifications = notificationManager.activeNotifications
            Log.d(TAG, "========== 通知清理开始 ==========")
            Log.d(TAG, "系统通知总数: ${activeNotifications.size}, 白名单 validIds: $validIds")

            var matchedCount = 0
            var cleanedCount = 0

            for (sbNotification in activeNotifications) {
                val notification = sbNotification.notification
                val notificationId = sbNotification.id

                // 1. 获取特征
                val channelId = notification.channelId
                val groupName = notification.group

                // 2. 跳过系统自动生成的聚合摘要（id=0 且 group 包含 Aggregate_AlertingSection）
                val isSystemAggregate = notificationId == 0 &&
                        (groupName?.contains(SYSTEM_AGGREGATE_GROUP) == true)
                if (isSystemAggregate) {
                    Log.d(TAG, "跳过系统聚合摘要: id=$notificationId, group=[$groupName]")
                    continue
                }

                // 3. 精确匹配：必须同时满足 Channel 和 Group 条件
                val channelMatch = channelId != null && channelId.contains("live", ignoreCase = true)
                val groupMatch = LIVE_CAPSULE_GROUP == groupName
                val isOurCapsule = channelMatch && groupMatch

                // 4. 输出每个通知的详细信息
                Log.d(TAG, "通知: id=$notificationId, channelId=[$channelId], group=[$groupName], " +
                        "channelMatch=$channelMatch, groupMatch=$groupMatch, isOurCapsule=$isOurCapsule")

                if (isOurCapsule) {
                    matchedCount++
                    // 5. 白名单审判：只清理我们自己创建的、不在白名单中的通知
                    // ✅ 关键修改：如果 validIds 为空（只有网速胶囊时），清理所有胶囊
                    val shouldClean = validIds.isEmpty() || notificationId !in validIds
                    if (shouldClean) {
                        notificationManager.cancel(notificationId)
                        cleanedCount++
                        Log.w(TAG, "🗑️ 清除无效胶囊: id=$notificationId, channelId=[$channelId], group=[$groupName]")
                    } else {
                        Log.d(TAG, "✓ 保留合法胶囊: id=$notificationId")
                    }
                }
            }

            Log.d(TAG, "匹配到 $matchedCount 个胶囊通知，清除 $cleanedCount 个无效通知")
            Log.d(TAG, "========== 通知清理结束 ==========")
        } catch (e: Exception) {
            Log.e(TAG, "清理无效通知时出错", e)
        }
    }

    /**
     * 添加或更新单个胶囊
     */
    private fun upsertCapsule(item: CapsuleUiState.Active.CapsuleItem) {
        // ✅ 详细日志：收到的胶囊信息
        Log.d(TAG, ">>> upsertCapsule 被调用: title=${item.title}, type=${item.type}, id=${item.id}, notifId=${item.notifId}")

        // ✅ 关键：如果是占位通知（id="placeholder"），立即取消它
        if (item.id == "placeholder") {
            notificationManager.cancel(item.notifId)
            Log.d(TAG, ">>> 取消占位通知")
            return
        }

        val notification = provider.buildNotification(
            this,
            item.id,
            item.title,
            item.content,
            item.color,
            item.type,  // 传入 CapsuleItem 的 type 字段 (1=日程, 2=取件码)
            item.eventType,  // 新增: 传入 eventType 字段
            item.startMillis,  // 传入实际开始时间，用于计算"还有x分钟开始"
            item.endMillis  // 传入实际结束时间，用于判断取件码是否过期
        )

        // ✅ 暴力刷新策略：如果是过期胶囊，强制"先删-后建"
        if (item.type == TYPE_PICKUP_EXPIRED) {
            Log.d(TAG, ">>> 检测到 TYPE_PICKUP_EXPIRED，执行暴力刷新")
            // 如果这是一个更新操作（内存里已有该ID），先移除旧的
            if (activeCapsules.containsKey(item.notifId)) {
                Log.d(TAG, "检测到过期胶囊更新，执行暴力刷新: ${item.title}")

                // 显式取消旧通知
                notificationManager.cancel(item.notifId)

                // 立即重建（配合 setOnlyAlertOnce(false) 实现弹窗）
                // 这里不需要延迟，cancel + notify 同步执行就足以欺骗系统

                // 更新元数据
                val metadata = CapsuleMetadata(
                    notificationId = item.notifId,
                    originalId = item.id,
                    notification = notification,
                    type = item.type,
                    startTime = item.startMillis,
                    endTime = item.endMillis
                )
                activeCapsules[item.notifId] = metadata

                // ✅ 关键修复：如果是前台通知，需要用 startForeground 更新
                // 否则通知不会显示
                Log.d(TAG, ">>> currentForegroundId=$currentForegroundId, item.notifId=${item.notifId}")
                if (item.notifId == currentForegroundId) {
                    Log.d(TAG, ">>> 调用 promoteToForeground 更新前台通知")
                    promoteToForeground(item.notifId, notification)
                } else {
                    Log.d(TAG, ">>> 调用 notify 更新普通通知")
                    notificationManager.notify(item.notifId, notification)
                }

                Log.d(TAG, "过期胶囊暴力刷新完成: ${item.title}")
                return
            } else {
                Log.d(TAG, ">>> activeCapsules 中不存在该 notifId，首次创建过期胶囊")
            }
        }

        // === 普通逻辑 (非过期胶囊，或首次创建) ===
        val metadata = CapsuleMetadata(
            notificationId = item.notifId,
            originalId = item.id,
            notification = notification,
            type = item.type,
            startTime = item.startMillis,
            endTime = item.endMillis
        )

        activeCapsules[item.notifId] = metadata

        // ✅ 关键：如果是占位状态或第一个胶囊，调用 startForeground 正式启动
        // 否则调用 notify
        if (currentForegroundId == -1 || currentForegroundId == PLACEHOLDER_FOREGROUND_ID) {
            Log.d(TAG, ">>> 首次胶囊/占位替换，调用 startForeground: ${item.title}")
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(item.notifId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(item.notifId, notification)
            }
            currentForegroundId = item.notifId
        } else if (item.notifId != currentForegroundId) {
            notificationManager.notify(item.notifId, notification)
        } else {
            Log.d(TAG, ">>> 跳过 notify：notifId == currentForegroundId")
        }

        Log.d(TAG, "胶囊已更新: ${item.title} (${item.id})")
    }

    /**
     * 删除单个胶囊
     */
    private fun removeCapsule(notifId: Int) {
        activeCapsules.remove(notifId)?.let {
            notificationManager.cancel(notifId)
            Log.d(TAG, "胶囊已删除: $notifId")
        }
    }

    /**
     * 刷新前台通知状态
     * 选择优先级最高的胶囊作为前台通知
     */
    private fun refreshForegroundState() {
        if (activeCapsules.isEmpty()) {
            // 所有胶囊都删除了 → 停止服务
            stopServiceSafely()
            return
        }

        val now = System.currentTimeMillis()
        // ✅ 筛选未过期的胶囊（取件码有 30 分钟宽限期）
        val candidates = activeCapsules.values.filter { capsule ->
            val isPickup = capsule.type == TYPE_PICKUP || capsule.type == TYPE_PICKUP_EXPIRED
            if (isPickup) {
                // 取件码：允许 30 分钟宽限期
                val graceEnd = capsule.endTime + (30 * 60 * 1000)  // endTime + 30分钟
                now < graceEnd
            } else {
                // 普通日程：严格按 endTime
                now < capsule.endTime
            }
        }

        if (candidates.isEmpty()) {
            // 所有胶囊都过期了 → 停止服务
            Log.d(TAG, "所有胶囊已过期，停止服务")
            stopServiceSafely()
            return
        }

        // ✅ 核心修复：只选择winner，不调用 startForeground
        // 调用 startForeground 会导致系统重新评估整个通知组，其他胶囊可能被隐藏
        val winner = candidates.sortedWith(
            compareByDescending<CapsuleMetadata> { it.startTime }
                .thenByDescending { it.type }
        ).first()

        // ✅ 关键：只用 notify 更新所有胶囊，包括 winner
        // 不要调用 startForeground，让 Android 保持当前的前台服务状态
        candidates.forEach { capsule ->
            Log.d(TAG, ">>> refreshForegroundState notify: ${capsule.originalId}")
            notificationManager.notify(capsule.notificationId, capsule.notification)
        }
    }

    /**
     * 将胶囊晋升为前台通知（无缝切换策略）
     *
     * 修复策略：
     * - 直接调用 startForeground(newId, notification) 抢占前台服务焦点
     * - 成功后 cancel(oldId) 取消旧前台通知
     * - 绝不调用 stopForeground() 或 sleep()，避免 Service 降级被系统查杀
     */
    private fun promoteToForeground(id: Int, notification: Notification) {
        try {
            if (currentForegroundId == -1) {
                // 首次启动前台服务
                Log.d(TAG, "首次启动前台服务: id=$id")
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(id, notification)
                }
                currentForegroundId = id
            } else if (currentForegroundId != id) {
                // 无缝切换前台通知
                Log.d(TAG, "切换前台通知: $currentForegroundId -> $id")

                // ✅ 关键修复：直接调用 startForeground 替换前台通知，不要 cancel 旧的
                // cancel 同一个 Group 的前台通知会导致系统重新评估 Group，所有胶囊可能被隐藏
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(id, notification)
                }

                // 更新前台通知 ID（不要 cancel，让系统自动处理）
                currentForegroundId = id
                Log.d(TAG, "前台通知已切换: $id")
            } else {
                // ✅ 关键修复：更新当前前台通知的内容
                // 当 currentForegroundId == id 时，需要重新调用 startForeground 更新前台通知
                Log.d(TAG, "更新当前前台通知内容: id=$id")
                if (Build.VERSION.SDK_INT >= 34) {
                    startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
                } else {
                    startForeground(id, notification)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "promoteToForeground failed", e)
        }
    }

    /**
     * ✅ 安全停止服务（带完整通知清理）
     * 必须先调用 stopForeground，再清理所有实况胶囊通知
     */
    private fun stopServiceSafely() {
        // ✅ 清理所有实况胶囊 Channel 的通知（不留残渣）
        cleanupAllCapsuleNotifications()

        if (activeCapsules.isNotEmpty()) {
            activeCapsules.clear()
        }

        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
            currentForegroundId = -1  // 重置前台 ID
            stopSelf()  // 真正停止服务，防止进程残留
            Log.d(TAG, "前台服务已停止，所有胶囊通知已清理")
        } catch (e: Exception) {
            Log.e(TAG, "停止服务时出错", e)
        }
    }

    /**
     * ✅ 清理所有实况胶囊通知
     * 遍历系统的 activeNotifications，取消所有属于 CAPSULE_CHANNEL_ID 的通知
     */
    private fun cleanupAllCapsuleNotifications() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val activeNotifications = notificationManager.activeNotifications
                var cleanedCount = 0

                // 清理占位通知（ID=1）
                if (currentForegroundId == PLACEHOLDER_FOREGROUND_ID) {
                    notificationManager.cancel(1)
                    cleanedCount++
                    Log.d(TAG, "清理占位通知: id=1")
                }

                for (sbNotification in activeNotifications) {
                    if (sbNotification.notification.channelId == App.CHANNEL_ID_LIVE &&
                        sbNotification.id != 1) {  // 跳过占位通知
                        notificationManager.cancel(sbNotification.id)
                        cleanedCount++
                        Log.d(TAG, "清理胶囊通知: id=${sbNotification.id}")
                    }
                }

                Log.d(TAG, "共清理 $cleanedCount 个实况胶囊通知")
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理所有胶囊通知时出错", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isServiceRunning = false
        stateCollectJob?.cancel()
        serviceScope.cancel()
        activeCapsules.clear()
        Log.d(TAG, "CapsuleService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
