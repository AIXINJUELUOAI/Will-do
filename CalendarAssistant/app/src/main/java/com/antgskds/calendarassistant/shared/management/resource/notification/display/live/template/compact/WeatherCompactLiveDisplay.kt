package com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.compact

import com.antgskds.calendarassistant.data.model.WeatherAlertData
import com.antgskds.calendarassistant.data.model.WeatherRiskAlert
import com.antgskds.calendarassistant.service.capsule.CapsuleDisplayModel
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.WeatherLiveDisplaySupport

object WeatherCompactLiveDisplay {
    fun officialAlert(locationName: String, alert: WeatherAlertData): CapsuleDisplayModel {
        return WeatherLiveDisplaySupport.officialAlert(
            locationName = locationName,
            alert = alert,
            mode = WeatherLiveDisplaySupport.Mode.COMPACT
        )
    }

    fun risk(locationName: String, risk: WeatherRiskAlert): CapsuleDisplayModel {
        return WeatherLiveDisplaySupport.risk(
            locationName = locationName,
            risk = risk,
            mode = WeatherLiveDisplaySupport.Mode.COMPACT
        )
    }
}
