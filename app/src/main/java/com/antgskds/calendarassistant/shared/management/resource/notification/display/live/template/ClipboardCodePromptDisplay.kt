package com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template

import com.antgskds.calendarassistant.feature.capsule.domain.CapsuleDisplayModel
import com.antgskds.calendarassistant.feature.notification.model.NotificationDisplaySnapshot

/** 普通发布器直接渲染 snapshot；实况发布器从同一份数据生成胶囊展示。 */
object ClipboardCodePromptDisplay {
    fun snapshot(typeLabel: String, code: String = "") = NotificationDisplaySnapshot(
        shortText = "识别到$typeLabel" + if (code.isBlank()) "" else "|$code",
        primaryText = "识别到$typeLabel" + if (code.isBlank()) "" else "|$code",
        secondaryText = "点击下方按钮即可添加",
        expandedText = "点击下方按钮即可添加",
    )

    fun linkSnapshot(source: String) = NotificationDisplaySnapshot(
        shortText = "${source.ifBlank { "发现" }}链接",
        primaryText = "识别到${source}链接",
        secondaryText = "收藏到随口记，稍后继续查看",
        expandedText = "收藏到随口记，稍后继续查看",
    )

    fun savedLinkSnapshot(title: String) = NotificationDisplaySnapshot(
        shortText = "已收藏",
        primaryText = "已收藏到随口记",
        secondaryText = title,
        expandedText = title,
    )

    fun notification(
        display: NotificationDisplaySnapshot,
        actions: List<com.antgskds.calendarassistant.feature.notification.model.NotificationAction> = emptyList(),
        tapTarget: com.antgskds.calendarassistant.feature.notification.model.NotificationTapTarget? = null,
    ) = CapsuleDisplayModel(
        shortText = display.shortText,
        primaryText = display.primaryText,
        secondaryText = display.secondaryText,
        tertiaryText = display.tertiaryText,
        expandedText = display.expandedText,
        tapQuickMemoId = tapTarget?.takeIf {
            it.type == com.antgskds.calendarassistant.feature.notification.model.NotificationTapTargetType.QUICK_MEMO_DETAIL
        }?.payload?.get("quickMemoId"),
        tapOpensQuickMemoDetail = tapTarget?.type ==
            com.antgskds.calendarassistant.feature.notification.model.NotificationTapTargetType.QUICK_MEMO_DETAIL,
        actions = actions.map {
            com.antgskds.calendarassistant.feature.capsule.domain.CapsuleActionSpec(
                label = it.label, receiverAction = it.key, stringExtras = it.payload, openQuickMemoId = it.openQuickMemoId,
            )
        },
    )
}
