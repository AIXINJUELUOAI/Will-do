package com.antgskds.calendarassistant.feature.notification.policy

import com.antgskds.calendarassistant.feature.notification.model.*

/** 待确认候选拥有自己的通知，不依赖已入库日程的胶囊状态计算。 */
object ClipboardCodePromptDeliveryPolicy {
    private const val KEY_PREFIX = "clipboard:prompt:"

    fun key(traceId: Long) = NotificationKey("$KEY_PREFIX$traceId")

    fun owns(key: NotificationKey): Boolean = key.value.startsWith(KEY_PREFIX)

    fun route(kind: NotificationKind, liveCapsuleEnabled: Boolean): NotificationRoute? {
        if (kind != NotificationKind.CLIPBOARD_CODE_PROMPT) return null
        return if (liveCapsuleEnabled) NotificationRoute.LIVE else NotificationRoute.NORMAL
    }

    fun isDelivered(result: NotificationResult): Boolean =
        result is NotificationResult.Success && result.state == NotificationState.POSTED
}
