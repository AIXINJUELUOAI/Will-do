package com.antgskds.calendarassistant.feature.weather.ui.contract

import com.antgskds.calendarassistant.data.model.WeatherData

data class WeatherDetailUiState(
    val weatherData: WeatherData?,
    val hasAppBackground: Boolean,
    val miuiBlurEnabled: Boolean,
    val cardAlphaPercent: Int
)

sealed interface WeatherDetailUiAction {
    data object NavigateBack : WeatherDetailUiAction
}
