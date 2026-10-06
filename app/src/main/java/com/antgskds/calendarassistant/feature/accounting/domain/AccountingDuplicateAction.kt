package com.antgskds.calendarassistant.feature.accounting.domain

import com.antgskds.calendarassistant.feature.notification.model.NotificationAction
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 普通与胶囊通知共用动作，只携带当前识别批次已暂存的疑似重复草稿。 */
object AccountingDuplicateAction {
    const val RECEIVER_ACTION = "com.antgskds.calendarassistant.action.COUNT_ACCOUNTING_DUPLICATE"
    const val EXTRA_DRAFT_IDS = "accounting_duplicate_draft_ids"

    fun create(draftIds: List<String>): NotificationAction? {
        val ids = draftIds.filter(String::isNotBlank).distinct()
        if (ids.isEmpty()) return null
        return NotificationAction(RECEIVER_ACTION, "仍然计入", mapOf(EXTRA_DRAFT_IDS to Json.encodeToString(ids)))
    }

    fun draftIds(payload: String?): List<String> {
        if (payload == null) return emptyList()
        return runCatching { Json.decodeFromString<List<String>>(payload) }.getOrDefault(emptyList())
            .filter(String::isNotBlank).distinct()
    }
}
