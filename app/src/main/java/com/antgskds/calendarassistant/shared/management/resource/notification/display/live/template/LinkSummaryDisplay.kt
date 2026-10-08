package com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template
import com.antgskds.calendarassistant.feature.notification.model.*

object LinkSummaryDisplay {
    fun snapshot(title: String) = NotificationDisplaySnapshot("摘要已生成","摘要已生成",
        title.ifBlank { "点击查看随口记" },expandedText=title.ifBlank { "点击查看随口记" })
    fun notification(display: NotificationDisplaySnapshot, actions: List<NotificationAction>, target: NotificationTapTarget?) =
        ClipboardCodePromptDisplay.notification(display,actions,target)
}
