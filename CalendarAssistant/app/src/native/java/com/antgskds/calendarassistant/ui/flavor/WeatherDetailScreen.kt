package com.antgskds.calendarassistant.ui.flavor

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.ui.contract.WeatherDetailUiAction
import com.antgskds.calendarassistant.ui.contract.WeatherDetailUiState
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialWeatherDetailPage
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialWeatherDetailScreen

@Composable
fun WeatherDetailPageContent(state: WeatherDetailUiState, uiSize: Int = 2) {
    MaterialWeatherDetailPage(state = state, uiSize = uiSize)
}

@Composable
fun WeatherDetailScreenContent(
    state: WeatherDetailUiState,
    uiSize: Int = 2,
    onAction: (WeatherDetailUiAction) -> Unit
) {
    MaterialWeatherDetailScreen(state = state, uiSize = uiSize, onAction = onAction)
}
