package com.antgskds.calendarassistant.feature.recognition.ingest.clipboard

import com.antgskds.calendarassistant.feature.notification.model.NotificationAction
import com.antgskds.calendarassistant.feature.notification.model.NotificationKey
import com.antgskds.calendarassistant.feature.quickmemo.domain.QuickMemoLink
import com.antgskds.calendarassistant.feature.quickmemo.domain.QuickMemoLinkParser
import com.antgskds.calendarassistant.feature.recognition.ingest.instantcode.InstantCodeCandidate
import com.antgskds.calendarassistant.feature.recognition.ingest.instantcode.InstantCodeType

/** 动作只携带键；正文保留在应用私有通知快照中，跨进程点击仍保存当时的候选。 */
object ClipboardPromptAction {
    const val RECEIVER_ACTION = "com.antgskds.calendarassistant.action.ACCEPT_CLIPBOARD"
    const val EXTRA_KEY = "clipboard_notification_key"

    fun create(key: NotificationKey, label: String) =
        NotificationAction(RECEIVER_ACTION, label, mapOf(EXTRA_KEY to key.value))

    fun encode(code: InstantCodeCandidate) = mapOf(
        "codeType" to code.type.name, "code" to code.code,
        "company" to code.company, "location" to code.location,
    )

    fun code(metadata: Map<String, String>): InstantCodeCandidate? {
        val type = InstantCodeType.entries.firstOrNull { it.name == metadata["codeType"] } ?: return null
        val code = metadata["code"]?.takeIf { it.isNotBlank() } ?: return null
        return InstantCodeCandidate(type, code, metadata["company"].orEmpty(), metadata["location"].orEmpty())
    }

    fun encode(link: QuickMemoLink) = mapOf("url" to link.url, "caption" to link.caption)

    fun link(metadata: Map<String, String>): QuickMemoLink? =
        metadata["url"]?.let(QuickMemoLinkParser::parse)?.copy(caption = metadata["caption"].orEmpty())
}
