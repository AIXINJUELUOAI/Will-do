package com.antgskds.calendarassistant.feature.notification.model

data class NotificationDisplaySnapshot(
    val shortText: String,
    val primaryText: String,
    val secondaryText: String? = null,
    val tertiaryText: String? = null,
    val expandedText: String? = null
)

data class NotificationAction(
    val key: String,
    val label: String,
    val payload: Map<String, String> = emptyMap()
) {
    val openQuickMemoId: Long?
        get() = if (key == OPEN_QUICK_MEMO) payload["quickMemoId"]?.toLongOrNull()?.takeIf { it > 0L } else null

    companion object {
        const val OPEN_QUICK_MEMO = "open_quick_memo_detail"
        fun viewQuickMemo(id: Long) = NotificationAction(OPEN_QUICK_MEMO, "查看", mapOf("quickMemoId" to id.toString()))
    }
}

data class NotificationTapTarget(
    val type: NotificationTapTargetType,
    val payload: Map<String, String> = emptyMap()
)

enum class NotificationTapTargetType {
    QUICK_MEMO_DETAIL,
    APP_HOME,
    SCHEDULE_DETAIL,
    PICKUP_LIST,
    SETTINGS,
    DEBUG,
    NONE
}
