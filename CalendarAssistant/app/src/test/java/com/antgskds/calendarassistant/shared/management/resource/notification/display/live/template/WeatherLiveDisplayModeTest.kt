package com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template

import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherAlertData
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherRiskAlert
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.compact.WeatherCompactLiveDisplay
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.full.WeatherFullLiveDisplay
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherLiveDisplayModeTest {
    private val alert = WeatherAlertData(
        eventName = "暴雨预警",
        description = "未来两小时可能出现强降雨，请注意防范。"
    )
    private val risk = WeatherRiskAlert(
        title = "强降雨风险",
        weatherText = "大雨",
        message = "预计两小时后出现强降雨。"
    )

    @Test
    fun compactWeatherDisplaysAreMarkedCompact() {
        assertTrue(WeatherCompactLiveDisplay.officialAlert("武汉", alert).isCompact)
        assertTrue(WeatherCompactLiveDisplay.risk("武汉", risk).isCompact)
    }

    @Test
    fun fullWeatherDisplaysAreNotMarkedCompact() {
        assertFalse(WeatherFullLiveDisplay.officialAlert("武汉", alert).isCompact)
        assertFalse(WeatherFullLiveDisplay.risk("武汉", risk).isCompact)
    }
}
