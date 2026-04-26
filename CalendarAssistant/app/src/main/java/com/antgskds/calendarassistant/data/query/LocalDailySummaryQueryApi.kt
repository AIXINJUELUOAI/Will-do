package com.antgskds.calendarassistant.data.query

import com.antgskds.calendarassistant.core.query.DailySummaryPayload
import com.antgskds.calendarassistant.core.query.DailySummaryQueryApi
import com.antgskds.calendarassistant.core.weather.hasWeatherConfig
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.WeatherData
import java.time.LocalDate

class LocalDailySummaryQueryApi : DailySummaryQueryApi {
    override fun buildPayload(
        isMorning: Boolean,
        settings: MySettings,
        events: List<Event>,
        weatherData: WeatherData?
    ): DailySummaryPayload? {
        if (!settings.isDailySummaryEnabled) return null

        val targetDate = if (isMorning) LocalDate.now() else LocalDate.now().plusDays(1)
        val summaryEvents = events.filter { it.startDate == targetDate && it.tag != EventTags.NOTE }
        if (summaryEvents.isEmpty()) return null

        val titleBase = if (isMorning) "今日日程提醒" else "明日日程预告"
        val title = if (settings.hasWeatherConfig() && weatherData != null) {
            "$titleBase ${weatherData.temperature}°C ${weatherData.text}".trim()
        } else {
            titleBase
        }
        val content = "您有 ${summaryEvents.size} 个日程：${summaryEvents.joinToString("，") { it.title }}"

        return DailySummaryPayload(
            targetDate = targetDate,
            title = title,
            content = content,
            events = summaryEvents
        )
    }
}
