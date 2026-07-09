package com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template

import com.antgskds.calendarassistant.service.capsule.CapsuleActionSpec
import com.antgskds.calendarassistant.service.capsule.CapsuleDisplayModel

object ScheduleActionLiveDisplay {
    fun actionItem(
        title: String,
        secondaryText: String?,
        expandedText: String?,
        tapOpensPickupList: Boolean = false,
        tapEventId: String? = null,
        action: CapsuleActionSpec? = null
    ): CapsuleDisplayModel {
        val headerTitle = clean(title) ?: "日程提醒"
        val secondary = clean(secondaryText)
        val expanded = clean(expandedText) ?: secondary
        return CapsuleDisplayModel(
            shortText = headerTitle,
            primaryText = headerTitle,
            secondaryText = secondary,
            expandedText = expanded,
            tapOpensPickupList = tapOpensPickupList,
            tapEventId = tapEventId,
            action = action
        )
    }

    private fun clean(value: String?): String? {
        val clean = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (clean.equals("null", ignoreCase = true)) null else clean
    }
}
