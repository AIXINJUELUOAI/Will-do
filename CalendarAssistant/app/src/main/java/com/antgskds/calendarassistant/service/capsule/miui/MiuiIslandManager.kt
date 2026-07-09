package com.antgskds.calendarassistant.service.capsule.miui

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Icon
import android.util.Log
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.calendar.helpers.STATE_CHECKED_IN
import com.antgskds.calendarassistant.core.service.pickup.PickupQrHandleActivity
import com.antgskds.calendarassistant.core.util.OsUtils
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.data.state.CapsuleUiState
import com.antgskds.calendarassistant.data.state.CapsuleType
import com.antgskds.calendarassistant.service.capsule.CapsuleUiUtils
import com.antgskds.calendarassistant.service.capsule.IconUtils
import com.antgskds.calendarassistant.platform.receiver.EventActionReceiver
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.vendor.xiaomi.XiaomiLiveNotificationTemplate
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.vendor.xiaomi.XiaomiLiveTemplateKind
import com.antgskds.calendarassistant.platform.xposed.MiuiIslandAction
import com.antgskds.calendarassistant.platform.xposed.MiuiIslandDispatcher
import com.antgskds.calendarassistant.platform.xposed.MiuiIslandRequest
import com.antgskds.calendarassistant.platform.xposed.XposedModuleStatus

object MiuiIslandManager {
    private const val TAG = "MiuiIslandManager"
    private const val MAX_TIMEOUT_SECS = 3600
    private const val MIN_TIMEOUT_SECS = 5

    @Volatile private var lastRequestKey: String? = null
    @Volatile private var lastNotifId: Int? = null

    private data class ActionPayload(
        val actions: List<MiuiIslandAction>,
        val actionTitle: String?,
        val actionIntentUri: String?
    )

    fun update(context: Context, capsules: List<CapsuleUiState.Active.CapsuleItem>) {
        if (!isAvailable()) return
        val target = selectTargetCapsule(capsules) ?: run {
            clear(context)
            return
        }

        val isNewTarget = lastNotifId == null || lastNotifId != target.notifId
        val request = buildRequest(context, target, isNewTarget)
        val requestKey = buildRequestKey(request)
        if (requestKey == lastRequestKey) return

        val previousNotifId = lastNotifId
        if (previousNotifId != null && previousNotifId != request.notifId) {
            sendDismiss(context, previousNotifId)
        }

        lastRequestKey = requestKey
        lastNotifId = request.notifId
        MiuiIslandDispatcher.sendBroadcast(context, request)
        Log.d(TAG, "send island: ${request.title} | ${request.content} | actions=${request.actions.size}")
    }

    fun clear(context: Context) {
        if (!isAvailable()) return
        lastNotifId?.let { sendDismiss(context, it) }
        lastRequestKey = null
        lastNotifId = null
    }

    private fun sendDismiss(context: Context, notifId: Int) {
        MiuiIslandDispatcher.sendBroadcast(
            context,
            MiuiIslandRequest(
                title = "",
                content = "",
                notifId = notifId,
                dismissIsland = true,
                showNotification = false,
            )
        )
        Log.d(TAG, "dismiss island: $notifId")
    }

    private fun isAvailable(): Boolean = OsUtils.isHyperOS() && XposedModuleStatus.isActive()

    private fun selectTargetCapsule(
        capsules: List<CapsuleUiState.Active.CapsuleItem>
    ): CapsuleUiState.Active.CapsuleItem? {
        if (capsules.isEmpty()) return null
        val now = System.currentTimeMillis()
        val candidates = capsules.filter { isCapsuleActive(it, now) }
        if (candidates.isEmpty()) return null
        return candidates.sortedWith(
            compareByDescending<CapsuleUiState.Active.CapsuleItem> { it.startMillis }
                .thenByDescending { it.endMillis }
        ).first()
    }

    private fun isCapsuleActive(item: CapsuleUiState.Active.CapsuleItem, now: Long): Boolean {
        val extraMillis = if (item.type == CapsuleType.PICKUP ||
            item.type == CapsuleType.PICKUP_EXPIRED
        ) {
            5 * 60 * 1000L
        } else {
            0L
        }
        return now < item.endMillis + extraMillis
    }

    private fun buildRequest(
        context: Context,
        item: CapsuleUiState.Active.CapsuleItem,
        isNewTarget: Boolean
    ): MiuiIslandRequest {
        val display = item.display
        val useShortTitle = useShortTitleForIsland(item)
        val actionsPayload = buildActions(context, item)
        val actions = actionsPayload.actions
        val template = XiaomiLiveNotificationTemplate.create(
            display = display,
            useShortTitle = useShortTitle,
            hasActions = actions.isNotEmpty(),
            forceTextIcon = useTextIconOnlyTemplate(item),
            summaryStatus = buildSummaryStatus(item),
            startMillis = item.startMillis,
            endMillis = item.endMillis
        )
        val templateType = template.templateKind.toMiuiTemplateType()
        val (iconLight, iconDark) = buildEventIcons(context, item)
        val appIcon = buildAppIcon(context)
        val contentIntent = createContentPendingIntent(context, item)
        val timeout = computeTimeout(item)
        val highlightColor = formatHighlightColor(item.color)

        return MiuiIslandRequest(
            title = template.title,
            content = template.content,
            icon = iconLight,
            iconDark = iconDark,
            appIcon = appIcon,
            summaryStatus = template.summaryStatus,
            summaryTitle = template.summaryTitle,
            notifId = item.notifId,
            timeoutSecs = timeout,
            firstFloat = isNewTarget,
            enableFloat = true,
            showNotification = true,
            highlightColor = highlightColor,
            dismissIsland = false,
            contentIntent = contentIntent,
            actions = actions,
            templateType = templateType,
            tagText = template.tagText,
            hintTitle = template.hintTitle,
            actionTitle = actionsPayload.actionTitle,
            actionIntentUri = actionsPayload.actionIntentUri,
        )
    }

    private fun useShortTitleForIsland(item: CapsuleUiState.Active.CapsuleItem): Boolean {
        return when (item.type) {
            CapsuleType.WEATHER_ALERT,
            CapsuleType.OCR_PROGRESS,
            CapsuleType.OCR_RESULT,
            CapsuleType.MODEL_LOADING,
            CapsuleType.QUICK_MEMO_RECORDING,
            CapsuleType.TEXT_QUICK_MEMO -> true
            else -> false
        }
    }

    private fun useTextIconOnlyTemplate(item: CapsuleUiState.Active.CapsuleItem): Boolean {
        return when (item.type) {
            CapsuleType.OCR_PROGRESS,
            CapsuleType.OCR_RESULT -> true
            else -> false
        }
    }

    private fun XiaomiLiveTemplateKind.toMiuiTemplateType(): Int {
        return when (this) {
            XiaomiLiveTemplateKind.TEXT_ICON -> MiuiIslandRequest.TEMPLATE_TEXT_ICON
            XiaomiLiveTemplateKind.TEXT_ICON_ACTION -> MiuiIslandRequest.TEMPLATE_TEXT_ICON_ACTION
        }
    }

    private fun buildEventIcons(
        context: Context,
        item: CapsuleUiState.Active.CapsuleItem
    ): Pair<Icon?, Icon?> {
        val iconResId = IconUtils.getSmallIconForCapsule(context, item)
        val bitmap = CapsuleUiUtils.drawableToBitmap(context, iconResId)
        if (bitmap != null) {
            val icon = Icon.createWithBitmap(CapsuleUiUtils.tintBitmap(bitmap, Color.WHITE))
            return icon to icon
        }
        val fallback = Icon.createWithResource(context, iconResId)
        return fallback to fallback
    }

    private fun buildAppIcon(context: Context): Icon? {
        return try {
            val appInfo = context.applicationInfo
            val drawable = context.packageManager.getApplicationIcon(appInfo)
            val bitmap = CapsuleUiUtils.drawableToBitmap(drawable)
            Icon.createWithBitmap(bitmap)
        } catch (_: Exception) {
            null
        }
    }

    private fun buildActions(
        context: Context,
        item: CapsuleUiState.Active.CapsuleItem
    ): ActionPayload {
        val actionSpecs = item.display.effectiveActions
        if (actionSpecs.isEmpty()) return emptyActionPayload()

        var firstActionIntentUri: String? = null
        val actions = actionSpecs.mapIndexed { index, action ->
            val broadcastIntent = Intent(context, EventActionReceiver::class.java).apply {
                this.action = action.receiverAction
                if (action.extraLongKey != null && action.extraLongValue != null) {
                    putExtra(action.extraLongKey, action.extraLongValue)
                } else {
                    putExtra(EventActionReceiver.EXTRA_EVENT_ID, item.id)
                }
            }
            if (index == 0) {
                firstActionIntentUri = broadcastIntent.toUri(Intent.URI_INTENT_SCHEME)
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                item.id.hashCode() xor action.receiverAction.hashCode() xor index,
                broadcastIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            MiuiIslandAction(action.label, pendingIntent)
        }
        return ActionPayload(
            actions = actions,
            actionTitle = actions.firstOrNull()?.title,
            actionIntentUri = firstActionIntentUri
        )
    }

    private fun emptyActionPayload(): ActionPayload = ActionPayload(emptyList(), null, null)

    private fun createContentPendingIntent(
        context: Context,
        item: CapsuleUiState.Active.CapsuleItem
    ): PendingIntent {
        val tapEventId = item.display.tapEventId?.toLongOrNull()
        val tapIntent = Intent(context, if (tapEventId != null) PickupQrHandleActivity::class.java else MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (tapEventId != null) {
                putExtra(MainActivity.EXTRA_OPEN_EVENT_ID, tapEventId)
            } else if (item.display.tapOpensPickupList) {
                putExtra("openPickupList", true)
            }
        }
        return PendingIntent.getActivity(
            context,
            item.id.hashCode(),
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun computeTimeout(item: CapsuleUiState.Active.CapsuleItem): Int {
        val remaining = (item.endMillis - System.currentTimeMillis()) / 1000
        if (remaining <= 0) return MIN_TIMEOUT_SECS
        return remaining.toInt().coerceIn(MIN_TIMEOUT_SECS, MAX_TIMEOUT_SECS)
    }

    private fun formatHighlightColor(color: Int): String {
        val rgb = color and 0x00FFFFFF
        return String.format("#%06X", rgb)
    }

    private fun buildRequestKey(request: MiuiIslandRequest): String {
        val actionsKey = request.actions.joinToString("|") { it.title }
        return listOf(
            request.notifId.toString(),
            request.title,
            request.content,
            request.highlightColor ?: "",
            actionsKey,
            request.templateType.toString(),
            request.tagText ?: "",
            request.hintTitle ?: "",
            request.actionTitle ?: "",
            request.actionIntentUri ?: "",
            request.summaryStatus ?: "",
            request.summaryTitle ?: ""
        ).joinToString("::")
    }

    private fun buildSummaryStatus(item: CapsuleUiState.Active.CapsuleItem): String {
        return when (item.type) {
            CapsuleType.OCR_RESULT -> "已完成"
            CapsuleType.WEATHER_ALERT -> "天气提醒"
            CapsuleType.VOICE_TRANSCRIPTION -> "语音转写"
            CapsuleType.TEXT_QUICK_MEMO -> "随口记"
            CapsuleType.QUICK_MEMO_RECORDING -> "录音中"
            CapsuleType.OCR_PROGRESS,
            CapsuleType.NETWORK_SPEED -> "进行中"
            else -> {
                when (item.eventType) {
                    RuleMatchingEngine.RULE_PICKUP -> if (isFoodPickup(item.description)) "待取餐" else "待取件"
                    RuleMatchingEngine.RULE_FOOD -> "待取餐"
                    RuleMatchingEngine.RULE_TAXI -> "待用车"
                    RuleMatchingEngine.RULE_TICKET -> "待取票"
                    RuleMatchingEngine.RULE_SENDER -> "待寄件"
                    RuleMatchingEngine.RULE_TRAIN -> if (item.state == STATE_CHECKED_IN) "待落座" else "待检票"
                    RuleMatchingEngine.RULE_FLIGHT -> if (item.state == STATE_CHECKED_IN) "待落座" else "待登机"
                    RuleMatchingEngine.RULE_COURSE -> buildTimeStatus(item)
                    else -> buildTimeStatus(item)
                }
            }
        }
    }

    private fun buildTimeStatus(item: CapsuleUiState.Active.CapsuleItem): String {
        val now = System.currentTimeMillis()
        return if (now < item.startMillis) "即将进行" else "进行中"
    }

    private fun isFoodPickup(description: String?): Boolean {
        return description?.startsWith("【取餐】") == true
    }
}
