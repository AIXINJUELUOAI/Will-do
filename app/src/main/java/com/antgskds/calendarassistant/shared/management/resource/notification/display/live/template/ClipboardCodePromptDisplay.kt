package com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template

import com.antgskds.calendarassistant.feature.capsule.domain.CapsuleDisplayModel
import com.antgskds.calendarassistant.feature.notification.model.NotificationDisplaySnapshot

/** 普通发布器直接渲染 snapshot；实况发布器从同一份数据生成胶囊展示。 */
object ClipboardCodePromptDisplay {
    fun snapshot(typeLabel: String) = NotificationDisplaySnapshot(
        shortText = "识别到$typeLabel",
        primaryText = "识别到$typeLabel",
        secondaryText = "点击打开 Will do，确认是否创建日程",
        expandedText = "点击打开 Will do，确认是否创建日程",
    )

    fun notification(display: NotificationDisplaySnapshot) = CapsuleDisplayModel(
        shortText = display.shortText,
        primaryText = display.primaryText,
        secondaryText = display.secondaryText,
        tertiaryText = display.tertiaryText,
        expandedText = display.expandedText,
    )
}
