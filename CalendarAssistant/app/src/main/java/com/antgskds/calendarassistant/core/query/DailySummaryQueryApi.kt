package com.antgskds.calendarassistant.core.query

import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.WeatherData
import java.time.LocalDate

data class DailySummaryPayload(
    val targetDate: LocalDate,
    val title: String,
    val content: String,
    val events: List<MyEvent>
)

interface DailySummaryQueryApi {
    fun buildPayload(
        isMorning: Boolean,
        settings: MySettings,
        events: List<MyEvent>,
        weatherData: WeatherData?
    ): DailySummaryPayload?
}
