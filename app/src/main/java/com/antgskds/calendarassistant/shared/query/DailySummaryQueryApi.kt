package com.antgskds.calendarassistant.shared.query

import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherData
import java.time.LocalDate

data class DailySummaryPayload(
    val targetDate: LocalDate,
    val title: String,
    val shortTitle: String,
    val content: String,
    val eventCount: Int,
    val fullLines: List<String>,
    val compactLines: List<String>
)

interface DailySummaryQueryApi {
    fun buildPayload(
        isMorning: Boolean,
        settings: MySettings,
        events: List<Event>,
        weatherData: WeatherData?
    ): DailySummaryPayload?
}
