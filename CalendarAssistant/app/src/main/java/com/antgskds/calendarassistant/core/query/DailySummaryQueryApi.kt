package com.antgskds.calendarassistant.core.query

import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.WeatherData
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
