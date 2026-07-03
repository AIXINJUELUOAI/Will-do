package com.antgskds.calendarassistant.service.capsule

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.core.util.FlymeUtils
import com.antgskds.calendarassistant.core.util.OsUtils
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.state.CapsuleType
import com.antgskds.calendarassistant.data.state.CapsuleUiState
import com.antgskds.calendarassistant.service.capsule.miui.MiuiIslandManager
import com.antgskds.calendarassistant.service.capsule.provider.FlymeCapsuleProvider
import com.antgskds.calendarassistant.service.capsule.provider.ICapsuleProvider
import com.antgskds.calendarassistant.service.capsule.provider.NativeCapsuleProvider
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationIds
import com.antgskds.calendarassistant.platform.xposed.XposedModuleStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

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
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val provider: ICapsuleProvider =
        if (FlymeUtils.isFlyme()) FlymeCapsuleProvider() else NativeCapsuleProvider()
    private val activeNotifIds = ConcurrentHashMap.newKeySet<Int>()
    private var monitorJob: Job? = null
    private var isAggregateMode = false

    /** 胶囊状态变化时的总分发入口：决定厂商通道并发布/取消。 */
    fun dispatch(state: CapsuleUiState) {
        val settings = settingsQueryApi.settings.value
        val useMiuiIsland = isMiuiIslandMode(settings)
        when (state) {
            is CapsuleUiState.Active -> {
                if (useMiuiIsland) {
                    MiuiIslandManager.update(context, state.capsules)
                    cancelAllCapsuleNotifications()
                } else {
                    updateCapsules(state.capsules)
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
                        val stillValid = state.capsules.any { it.notifId == notificationId }
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
