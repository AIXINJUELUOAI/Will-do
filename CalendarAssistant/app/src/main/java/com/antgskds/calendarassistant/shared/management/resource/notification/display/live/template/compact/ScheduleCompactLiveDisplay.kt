package com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.compact

import com.antgskds.calendarassistant.service.capsule.CapsuleActionSpec
import com.antgskds.calendarassistant.service.capsule.CapsuleDisplayModel
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.ScheduleLiveDisplaySupport

object ScheduleCompactLiveDisplay {
    fun general(
        title: String,
        time: String?,
        location: String?,
        description: String?,
        action: CapsuleActionSpec? = null
    ): CapsuleDisplayModel {
        return ScheduleLiveDisplaySupport.general(
            title = title,
            time = time,
            location = location,
            description = description,
            action = action,
            mode = ScheduleLiveDisplaySupport.Mode.COMPACT
        )
    }

    fun daily(
        title: String,
        shortTitle: String,
        fullLines: List<String?>,
        compactLines: List<String?>
    ): CapsuleDisplayModel {
        return ScheduleLiveDisplaySupport.daily(
            title = title,
            shortTitle = shortTitle,
            fullLines = fullLines,
            compactLines = compactLines,
            mode = ScheduleLiveDisplaySupport.Mode.COMPACT
        )
    }
}
