package com.antgskds.calendarassistant.platform.widget.ui.contract

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherData
import com.antgskds.calendarassistant.platform.widget.model.WidgetScheduleSnapshot
import com.antgskds.calendarassistant.platform.widget.CourseWidgetSnapshot
import com.antgskds.calendarassistant.platform.widget.WidgetAppearanceConfig
import com.antgskds.calendarassistant.platform.widget.WidgetType

data class WidgetSettingsUiState(
    val settings: MySettings,
    val scheduleSnapshot: WidgetScheduleSnapshot,
    val courseSnapshot: CourseWidgetSnapshot,
    val weatherData: WeatherData?,
    val appearances: Map<WidgetType, WidgetAppearanceConfig>
)

sealed interface WidgetSettingsUiAction {
    data class UpdateAppearance(val type: WidgetType, val appearance: WidgetAppearanceConfig) : WidgetSettingsUiAction
}
