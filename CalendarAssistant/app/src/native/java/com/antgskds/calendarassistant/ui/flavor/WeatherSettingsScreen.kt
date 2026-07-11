package com.antgskds.calendarassistant.ui.flavor

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.ui.contract.WeatherSettingsUiState
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialWeatherSettingsScreen

@Composable
fun WeatherSettingsScreen(state: WeatherSettingsUiState, uiSize: Int = 2, onOpenDetail: () -> Unit, saveWeather: suspend (MySettings) -> Result<Unit>) {
    MaterialWeatherSettingsScreen(state, uiSize, onOpenDetail, saveWeather)
}
