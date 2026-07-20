package com.antgskds.calendarassistant.feature.home.ui.contract

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherData
import java.time.LocalDate

data class HomePageUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val timeRefreshToken: Long = 0L,
    val revealedItemKey: String? = null,
    val courseScheduleItems: List<ScheduleDisplayItem> = emptyList(),
    val currentDateEvents: List<ScheduleDisplayItem> = emptyList(),
    val tomorrowEvents: List<ScheduleDisplayItem> = emptyList(),
    val settings: MySettings = MySettings(),
    val weatherData: WeatherData? = null,
)

sealed interface HomePageUiAction {
    data class SelectDate(val date: LocalDate) : HomePageUiAction
    data class RevealItem(val itemKey: String?) : HomePageUiAction
    data class DeleteItem(val item: ScheduleDisplayItem) : HomePageUiAction
    data class ArchiveItem(val item: ScheduleDisplayItem) : HomePageUiAction
}
