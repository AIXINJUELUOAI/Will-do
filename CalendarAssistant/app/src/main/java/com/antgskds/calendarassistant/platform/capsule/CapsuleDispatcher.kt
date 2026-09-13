package com.antgskds.calendarassistant.platform.capsule

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import com.antgskds.calendarassistant.shared.query.SettingsQueryApi
import com.antgskds.calendarassistant.shared.util.FlymeUtils
import com.antgskds.calendarassistant.shared.util.OsUtils
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.capsule.domain.model.CapsuleType
import com.antgskds.calendarassistant.feature.capsule.domain.model.CapsuleUiState
import com.antgskds.calendarassistant.platform.capsule.miui.MiuiIslandManager
import com.antgskds.calendarassistant.platform.capsule.provider.FlymeCapsuleProvider
import com.antgskds.calendarassistant.platform.capsule.provider.ICapsuleProvider
import com.antgskds.calendarassistant.platform.capsule.provider.NativeCapsuleProvider
import com.antgskds.calendarassistant.platform.capsule.render.IconUtils
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationIds
import com.antgskds.calendarassistant.platform.xposed.XposedModuleStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import com.antgskds.calendarassistant.feature.notification.api.ports.PlatformPublisher
import com.antgskds.calendarassistant.feature.notification.model.*
import com.antgskds.calendarassistant.feature.capsule.domain.CapsuleActionSpec
import com.antgskds.calendarassistant.feature.capsule.domain.QuickMemoCapsuleDurationPolicy
import com.antgskds.calendarassistant.feature.capsule.presentation.CapsuleMessageComposer
import com.antgskds.calendarassistant.platform.receiver.EventActionReceiver

/**
 * 胶囊发布站 —— 胶囊「一条线」里的发布分支。
 *
 * 职责（从 CapsuleStateManager 搬出，行为不变）：接收算好的 [CapsuleUiState]，
 * 决定走哪条 vendor 通道（小米超级岛 Xposed transport / 原生 / 魅族），并执行真正的
 * notify/cancel、stale 清理、聚合监控。CapsuleStateManager 从此只算状态、不发布。
 *
 * 小米超级岛走 Xposed/SystemUI 跨进程，是底层 transport 例外，此处仅作为入口分流，
 * 不把它塞进 app 进程内的 publisher 模型。
 *
 * @param uiStateProvider 读取当前 uiState（stale 清理需要核对实时状态）。
 */
class CapsuleDispatcher(
    private val context: Context,
    private val appScope: CoroutineScope,
    private val settingsQueryApi: SettingsQueryApi,
    private val uiStateProvider: () -> CapsuleUiState
) : PlatformPublisher {
    private val reminderCapsules = linkedMapOf<String, CapsuleUiState.Active.CapsuleItem>()
    private val reminderExpiryJobs = mutableMapOf<String, Job>()
    private var baseState: CapsuleUiState = CapsuleUiState.None

    override suspend fun publish(payload: PlatformNotificationPayload): NotificationResult = synchronized(this) {
        if (!settingsQueryApi.settings.value.isLiveCapsuleEnabled) {
            return@synchronized NotificationResult.Failure(payload.key, NotificationFailureReason.PUBLISH_FAILED, "胶囊开关已关闭，等待重试重新选择路由")
        }
        if (!androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return@synchronized NotificationResult.Failure(payload.key, NotificationFailureReason.PERMISSION_DENIED, "通知权限未开启")
        }
        if (!isMiuiIslandMode(settingsQueryApi.settings.value) &&
            notificationManager.getNotificationChannel(com.antgskds.calendarassistant.App.CHANNEL_ID_LIVE)?.importance == NotificationManager.IMPORTANCE_NONE) {
            return@synchronized NotificationResult.Failure(payload.key, NotificationFailureReason.PERMISSION_DENIED, "实况通知渠道已关闭")
        }
        val now = System.currentTimeMillis()
        val settings = settingsQueryApi.settings.value
        val duration = QuickMemoCapsuleDurationPolicy.durationMillis(settings.defaultEventDurationMinutes)
        val memoId = payload.tapTarget?.payload?.get("quickMemoId")?.toLongOrNull()
            ?: return@synchronized NotificationResult.Failure(payload.key, NotificationFailureReason.VALIDATION_FAILED, "缺少随口记 ID")
        val reminderId = payload.key.value.removePrefix("quick-memo:reminder:").toLongOrNull()
            ?: return@synchronized NotificationResult.Failure(payload.key, NotificationFailureReason.VALIDATION_FAILED, "缺少提醒 ID")
        val display = CapsuleMessageComposer.composeTextQuickMemo(
            title = payload.display.secondaryText.orEmpty().ifBlank { "随口记提醒" },
            memoId = memoId,
            fixedTitleEnabled = settings.quickMemoPinnedFixedTitleEnabled,
            removeAction = CapsuleActionSpec(
                label = "移除",
                receiverAction = EventActionReceiver.ACTION_CLEAR_QUICK_MEMO_REMINDER,
                extraLongKey = EventActionReceiver.EXTRA_QUICK_MEMO_REMINDER_ID,
                extraLongValue = reminderId,
            ),
        )
        val item = CapsuleUiState.Active.CapsuleItem(
            id = payload.key.value,
            notifId = payload.notificationId,
            type = CapsuleType.QUICK_MEMO_REMINDER,
            eventType = "quick_memo_reminder",
            title = display.primaryText,
            content = payload.display.secondaryText.orEmpty(),
            description = payload.display.expandedText.orEmpty(),
            color = android.graphics.Color.parseColor("#7C4DFF"),
            startMillis = now,
            endMillis = now + duration,
            display = display,
        )
        try {
            reminderCapsules[payload.key.value] = item
            dispatch(baseState)
            check(reminderCapsules[payload.key.value] === item) { "胶囊开关在发布期间变更" }
            reminderExpiryJobs.remove(payload.key.value)?.cancel()
            reminderExpiryJobs[payload.key.value] = appScope.launch {
                kotlinx.coroutines.delay(duration)
                synchronized(this@CapsuleDispatcher) {
                    reminderCapsules.remove(payload.key.value)
                    reminderExpiryJobs.remove(payload.key.value)
                    runCatching { dispatch(baseState) }
                        .onFailure { Log.e(TAG, "expire reminder capsule failed key=${payload.key.value}", it) }
                }
            }
            Log.i(TAG, "published reminder capsule key=${payload.key.value} id=${payload.notificationId}")
            NotificationResult.Success(payload.key, NotificationState.POSTED)
        } catch (error: Exception) {
            reminderCapsules.remove(payload.key.value)
            Log.e(TAG, "publish reminder capsule failed key=${payload.key.value}", error)
            NotificationResult.Failure(payload.key, NotificationFailureReason.PUBLISH_FAILED, error.message, error)
        }
    }

    override suspend fun cancel(key: NotificationKey): NotificationResult = synchronized(this) {
        reminderCapsules.remove(key.value)
        reminderExpiryJobs.remove(key.value)?.cancel()
        dispatch(baseState)
        NotificationResult.Success(key, NotificationState.CANCELLED)
    }
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val provider: ICapsuleProvider =
        if (FlymeUtils.isFlyme()) FlymeCapsuleProvider() else NativeCapsuleProvider()
    private val activeNotifIds = ConcurrentHashMap.newKeySet<Int>()
    private var monitorJob: Job? = null
    private var isAggregateMode = false

    /** 胶囊状态变化时的总分发入口：决定厂商通道并发布/取消。 */
    @Synchronized
    fun dispatch(state: CapsuleUiState) {
        baseState = state
        val settings = settingsQueryApi.settings.value
        if (!settings.isLiveCapsuleEnabled) {
            reminderCapsules.clear()
            reminderExpiryJobs.values.forEach { it.cancel() }
            reminderExpiryJobs.clear()
        }
        val combined = (state as? CapsuleUiState.Active)?.capsules.orEmpty() + reminderCapsules.values
        val effectiveState = if (combined.isEmpty()) CapsuleUiState.None else CapsuleUiState.Active(combined)
        val useMiuiIsland = isMiuiIslandMode(settings)
        when (effectiveState) {
            is CapsuleUiState.Active -> {
                if (useMiuiIsland) {
                    MiuiIslandManager.update(context, effectiveState.capsules)
                    cancelAllCapsuleNotifications()
                } else {
                    updateCapsules(effectiveState.capsules)
                }
            }
            is CapsuleUiState.None -> {
                monitorJob?.cancel()
                isAggregateMode = false
                MiuiIslandManager.clear(context)
                cancelAllCapsuleNotifications()
            }
        }
    }

    private fun isMiuiIslandMode(settings: MySettings): Boolean {
        return settings.isLiveCapsuleEnabled && OsUtils.isHyperOS() && XposedModuleStatus.isActive()
    }

    private fun updateCapsules(newCapsules: List<CapsuleUiState.Active.CapsuleItem>) {
        val validIds = newCapsules.map { it.notifId }.toSet()

        val newAggregateMode = newCapsules.any { it.id == AGGREGATE_PICKUP_ID }
        if (newAggregateMode && !isAggregateMode) {
            isAggregateMode = true
            startMonitoring()
        } else if (!newAggregateMode && isAggregateMode) {
            isAggregateMode = false
            monitorJob?.cancel()
        }

        newCapsules.forEach { item ->
            val iconResId = IconUtils.getSmallIconForCapsule(context, item)
            val notification = provider.buildNotification(context, item, iconResId)
            notificationManager.notify(item.notifId, notification)
            activeNotifIds.add(item.notifId)
            cancelLegacyCapsuleNotification(item)
        }

        // 清理不再需要的通知
        val staleIds = activeNotifIds.toMutableSet()
        staleIds.removeAll(validIds)
        staleIds.forEach { id ->
            notificationManager.cancel(id)
            activeNotifIds.remove(id)
        }
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = appScope.launch {
            while (true) {
                kotlinx.coroutines.delay(3000)
                if (isAggregateMode) {
                    cleanupStaleNotifications()
                }
            }
        }
    }

    private fun cleanupStaleNotifications() {
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
            val activeNotifications = notificationManager.activeNotifications
            activeNotifications.forEach { sb ->
                val notificationId = sb.id
                if (notificationId !in activeNotifIds) return@forEach
                val channelId = sb.notification.channelId
                val channelMatch = channelId != null && channelId.contains("live", ignoreCase = true)
                if (channelMatch) {
                    val state = uiStateProvider()
                    if (state is CapsuleUiState.Active) {
                        val stillValid = state.capsules.any { it.notifId == notificationId } ||
                            synchronized(this) { reminderCapsules.values.any { it.notifId == notificationId } }
                        if (!stillValid) {
                            notificationManager.cancel(notificationId)
                            activeNotifIds.remove(notificationId)
                            Log.d(TAG, "清除过期胶囊通知: id=$notificationId")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理过期通知失败", e)
        }
    }

    private fun cancelAllCapsuleNotifications() {
        activeNotifIds.forEach { id ->
            notificationManager.cancel(id)
        }
        activeNotifIds.clear()
    }

    private fun cancelLegacyCapsuleNotification(item: CapsuleUiState.Active.CapsuleItem) {
        if (item.type != CapsuleType.SCHEDULE &&
            item.type != CapsuleType.PICKUP &&
            item.type != CapsuleType.PICKUP_EXPIRED
        ) return
        NotificationIds.legacyKeyIds(item.id)
            .filter { it != item.notifId }
            .forEach(notificationManager::cancel)
    }

    companion object {
        private const val TAG = "CapsuleDispatcher"
        private const val AGGREGATE_PICKUP_ID = "AGGREGATE_PICKUP"
    }
}
