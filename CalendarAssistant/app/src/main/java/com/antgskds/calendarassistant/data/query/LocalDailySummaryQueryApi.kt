package com.antgskds.calendarassistant.data.query

import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.core.center.ScheduleDisplayHelper
import com.antgskds.calendarassistant.core.query.DailySummaryPayload
import com.antgskds.calendarassistant.core.query.DailySummaryQueryApi
import com.antgskds.calendarassistant.core.weather.hasWeatherConfig
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
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

        val nowSeconds = System.currentTimeMillis() / 1000L
        val targetDate = if (isMorning) LocalDate.now() else LocalDate.now().plusDays(1)
        val summaryItems = ScheduleDisplayHelper.buildDisplayItems(events, targetDate, targetDate)
            .filter { !it.isCompleted }
            .filter { !isMorning || it.endTS > nowSeconds }
            .sortedWith(compareBy<ScheduleDisplayItem> { it.startTS }.thenBy { it.title })
        if (summaryItems.isEmpty()) return null

        val shortTitle = if (isMorning) "今日提醒" else "明日预告"
        val weatherText = if (settings.hasWeatherConfig() && weatherData != null) {
            listOf("${weatherData.temperature}°C", weatherData.text)
                .filter { it.isNotBlank() }
                .joinToString(" ")
        } else {
            ""
        }
        val title = if (weatherText.isNotBlank()) "$shortTitle|$weatherText" else shortTitle
        val titles = summaryItems.map { it.title.ifBlank { "未命名日程" } }
        val content = "您有 ${summaryItems.size} 个日程：${titles.joinToString("，")}"
        val compactLines = if (titles.size <= 1) {
            titles
        } else {
            listOf(titles.first(), "以及其他 ${titles.size - 1} 个日程")
        }

        return DailySummaryPayload(
            targetDate = targetDate,
            title = title,
            shortTitle = shortTitle,
            content = content,
            eventCount = summaryItems.size,
            fullLines = titles,
            compactLines = compactLines
        )
    }
}
