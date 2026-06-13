package com.antgskds.calendarassistant.core.center

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Handler
import android.os.Looper
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
import com.antgskds.calendarassistant.service.capsule.CapsuleDisplayModel
import com.antgskds.calendarassistant.service.capsule.NotificationTemplateCenter
import com.antgskds.calendarassistant.data.state.CapsuleUiState
import com.antgskds.calendarassistant.service.capsule.IconUtils
import com.antgskds.calendarassistant.service.capsule.provider.FlymeCapsuleProvider
import com.antgskds.calendarassistant.service.capsule.provider.ICapsuleProvider
import com.antgskds.calendarassistant.service.capsule.provider.NativeCapsuleProvider
import com.antgskds.calendarassistant.service.receiver.EventActionReceiver
import com.antgskds.calendarassistant.service.notification.NotificationIds
import com.antgskds.calendarassistant.core.util.FlymeUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class NotificationCenter(
    private val appContext: Context
) {
    companion object {
        private const val TAG = "NotificationCenter"
        private const val GROUP_CREATED_EVENTS = "calendar_assistant_created_events"
        private const val GROUP_QUICK_MEMO_SUGGESTIONS = "calendar_assistant_quick_memo_suggestions"
        private val RESULT_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm")
        private const val TYPE_RECOGNITION_RESULT = 9
        private const val EVENT_TYPE_RECOGNITION_RESULT = "recognition_result"
        private const val EVENT_TYPE_RECOGNITION_FAILED = "recognition_failed"
        private const val RESULT_NOTIFICATION_TIMEOUT_MS = 8000L
        private const val QUICK_MEMO_SUGGESTION_TIMEOUT_MS = 60_000L
        private const val DAILY_SUMMARY_TIMEOUT_MS = 60_000L
        private const val RESULT_SHORT_TITLE_MAX_CHARS = 6
        private const val RESULT_TEXT_MAX_CHARS = 56
        private val RECOGNITION_RESULT_COLOR = Color.rgb(76, 175, 80)
        private val RECOGNITION_FAILED_COLOR = Color.rgb(244, 67, 54)
        private val DAILY_SUMMARY_COLOR = Color.rgb(103, 80, 164)
        private const val TYPE_DAILY_SUMMARY = 10
        private const val EVENT_TYPE_DAILY_SUMMARY = "daily_summary"
    }

    private val capsuleProvider: ICapsuleProvider by lazy {
        if (FlymeUtils.isFlyme()) FlymeCapsuleProvider() else NativeCapsuleProvider()
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
            groupKey = null
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
            endMillis = System.currentTimeMillis() + QUICK_MEMO_SUGGESTION_TIMEOUT_MS,
            actionSpec = actionSpec,
            actionPendingIntent = pendingAction
        )
        manager.notify(notificationId, notification)
        scheduleResultNotificationTimeout(manager, notificationId, QUICK_MEMO_SUGGESTION_TIMEOUT_MS)
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
            val display = NotificationTemplateCenter.composeBody(
                headerTitle = payload.title,
                shortText = payload.shortTitle,
                fullBodyLines = payload.fullLines,
                compactBodyLines = payload.compactLines,
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
                endMillis = System.currentTimeMillis() + DAILY_SUMMARY_TIMEOUT_MS,
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
            scheduleResultNotificationTimeout(manager, notificationId, DAILY_SUMMARY_TIMEOUT_MS)
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
        val content = contentLines.joinToString("\n").ifBlank { formatEventTime(event) }
        return NotificationCompat.Builder(appContext, App.CHANNEL_ID_POPUP)
            .setSmallIcon(R.drawable.ic_stat_success)
            .setContentTitle(compactResultTitle("识别成功", title))
            .setContentText(contentLines.firstOrNull() ?: content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
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
        actionPendingIntent: PendingIntent? = null
    ): Notification {
        val settings = (appContext as? App)?.settingsQueryApi?.settings?.value
        if (settings?.isLiveCapsuleEnabled == true) {
            val display = CapsuleDisplayModel(
                shortText = compactResultText(shortText, RESULT_SHORT_TITLE_MAX_CHARS),
                primaryText = title,
                secondaryText = contentLines.getOrNull(0),
                tertiaryText = contentLines.getOrNull(1),
                expandedText = contentLines.joinToString("\n").ifBlank { null },
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
                endMillis = endMillis ?: System.currentTimeMillis() + RESULT_NOTIFICATION_TIMEOUT_MS,
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

        val content = contentLines.joinToString("\n").ifBlank { "点击查看详情" }
        return NotificationCompat.Builder(appContext, App.CHANNEL_ID_POPUP)
            .setSmallIcon(iconResId ?: R.drawable.ic_notification_small)
            .setContentTitle(title)
            .setContentText(contentLines.firstOrNull() ?: content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
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
        val description = cleanResultText(stripSourceImageMarkers(event.description))
        val location = cleanResultText(event.location)
        val time = cleanResultText(formatEventTime(event))
        return listOfNotNull(time, description ?: location)
    }

    private fun quickMemoSuggestionContentLines(draft: RecognitionDraft): List<String> {
        val time = cleanResultText(formatDraftTime(draft))
        val description = cleanResultText(draft.description)
        val location = cleanResultText(draft.location)
        return listOfNotNull(time, description ?: location)
    }

    private fun compactResultTitle(status: String, detail: String): String {
        val cleanDetail = cleanResultTitleDetail(detail)
        return if (cleanDetail.isBlank()) status else "$status|$cleanDetail"
    }

    private fun compactResultText(value: String, maxChars: Int): String {
        val clean = value.trim()
        if (clean.length <= maxChars) return clean
        return if (maxChars <= 3) clean.take(maxChars) else clean.take(maxChars - 3) + "..."
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
        scheduleResultNotificationTimeout(manager, notificationId, RESULT_NOTIFICATION_TIMEOUT_MS)
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

    fun showStandardNotificationForEvent(event: Event, label: String = "日程开始") {
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
        val contentText = presentation?.contentText ?: if (label.isNotEmpty()) label else "点击查看详情"

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
        val contentText = presentation?.contentText ?: if (label.isNotEmpty()) label else "点击查看详情"

        val builder = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(title)
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
        autoCancel: Boolean = true
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

        manager.notify(notificationId, builder.build())
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
            else -> "点击创建事件"
        }
    }

    private fun formatEpoch(epochSeconds: Long): String {
        return Instant.ofEpochSecond(epochSeconds)
            .atZone(ZoneId.systemDefault())
            .format(RESULT_TIME_FORMATTER)
    }

    private fun showCreatedEventsGroupSummary(manager: NotificationManager, count: Int) {
        val notification = NotificationCompat.Builder(appContext, App.CHANNEL_ID_POPUP)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle("识别成功")
            .setContentText("已识别 $count 个日程")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setGroup(GROUP_CREATED_EVENTS)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        manager.notify(NotificationIds.CREATED_EVENT_RESULT_GROUP, notification)
    }

    private fun showQuickMemoSuggestionsGroupSummary(manager: NotificationManager) {
        val notification = NotificationCompat.Builder(appContext, App.CHANNEL_ID_POPUP)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle("识别到日程")
            .setContentText("随口记中发现可创建的日程")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setGroup(GROUP_QUICK_MEMO_SUGGESTIONS)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .build()
        manager.notify(NotificationIds.QUICK_MEMO_SUGGESTION_GROUP, notification)
    }
}
