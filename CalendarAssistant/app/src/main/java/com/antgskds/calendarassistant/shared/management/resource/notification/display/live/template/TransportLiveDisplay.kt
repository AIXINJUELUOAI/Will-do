package com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template

import com.antgskds.calendarassistant.service.capsule.CapsuleActionSpec
import com.antgskds.calendarassistant.service.capsule.CapsuleDisplayModel

object TransportLiveDisplay {
    fun train(
        title: String,
        secondaryText: String?,
        action: CapsuleActionSpec? = null
    ): CapsuleDisplayModel {
        return transport(
            title = title,
            secondaryText = secondaryText,
            expandedText = secondaryText,
            action = action
        )
    }

    fun flight(
        title: String,
        secondaryText: String?,
        expandedText: String?,
        action: CapsuleActionSpec? = null
    ): CapsuleDisplayModel {
        return transport(
            title = title,
            secondaryText = secondaryText,
            expandedText = expandedText,
            action = action
        )
    }

    private fun transport(
        title: String,
        secondaryText: String?,
        expandedText: String?,
        action: CapsuleActionSpec?
    ): CapsuleDisplayModel {
        val headerTitle = clean(title) ?: "出行提醒"
        val secondary = clean(secondaryText)
        val expanded = clean(expandedText) ?: secondary
        return CapsuleDisplayModel(
            shortText = headerTitle,
            primaryText = headerTitle,
            secondaryText = secondary,
            expandedText = expanded,
            action = action
        )
    }

    private fun clean(value: String?): String? {
        val clean = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (clean.equals("null", ignoreCase = true)) null else clean
    }
}
