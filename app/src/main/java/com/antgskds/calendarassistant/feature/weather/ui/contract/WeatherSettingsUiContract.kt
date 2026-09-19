package com.antgskds.calendarassistant.feature.weather.ui.contract

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherData
import com.antgskds.calendarassistant.feature.weather.domain.WeatherCatalogProvince

data class WeatherSettingsUiState(
    val settings: MySettings,
    val weatherData: WeatherData?,
    val locationCatalog: List<WeatherCatalogProvince>
)
