package com.antgskds.calendarassistant.core.center

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.core.content.EventTimelinePresenter
import com.antgskds.calendarassistant.core.ai.RecognitionFailureDisplay
import com.antgskds.calendarassistant.core.model.RecognitionDraft
import com.antgskds.calendarassistant.core.query.DailySummaryPayload
import com.antgskds.calendarassistant.core.rule.EventPresenter
import com.antgskds.calendarassistant.core.util.stripSourceImageMarkers
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.service.capsule.CapsuleActionSpec
import com.antgskds.calendarassistant.service.capsule.NotificationTemplateCenter
import com.antgskds.calendarassistant.data.state.CapsuleUiState
import com.antgskds.calendarassistant.service.capsule.IconUtils
import com.antgskds.calendarassistant.service.capsule.provider.FlymeCapsuleProvider
import com.antgskds.calendarassistant.service.capsule.provider.ICapsuleProvider
import com.antgskds.calendarassistant.service.capsule.provider.NativeCapsuleProvider
import com.antgskds.calendarassistant.platform.receiver.EventActionReceiver
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationIds
import com.antgskds.calendarassistant.core.util.FlymeUtils
import com.antgskds.calendarassistant.feature.api.notification.NotificationApi
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationFailureReason
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationKey
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationQuery
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationRequest
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationResult
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationSnapshot
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationState
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationTrigger
import com.antgskds.calendarassistant.feature.api.notification.ports.NotificationRegistryStore
import com.antgskds.calendarassistant.feature.api.notification.ports.SystemAlarmGateway
import com.antgskds.calendarassistant.feature.api.notification.ports.PlatformPublisher
import com.antgskds.calendarassistant.feature.api.notification.model.PlatformNotificationPayload
import com.antgskds.calendarassistant.feature.api.notification.model.NotificationRoute
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import com.antgskds.calendarassistant.shared.management.resource.notification.display.normal.NormalNotificationContent
import com.antgskds.calendarassistant.shared.management.resource.notification.display.normal.RecognitionNormalDisplay
import com.antgskds.calendarassistant.shared.management.resource.notification.display.normal.ScheduleNormalDisplay
import com.antgskds.calendarassistant.shared.management.resource.notification.display.normal.SystemNormalDisplay
import com.antgskds.calendarassistant.shared.management.resource.notification.display.normal.TransportNormalDisplay
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.RecognitionLiveDisplay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class NotificationCenter(
    private val appContext: Context,
    private val registryStore: NotificationRegistryStore,
    private val systemAlarmGateway: SystemAlarmGateway? = null,
    private val platformPublisher: PlatformPublisher? = null,
    private val liveCapsuleEnabledProvider: () -> Boolean = { false }
) : NotificationApi {
    companion object {
        private const val TAG = "NotificationCenter"
        private const val GROUP_CREATED_EVENTS = "calendar_assistant_created_events"
        private const val GROUP_QUICK_MEMO_SUGGESTIONS = "calendar_assistant_quick_memo_suggestions"
        private val RESULT_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm")
        private const val TYPE_RECOGNITION_RESULT = 9
        private const val EVENT_TYPE_RECOGNITION_RESULT = "recognition_result"
        private const val EVENT_TYPE_RECOGNITION_FAILED = "recognition_failed"
        private const val DEFAULT_RESULT_NOTIFICATION_TIMEOUT_MS = 8000L
        private const val DEFAULT_QUICK_MEMO_SUGGESTION_TIMEOUT_MS = 60_000L
        private const val DEFAULT_DAILY_SUMMARY_TIMEOUT_MS = 60_000L
        private const val RESULT_TEXT_MAX_CHARS = 56
        private val RECOGNITION_RESULT_COLOR = Color.rgb(76, 175, 80)
        private val RECOGNITION_FAILED_COLOR = Color.rgb(244, 67, 54)
        private val DAILY_SUMMARY_COLOR = Color.rgb(103, 80, 164)
        private const val TYPE_DAILY_SUMMARY = 10
        private const val EVENT_TYPE_DAILY_SUMMARY = "daily_summary"
    }

    // —— 通知时长（Policy Config）：从 settings 读，coerceIn 防坏值，缺省回退 DEFAULT_* ——
    private fun resultNotificationTimeoutMs(): Long =
        ((appContext as? App)?.settingsQueryApi?.settings?.value?.resultNotificationTimeoutMs?.toLong()
            ?: DEFAULT_RESULT_NOTIFICATION_TIMEOUT_MS).coerceIn(1000L, 120_000L)

    private fun quickMemoSuggestionTimeoutMs(): Long =
        ((appContext as? App)?.settingsQueryApi?.settings?.value?.quickMemoSuggestionTimeoutMs?.toLong()
            ?: DEFAULT_QUICK_MEMO_SUGGESTION_TIMEOUT_MS).coerceIn(1000L, 120_000L)

    private fun dailySummaryTimeoutMs(): Long =
        ((appContext as? App)?.settingsQueryApi?.settings?.value?.dailySummaryTimeoutMs?.toLong()
            ?: DEFAULT_DAILY_SUMMARY_TIMEOUT_MS).coerceIn(1000L, 120_000L)

    private val capsuleProvider: ICapsuleProvider by lazy {
        if (FlymeUtils.isFlyme()) FlymeCapsuleProvider() else NativeCapsuleProvider()
    }

    override suspend fun create(request: NotificationRequest): NotificationResult {
        return upsertApiRequest(request)
    }

    override suspend fun update(request: NotificationRequest): NotificationResult {
        return upsertApiRequest(request)
    }

    override suspend fun cancel(key: NotificationKey): NotificationResult {
        val previous = registryStore.get(key)
        if (previous != null) {
            registryStore.delete(key)
        }
        systemAlarmGateway?.cancel(key)
        previous?.notificationId?.let(::cancelNotification)
        return NotificationResult.Success(key, NotificationState.CANCELLED)
    }

    override suspend fun cancelAll(keys: Collection<NotificationKey>) {
        val distinctKeys = keys.distinctBy { it.value }
        if (distinctKeys.isEmpty()) return

        val removed = registryStore.deleteAll(distinctKeys)
        distinctKeys.forEach { key -> systemAlarmGateway?.cancel(key) }
        removed.mapNotNull { it.notificationId }.distinct().forEach(::cancelNotification)
    }

    override suspend fun get(key: NotificationKey): NotificationSnapshot? {
        return registryStore.get(key)
    }

    override suspend fun list(query: NotificationQuery): List<NotificationSnapshot> {
        return registryStore.list(query)
    }

    /**
     * Phase 2 修复：开机 / 升级后从持久化 Registry 重排所有「已排程」通知的系统闹钟。
     * 安卓重启会清空 AlarmManager；Registry（SharedPreferences）持久化了快照，这里据其 triggerAt
     * 重新 arm，使单次提醒在重启后仍按时触发（旧链路靠 BootReceiver 重建，迁移后由此承担）。
     */
    suspend fun rescheduleAllAlarms() {
        val scheduled = registryStore.list(NotificationQuery(state = NotificationState.SCHEDULED))
        Log.d("WillDoNotify", "boot reschedule: ${scheduled.size} scheduled snapshots")
        scheduled.forEach { snapshot -> syncSystemAlarm(snapshot) }
    }

    override suspend fun trigger(trigger: NotificationTrigger): NotificationResult {
        return when (trigger) {
            // Phase 2：ByKey / Due 现在真正发布——普通单次提醒已从旧链路切到新链路。
            // 发布前做胶囊门控与时效校验（见 publishOrSuppress）。
            is NotificationTrigger.ByKey -> publishOrSuppress(trigger.key)
            is NotificationTrigger.Due -> {
                val now = trigger.nowEpochMillis ?: System.currentTimeMillis()
                val due = list(NotificationQuery(dueAtOrBeforeEpochMillis = now, limit = 1)).firstOrNull()
                    ?: return NotificationResult.Failure(reason = NotificationFailureReason.NOT_FOUND)
                publishOrSuppress(due.key)
            }
            // Debug：开发者预览/强制触发（Phase 1 起即真实发布）。
            is NotificationTrigger.Debug -> publishForDebug(trigger.key)
        }
    }

    /**
     * Phase 2：到点触发时发布（或在胶囊开启时抑制）一条通知。
     * - 胶囊门控（fire-time，与旧 EventReminderReceiver 对齐）：isLiveCapsuleEnabled 时
     *   不发普通通知，置 READY 并取消闹钟（胶囊负责展示）。
     * - 防御性时效校验：事件已结束（metadata.endTS 已过）则取消，不发过期提醒。
     * - 否则走真实发布器（publishSnapshot 置 POSTED）。
     */
    private suspend fun publishOrSuppress(key: NotificationKey): NotificationResult {
        if (liveCapsuleEnabledProvider()) {
            Log.d("WillDoNotify", "fire key=${key.value} -> SUPPRESSED_CAPSULE")
            return markSnapshotReady(key, cancelAlarm = true)
        }
        val snapshot = registryStore.get(key)
        if (snapshot == null) {
            Log.d("WillDoNotify", "fire key=${key.value} -> NOT_FOUND")
            return NotificationResult.Failure(key, NotificationFailureReason.NOT_FOUND)
        }
        val endMillis = snapshot.metadata["endTS"]?.toLongOrNull()?.times(1000L)
        if (endMillis != null && endMillis <= System.currentTimeMillis()) {
            Log.d("WillDoNotify", "fire key=${key.value} -> EXPIRED endTS=$endMillis")
            registryStore.delete(key)
            systemAlarmGateway?.cancel(key)
            snapshot.notificationId?.let(::cancelNotification)
            return NotificationResult.Success(key, NotificationState.EXPIRED)
        }
        Log.d("WillDoNotify", "fire key=${key.value} -> PUBLISH offset=${snapshot.offsetMinutes}")
        val readied = snapshot.copy(
            state = NotificationState.READY,
            updatedAtEpochMillis = System.currentTimeMillis(),
            version = snapshot.version + 1L
        )
        registryStore.upsert(readied)
        return publishSnapshot(readied)
    }

    /**
     * Phase 1 开发者预览/强制触发：把快照置为 READY 后通过真实发布器发布。
     * 刻意复用「快照 → payload → publisher.publish」真实路径，不另起测试专用 builder，
     * 确保预览验证到的就是真实发布器。ByKey/Due 在本阶段不会走到这里。
     */
    private suspend fun publishForDebug(key: NotificationKey): NotificationResult {
        return publishReadySnapshot(key)
    }

    private suspend fun publishReadySnapshot(key: NotificationKey): NotificationResult {
        val previous = registryStore.get(key)
            ?: return NotificationResult.Failure(key, NotificationFailureReason.NOT_FOUND)
        val readied = previous.copy(
            state = NotificationState.READY,
            updatedAtEpochMillis = System.currentTimeMillis(),
            version = previous.version + 1L
        )
        registryStore.upsert(readied)
        return publishSnapshot(readied)
    }

    private suspend fun publishSnapshot(snapshot: NotificationSnapshot): NotificationResult {
        val publisher = platformPublisher ?: return NotificationResult.Failure(
            snapshot.key, NotificationFailureReason.PUBLISH_FAILED, "PlatformPublisher 未装配"
        )
        val payload = PlatformNotificationPayload(
            key = snapshot.key,
            notificationId = snapshot.notificationId ?: snapshot.key.value.hashCode(),
            smallIconResId = snapshot.smallIconResId,
            route = NotificationRoute.NORMAL,
            display = snapshot.display,
            behavior = snapshot.behavior,
            tapTarget = snapshot.tapTarget,
            actions = snapshot.actions,
            channelKey = snapshot.channelKey,
            category = snapshot.category
        )
        val result = publisher.publish(payload)
        if (result is NotificationResult.Success) {
            registryStore.upsert(
                snapshot.copy(
                    state = NotificationState.POSTED,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                    version = snapshot.version + 1L
                )
            )
        }
        return result
    }

    private suspend fun upsertApiRequest(request: NotificationRequest): NotificationResult {
        val state = resolveApiState(request)
        if (state == NotificationState.CANCELLED || state == NotificationState.EXPIRED) {
            registryStore.delete(request.key)
            systemAlarmGateway?.cancel(request.key)
            request.notificationId?.let(::cancelNotification)
            return NotificationResult.Success(request.key, state)
        }

        val previous = registryStore.get(request.key)
        val snapshot = request.toSnapshot(
            state = state,
            updatedAt = System.currentTimeMillis(),
            version = (previous?.version ?: 0L) + 1L
        )
        registryStore.upsert(snapshot)
        syncSystemAlarm(snapshot)
        return NotificationResult.Success(request.key, state)
    }

    private suspend fun markSnapshotReady(key: NotificationKey, cancelAlarm: Boolean): NotificationResult {
        val previous = registryStore.get(key)
            ?: return NotificationResult.Failure(key, NotificationFailureReason.NOT_FOUND)

        val snapshot = previous.copy(
            state = NotificationState.READY,
            updatedAtEpochMillis = System.currentTimeMillis(),
            version = previous.version + 1L
        )
        registryStore.upsert(snapshot)
        if (cancelAlarm) {
            systemAlarmGateway?.cancel(key)
        }
        return NotificationResult.Success(snapshot.key, snapshot.state)
    }

    private suspend fun syncSystemAlarm(snapshot: NotificationSnapshot) {
        val triggerAt = snapshot.behavior.triggerAtEpochMillis
        if (snapshot.state == NotificationState.SCHEDULED && triggerAt != null) {
            systemAlarmGateway?.schedule(snapshot.key, triggerAt, snapshot.behavior.allowWhileIdle)
        } else {
            systemAlarmGateway?.cancel(snapshot.key)
        }
    }

    private fun resolveApiState(request: NotificationRequest): NotificationState {
        val now = System.currentTimeMillis()
        val endMillis = request.metadata["endTS"]?.toLongOrNull()?.times(1000L)
        if (endMillis != null && endMillis <= now) return NotificationState.EXPIRED
        val triggerAt = request.behavior.triggerAtEpochMillis
        return when {
            triggerAt == null -> NotificationState.READY
            triggerAt <= now -> NotificationState.CANCELLED
            else -> NotificationState.SCHEDULED
        }
    }

    private fun NotificationRequest.toSnapshot(
        state: NotificationState,
        updatedAt: Long,
        version: Long
    ): NotificationSnapshot {
        return NotificationSnapshot(
            key = key,
            kind = kind,
            state = state,
            route = route,
            display = display,
            notificationId = notificationId,
            smallIconResId = smallIconResId,
            scheduleInstanceKey = scheduleInstanceKey,
            offsetMinutes = offsetMinutes,
            channelKey = channelKey,
            category = category,
            behavior = behavior,
            tapTarget = tapTarget,
            actions = actions,
            updatedAtEpochMillis = updatedAt,
            version = version,
            metadata = metadata
        )
    }

    fun showCreatedEventResultNotifications(sourceType: String, events: List<Event>) {
        if (events.isEmpty()) return
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val useLiveCapsule = (appContext as? App)?.settingsQueryApi?.settings?.value?.isLiveCapsuleEnabled == true
        events.forEach { event ->
            val idKey = event.id?.toString() ?: "${event.title}:${event.startTS}"
            val notificationId = NotificationIds.createdEventResult(sourceType, idKey)
            val tapIntent = Intent(appContext, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                appContext,
                notificationId,
                tapIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val title = EventTimelinePresenter.present(appContext, event).title
            val notification = buildCreatedEventResultNotification(
                notificationId = notificationId,
                title = title,
                event = event,
                pendingIntent = pendingIntent
            )
            manager.notify(notificationId, notification)
            if (useLiveCapsule) {
                scheduleResultNotificationTimeout(manager, notificationId)
            }
        }
        if (!useLiveCapsule && events.size > 1) {
            showCreatedEventsGroupSummary(manager, events.size)
        }
    }

    fun showRecognitionFailureResultNotification(display: RecognitionFailureDisplay) {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = NotificationIds.RECOGNITION_FAILURE_RESULT
        val tapIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val primaryTitle = compactResultTitle("识别失败", display.reason)
        val normalContent = RecognitionNormalDisplay.failure(display.reason, display.suggestion)
        val notification = buildRecognitionResultNotification(
            notificationId = notificationId,
            shortText = "识别失败",
            title = primaryTitle,
            contentLines = listOf(display.suggestion),
            eventType = EVENT_TYPE_RECOGNITION_FAILED,
            description = display.reason,
            color = RECOGNITION_FAILED_COLOR,
            iconResId = R.drawable.ic_stat_error,
            pendingIntent = pendingIntent,
            groupKey = null,
            normalContent = normalContent
        )
        manager.notify(notificationId, notification)
        scheduleResultNotificationTimeout(manager, notificationId)
    }

    fun showQuickMemoScheduleSuggestion(suggestionId: Long, quickMemoId: Long, draft: RecognitionDraft) {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = NotificationIds.quickMemoSuggestion(suggestionId)
        val tapIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_QUICK_MEMO_ID, quickMemoId)
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val actionIntent = Intent(appContext, EventActionReceiver::class.java).apply {
            action = EventActionReceiver.ACTION_CREATE_QUICK_MEMO_SUGGESTION
            putExtra(EventActionReceiver.EXTRA_SUGGESTION_ID, suggestionId)
        }
        val pendingAction = PendingIntent.getBroadcast(
            appContext,
            notificationId + 100,
            actionIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val contentLines = quickMemoSuggestionContentLines(draft)
        val normalContent = RecognitionNormalDisplay.quickMemoSuggestion(draft.title, contentLines)
        val actionSpec = CapsuleActionSpec(
            label = "添加",
            receiverAction = EventActionReceiver.ACTION_CREATE_QUICK_MEMO_SUGGESTION,
            extraLongKey = EventActionReceiver.EXTRA_SUGGESTION_ID,
            extraLongValue = suggestionId
        )
        val notification = buildRecognitionResultNotification(
            notificationId = notificationId,
            shortText = "识别日程",
            title = compactResultTitle("识别日程", draft.title.ifBlank { "未命名日程" }),
            contentLines = contentLines,
            eventType = EVENT_TYPE_RECOGNITION_RESULT,
            description = cleanResultText(draft.description) ?: formatDraftTime(draft),
            color = RECOGNITION_RESULT_COLOR,
            iconResId = R.drawable.ic_stat_success,
            pendingIntent = pendingIntent,
            groupKey = null,
            endMillis = System.currentTimeMillis() + quickMemoSuggestionTimeoutMs(),
            actionSpec = actionSpec,
            actionPendingIntent = pendingAction,
            normalContent = normalContent
        )
        manager.notify(notificationId, notification)
        scheduleResultNotificationTimeout(manager, notificationId, quickMemoSuggestionTimeoutMs())
    }

    fun showDailySummaryNotification(payload: DailySummaryPayload, isMorning: Boolean) {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = if (isMorning) {
            NotificationIds.DAILY_SUMMARY_MORNING
        } else {
            NotificationIds.DAILY_SUMMARY_EVENING
        }
        val tapIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val settings = (appContext as? App)?.settingsQueryApi?.settings?.value
        val notification = if (settings?.isLiveCapsuleEnabled == true) {
            val display = NotificationTemplateCenter.composeDailySchedule(
                title = payload.title,
                shortTitle = payload.shortTitle,
                fullLines = payload.fullLines,
                compactLines = payload.compactLines,
                templateMode = settings.liveNotificationTemplateMode
            )
            val item = CapsuleUiState.Active.CapsuleItem(
                id = "daily_summary_${if (isMorning) "morning" else "evening"}",
                notifId = notificationId,
                type = TYPE_DAILY_SUMMARY,
                eventType = EVENT_TYPE_DAILY_SUMMARY,
                title = payload.title,
                content = payload.content,
                description = payload.content,
                color = DAILY_SUMMARY_COLOR,
                startMillis = System.currentTimeMillis(),
                endMillis = System.currentTimeMillis() + dailySummaryTimeoutMs(),
                display = display
            )
            capsuleProvider.buildNotification(
                context = appContext,
                item = item,
                iconResId = R.drawable.ic_notification_small
            ).apply {
                contentIntent = pendingIntent
            }
        } else {
            NotificationCompat.Builder(appContext, App.CHANNEL_ID_POPUP)
                .setSmallIcon(R.drawable.ic_notification_small)
                .setContentTitle(payload.title)
                .setContentText(payload.content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(payload.content))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()
        }

        manager.notify(notificationId, notification)
        if (settings?.isLiveCapsuleEnabled == true) {
            scheduleResultNotificationTimeout(manager, notificationId, dailySummaryTimeoutMs())
        }
    }

    private fun buildCreatedEventResultNotification(
        notificationId: Int,
        title: String,
        event: Event,
        pendingIntent: PendingIntent
    ): Notification {
        val app = appContext as? App
        val settings = app?.settingsQueryApi?.settings?.value
        if (settings?.isLiveCapsuleEnabled == true) {
            val contentLines = recognitionSuccessContentLines(event)
            val color = event.color.takeIf { it != 0 } ?: RECOGNITION_RESULT_COLOR
            val eventType = EventPresenter.resolveRuleId(event).ifBlank { EVENT_TYPE_RECOGNITION_RESULT }
            return buildRecognitionResultNotification(
                notificationId = notificationId,
                shortText = "识别成功",
                title = compactResultTitle("识别成功", title),
                contentLines = contentLines,
                eventType = eventType,
                description = stripSourceImageMarkers(event.description),
                color = color,
                iconResId = R.drawable.ic_stat_success,
                pendingIntent = pendingIntent,
                groupKey = GROUP_CREATED_EVENTS,
                endMillis = event.endTS.takeIf { it > 0L }?.times(1000L)
            )
        }

        val contentLines = recognitionSuccessContentLines(event)
        val normalContent = RecognitionNormalDisplay.success(title, contentLines, formatEventTime(event))
        return NotificationCompat.Builder(appContext, App.CHANNEL_ID_POPUP)
            .setSmallIcon(R.drawable.ic_stat_success)
            .setContentTitle(normalContent.title)
            .setContentText(normalContent.contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(normalContent.bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setGroup(GROUP_CREATED_EVENTS)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
    }

    private fun buildRecognitionResultNotification(
        notificationId: Int,
        shortText: String,
        title: String,
        contentLines: List<String>,
        eventType: String,
        description: String,
        color: Int,
        iconResId: Int?,
        pendingIntent: PendingIntent,
        groupKey: String?,
        endMillis: Long? = null,
        actionSpec: CapsuleActionSpec? = null,
        actionPendingIntent: PendingIntent? = null,
        normalContent: NormalNotificationContent? = null
    ): Notification {
        val settings = (appContext as? App)?.settingsQueryApi?.settings?.value
        if (settings?.isLiveCapsuleEnabled == true) {
            val display = RecognitionLiveDisplay.eventResult(
                shortText = shortText,
                title = title,
                contentLines = contentLines,
                action = actionSpec
            )
            val item = CapsuleUiState.Active.CapsuleItem(
                id = "recognition_result_$notificationId",
                notifId = notificationId,
                type = TYPE_RECOGNITION_RESULT,
                eventType = eventType,
                title = title,
                content = display.secondaryText.orEmpty(),
                description = description,
                color = color,
                startMillis = System.currentTimeMillis(),
                endMillis = endMillis ?: System.currentTimeMillis() + resultNotificationTimeoutMs(),
                display = display
            )
            return capsuleProvider.buildNotification(
                context = appContext,
                item = item,
                iconResId = iconResId ?: IconUtils.getSmallIconForCapsule(appContext, item)
            ).apply {
                contentIntent = pendingIntent
            }
        }

        val fallbackContent = contentLines.joinToString("\n").ifBlank { RecognitionNormalDisplay.fallbackDetail() }
        val displayContent = normalContent ?: NormalNotificationContent(
            title = title,
            contentText = contentLines.firstOrNull() ?: fallbackContent,
            bigText = fallbackContent
        )
        return NotificationCompat.Builder(appContext, App.CHANNEL_ID_POPUP)
            .setSmallIcon(iconResId ?: R.drawable.ic_notification_small)
            .setContentTitle(displayContent.title)
            .setContentText(displayContent.contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayContent.bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .also { builder -> groupKey?.let { builder.setGroup(it) } }
            .setContentIntent(pendingIntent)
            .also { builder ->
                if (actionSpec != null && actionPendingIntent != null) {
                    builder.addAction(R.drawable.ic_notification_small, actionSpec.label, actionPendingIntent)
                }
            }
            .setAutoCancel(true)
            .build()
    }

    private fun recognitionSuccessContentLines(event: Event): List<String> {
        return RecognitionNormalDisplay.resultLines(
            time = formatEventTime(event),
            description = stripSourceImageMarkers(event.description),
            location = event.location
        )
    }

    private fun quickMemoSuggestionContentLines(draft: RecognitionDraft): List<String> {
        return RecognitionNormalDisplay.resultLines(
            time = formatDraftTime(draft),
            description = draft.description,
            location = draft.location
        )
    }

    private fun compactResultTitle(status: String, detail: String): String {
        val cleanDetail = cleanResultTitleDetail(detail)
        return if (cleanDetail.isBlank()) status else "$status|$cleanDetail"
    }

    private fun cleanResultTitleDetail(value: String): String {
        return value
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            .orEmpty()
    }

    private fun cleanResultText(value: String?): String? {
        val clean = value
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString(" ")
            ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
            ?: return null
        return if (clean.length > RESULT_TEXT_MAX_CHARS) {
            clean.take(RESULT_TEXT_MAX_CHARS - 1) + "..."
        } else {
            clean
        }
    }

    private fun scheduleResultNotificationTimeout(manager: NotificationManager, notificationId: Int) {
        scheduleResultNotificationTimeout(manager, notificationId, resultNotificationTimeoutMs())
    }

    private fun scheduleResultNotificationTimeout(
        manager: NotificationManager,
        notificationId: Int,
        timeoutMs: Long
    ) {
        Handler(Looper.getMainLooper()).postDelayed({
            manager.cancel(notificationId)
        }, timeoutMs)
    }

    fun showStandardNotificationForEvent(event: Event, label: String = ScheduleNormalDisplay.startLabel()) {
        val channelId = App.CHANNEL_ID_POPUP
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = event.id?.let { NotificationIds.standardReminder(it) }
            ?: NotificationIds.standardReminder(event.title.ifBlank { "event" })
        val tapIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val app = appContext as? App
        val eventActionQueryApi = app?.eventActionQueryApi
        val effectiveRuleId = eventActionQueryApi?.resolveEffectiveRuleId(
            intentRuleId = null,
            fallbackTag = event.tag,
            event = event
        ) ?: event.tag

        val actionText = eventActionQueryApi?.actionTextForRule(effectiveRuleId).orEmpty()
        val presentation = app?.notificationPresentationQueryApi?.buildPresentation(
            startTime = event.startTime,
            endTime = event.endTime,
            location = event.location,
            label = label,
            actionText = actionText
        )
        val contentText = ScheduleNormalDisplay.reminderContent(presentation?.contentText, label)

        val builder = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(EventTimelinePresenter.present(appContext, event).title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (event.color.hashCode() != 0) {
            builder.setColor(event.color.hashCode())
        }

        try {
            val matched = app?.scheduleCenter?.events?.value?.find { it.id == event.id }
            val actionButton = eventActionQueryApi?.buildActionButton(effectiveRuleId, matched)
            if (actionButton != null) {
                val actionIntent = Intent(appContext, EventActionReceiver::class.java).apply {
                    action = actionButton.intentAction
                    putExtra(EventActionReceiver.EXTRA_EVENT_ID, event.idString)
                }
                val pendingAction = PendingIntent.getBroadcast(
                    appContext,
                    notificationId + 100,
                    actionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(R.drawable.ic_notification_small, actionButton.text, pendingAction)
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查事件状态失败: ${e.message}")
        }

        manager.notify(notificationId, builder.build())
    }

    fun showPickupInitialNotification(event: Event) {
        val eventId = event.id ?: return
        val notificationId = NotificationIds.pickupInitial(eventId)
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val completeIntent = Intent(appContext, EventActionReceiver::class.java).apply {
            action = EventActionReceiver.ACTION_COMPLETE
            putExtra(EventActionReceiver.EXTRA_EVENT_ID, eventId.toString())
        }
        val pendingComplete = PendingIntent.getBroadcast(
            appContext,
            notificationId + 1,
            completeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val model = EventTimelinePresenter.present(appContext, event).renderModel
        val content = ScheduleNormalDisplay.pickupInitialContent(
            title = model.title,
            subtitle = model.subtitle,
            detail = model.detail,
            description = stripSourceImageMarkers(event.description)
        )
        val notification = NotificationCompat.Builder(appContext, App.CHANNEL_ID_POPUP)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(content.title)
            .setContentText(content.contentText)
            .setStyle(NotificationCompat.BigTextStyle()
                .setBigContentTitle(content.title)
                .bigText(content.bigText)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(false)
            .setOngoing(true)
            .addAction(R.drawable.ic_notification_small, ScheduleNormalDisplay.pickupDoneAction(), pendingComplete)
            .build()

        manager.notify(notificationId, notification)
    }

    fun showAccessibilityServiceDisabledNotification(pendingIntent: PendingIntent) {
        val content = SystemNormalDisplay.accessibilityServiceDisabled()
        val notification = NotificationCompat.Builder(appContext, App.CHANNEL_ID_POPUP)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(content.title)
            .setContentText(content.contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(2001, notification)
    }

    fun showFloatingPermissionDeniedNotification(notificationId: Int = 2002, durationMs: Long = resultNotificationTimeoutMs()) {
        val content = SystemNormalDisplay.floatingPermissionDenied()
        val settingsIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${appContext.packageName}")
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(appContext, App.CHANNEL_ID_POPUP)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(content.title)
            .setContentText(content.contentText)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(notificationId, notification)
        scheduleResultNotificationTimeout(manager, notificationId, durationMs)
    }

    fun showRecognitionStatusNotification(
        notificationId: Int,
        content: NormalNotificationContent,
        isProgress: Boolean,
        autoLaunch: Boolean,
        durationMs: Long? = null
    ) {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val tapIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val smallIcon = when {
            content.title.contains("分析") || content.title.contains("识别") -> IconUtils.getScanningIcon()
            content.title.contains("已添加") || content.title.contains("添加了") || content.title.contains("新增") -> IconUtils.getSuccessIcon()
            else -> R.drawable.ic_notification_small
        }

        val builder = NotificationCompat.Builder(appContext, App.CHANNEL_ID_POPUP)
            .setSmallIcon(smallIcon)
            .setContentTitle(content.title)
            .setContentText(content.contentText)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (autoLaunch || !isProgress) builder.setContentIntent(pendingIntent)
        if (isProgress) {
            builder.setProgress(0, 0, true)
            builder.setOngoing(true)
        }
        manager.notify(notificationId, builder.build())
        if (!isProgress) {
            scheduleResultNotificationTimeout(manager, notificationId, durationMs ?: resultNotificationTimeoutMs())
        }
    }

    fun cancelNotification(notificationId: Int) {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(notificationId)
    }

    fun showStandardNotification(
        eventId: String,
        title: String,
        label: String,
        eventLocation: String = "",
        eventStartTime: String = "",
        eventEndTime: String = "",
        eventTag: String = "",
        eventColor: Int = 0,
        eventRuleId: String? = null
    ) {
        val channelId = App.CHANNEL_ID_POPUP
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = NotificationIds.standardReminder(eventId)
        val tapIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val app = appContext as? App
        val scheduleCenter = app?.scheduleCenter
        val eventActionQueryApi = app?.eventActionQueryApi
        val matchedEvent = scheduleCenter?.events?.value?.find { it.id == eventId.toLongOrNull() }

        val effectiveRuleId = eventActionQueryApi?.resolveEffectiveRuleId(
            intentRuleId = eventRuleId,
            fallbackTag = eventTag,
            event = matchedEvent
        ) ?: eventTag

        val actionText = eventActionQueryApi?.actionTextForRule(effectiveRuleId).orEmpty()
        val presentation = app?.notificationPresentationQueryApi?.buildPresentation(
            startTime = eventStartTime,
            endTime = eventEndTime,
            location = eventLocation,
            label = label,
            actionText = actionText
        )
        val contentText = ScheduleNormalDisplay.reminderContent(presentation?.contentText, label)
        val displayTitle = if (matchedEvent != null && TransportNormalDisplay.isTransportRule(effectiveRuleId)) {
            TransportNormalDisplay.title(
                renderedTitle = EventTimelinePresenter.present(appContext, matchedEvent).title,
                fallbackTitle = title
            )
        } else {
            title
        }

        val builder = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(displayTitle)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        if (eventColor != 0) {
            builder.setColor(eventColor)
        }

        try {
            val actionButton = eventActionQueryApi?.buildActionButton(effectiveRuleId, matchedEvent)
            if (actionButton != null) {
                val actionIntent = Intent(appContext, EventActionReceiver::class.java).apply {
                    action = actionButton.intentAction
                    putExtra(EventActionReceiver.EXTRA_EVENT_ID, eventId)
                }
                val pendingAction = PendingIntent.getBroadcast(
                    appContext,
                    notificationId + 100,
                    actionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(R.drawable.ic_notification_small, actionButton.text, pendingAction)
            }
        } catch (e: Exception) {
            Log.e(TAG, "检查事件状态失败: ${e.message}")
        }

        manager.notify(notificationId, builder.build())
    }

    fun showPlainNotification(
        notificationId: Int,
        title: String,
        content: String,
        channelId: String = App.CHANNEL_ID_POPUP,
        smallIcon: Int = R.drawable.ic_notification_small,
        ongoing: Boolean = false,
        autoCancel: Boolean = true,
        timeoutMs: Long? = null
    ) {
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val tapIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            notificationId,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setOngoing(ongoing)
            .setAutoCancel(autoCancel)
        if (timeoutMs != null) builder.setTimeoutAfter(timeoutMs)

        manager.notify(notificationId, builder.build())
    }

    fun publishPlainNotification(request: NotificationRequest): NotificationResult {
        return runBlocking(Dispatchers.IO) {
            val created = create(request)
            if (created is NotificationResult.Failure) {
                created
            } else {
                publishReadySnapshot(request.key)
            }
        }
    }

    private fun formatEventTime(event: Event): String {
        return if (event.startDate == event.endDate) {
            "${event.startTime} - ${event.endTime}"
        } else {
            "${formatEpoch(event.startTS)} - ${formatEpoch(event.endTS)}"
        }
    }

    private fun formatDraftTime(draft: RecognitionDraft): String {
        val start = draft.startTS.takeIf { it > 0L }?.let(::formatEpoch).orEmpty()
        val end = draft.endTS.takeIf { it > 0L }?.let(::formatEpoch).orEmpty()
        return when {
            start.isNotBlank() && end.isNotBlank() -> "$start - $end"
            start.isNotBlank() -> start
            else -> RecognitionNormalDisplay.fallbackCreateEvent()
        }
    }

    private fun formatEpoch(epochSeconds: Long): String {
        return Instant.ofEpochSecond(epochSeconds)
            .atZone(ZoneId.systemDefault())
            .format(RESULT_TIME_FORMATTER)
    }

    private fun showCreatedEventsGroupSummary(manager: NotificationManager, count: Int) {
        val content = RecognitionNormalDisplay.createdEventsSummary(count)
        val notification = NotificationCompat.Builder(appContext, App.CHANNEL_ID_POPUP)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(content.title)
            .setContentText(content.contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setGroup(GROUP_CREATED_EVENTS)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        manager.notify(NotificationIds.CREATED_EVENT_RESULT_GROUP, notification)
    }

    private fun showQuickMemoSuggestionsGroupSummary(manager: NotificationManager) {
        val content = RecognitionNormalDisplay.quickMemoSuggestionsSummary()
        val notification = NotificationCompat.Builder(appContext, App.CHANNEL_ID_POPUP)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(content.title)
            .setContentText(content.contentText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setGroup(GROUP_QUICK_MEMO_SUGGESTIONS)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        manager.notify(NotificationIds.QUICK_MEMO_SUGGESTION_GROUP, notification)
    }
}
