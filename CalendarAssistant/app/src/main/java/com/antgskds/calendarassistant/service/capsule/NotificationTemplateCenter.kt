package com.antgskds.calendarassistant.service.capsule

import com.antgskds.calendarassistant.core.util.OsUtils
import com.antgskds.calendarassistant.data.model.LiveNotificationTemplateMode
import com.antgskds.calendarassistant.data.model.WeatherAlertData
import com.antgskds.calendarassistant.data.model.WeatherRiskAlert
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.ScheduleActionLiveDisplay
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.TransportLiveDisplay
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.compact.ScheduleCompactLiveDisplay
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.compact.WeatherCompactLiveDisplay
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.full.ScheduleFullLiveDisplay
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.full.WeatherFullLiveDisplay

enum class NotificationTemplateMode {
    FULL_MULTILINE,
    COMPACT_TWO_LINE
}

object NotificationTemplateCenter {
    fun nativeCapsuleMode(templateMode: String = LiveNotificationTemplateMode.AUTO): NotificationTemplateMode {
        return when (LiveNotificationTemplateMode.normalize(templateMode)) {
            LiveNotificationTemplateMode.FULL -> NotificationTemplateMode.FULL_MULTILINE
            LiveNotificationTemplateMode.COMPACT -> NotificationTemplateMode.COMPACT_TWO_LINE
            else -> if (OsUtils.supportsNativeMultilineLiveNotification()) {
                NotificationTemplateMode.FULL_MULTILINE
            } else {
                NotificationTemplateMode.COMPACT_TWO_LINE
            }
        }
    }

    fun composeSchedule(
        title: String,
        time: String?,
        location: String?,
        description: String?,
        templateMode: String = LiveNotificationTemplateMode.AUTO,
        action: CapsuleActionSpec? = null
    ): CapsuleDisplayModel {
        return when (nativeCapsuleMode(templateMode)) {
            NotificationTemplateMode.COMPACT_TWO_LINE -> ScheduleCompactLiveDisplay.general(
                title = title,
                time = time,
                location = location,
                description = description,
                action = action
            )
            NotificationTemplateMode.FULL_MULTILINE -> ScheduleFullLiveDisplay.general(
                title = title,
                time = time,
                location = location,
                description = description,
                action = action
            )
        }
    }

    fun composeDailySchedule(
        title: String,
        shortTitle: String,
        fullLines: List<String?>,
        compactLines: List<String?>,
        templateMode: String = LiveNotificationTemplateMode.AUTO
    ): CapsuleDisplayModel {
        return when (nativeCapsuleMode(templateMode)) {
            NotificationTemplateMode.COMPACT_TWO_LINE -> ScheduleCompactLiveDisplay.daily(
                title = title,
                shortTitle = shortTitle,
                fullLines = fullLines,
                compactLines = compactLines
            )
            NotificationTemplateMode.FULL_MULTILINE -> ScheduleFullLiveDisplay.daily(
                title = title,
                shortTitle = shortTitle,
                fullLines = fullLines,
                compactLines = compactLines
            )
        }
    }

    fun composeScheduleActionItem(
        title: String,
        secondaryText: String?,
        expandedText: String?,
        tapOpensPickupList: Boolean = false,
        tapEventId: String? = null,
        templateMode: String = LiveNotificationTemplateMode.AUTO,
        action: CapsuleActionSpec? = null
    ): CapsuleDisplayModel {
        return ScheduleActionLiveDisplay.actionItem(
            title = title,
            secondaryText = secondaryText,
            expandedText = expandedText,
            tapOpensPickupList = tapOpensPickupList,
            tapEventId = tapEventId,
            action = action
        )
    }

    fun composeTransportTrain(
        title: String,
        secondaryText: String?,
        templateMode: String = LiveNotificationTemplateMode.AUTO,
        action: CapsuleActionSpec? = null
    ): CapsuleDisplayModel {
        return TransportLiveDisplay.train(
            title = title,
            secondaryText = secondaryText,
            action = action
        )
    }

    fun composeTransportFlight(
        title: String,
        secondaryText: String?,
        expandedText: String?,
        templateMode: String = LiveNotificationTemplateMode.AUTO,
        action: CapsuleActionSpec? = null
    ): CapsuleDisplayModel {
        return TransportLiveDisplay.flight(
            title = title,
            secondaryText = secondaryText,
            expandedText = expandedText,
            action = action
        )
    }

    fun composeOfficialWeatherAlert(
        locationName: String,
        alert: WeatherAlertData,
        templateMode: String = LiveNotificationTemplateMode.AUTO
    ): CapsuleDisplayModel {
        return when (nativeCapsuleMode(templateMode)) {
            NotificationTemplateMode.COMPACT_TWO_LINE -> WeatherCompactLiveDisplay.officialAlert(locationName, alert)
            NotificationTemplateMode.FULL_MULTILINE -> WeatherFullLiveDisplay.officialAlert(locationName, alert)
        }
    }

    fun composeWeatherRisk(
        locationName: String,
        risk: WeatherRiskAlert,
        templateMode: String = LiveNotificationTemplateMode.AUTO
    ): CapsuleDisplayModel {
        return when (nativeCapsuleMode(templateMode)) {
            NotificationTemplateMode.COMPACT_TWO_LINE -> WeatherCompactLiveDisplay.risk(locationName, risk)
            NotificationTemplateMode.FULL_MULTILINE -> WeatherFullLiveDisplay.risk(locationName, risk)
        }
    }
}
