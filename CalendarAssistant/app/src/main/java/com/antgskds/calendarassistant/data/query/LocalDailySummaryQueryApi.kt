package com.antgskds.calendarassistant.data.query

import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.core.center.ScheduleDisplayHelper
import com.antgskds.calendarassistant.core.query.DailySummaryPayload
import com.antgskds.calendarassistant.core.query.DailySummaryQueryApi
import com.antgskds.calendarassistant.feature.weather.domain.hasWeatherConfig
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.data.model.WeatherData
import com.antgskds.calendarassistant.shared.management.resource.notification.display.normal.ScheduleNormalDisplay
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

        val shortTitle = ScheduleNormalDisplay.dailySummaryShortTitle(isMorning)
        val weatherText = if (settings.hasWeatherConfig() && weatherData != null) {
            listOf("${weatherData.temperature}°C", weatherData.text)
                .filter { it.isNotBlank() }
                .joinToString(" ")
        } else {
            ""
        }
        val title = ScheduleNormalDisplay.dailySummaryTitle(shortTitle, weatherText)
        val titles = summaryItems.map { it.title.ifBlank { ScheduleNormalDisplay.unnamedEventTitle() } }
        val content = ScheduleNormalDisplay.dailySummaryContent(summaryItems.size, titles)
        val compactLines = if (titles.size <= 1) {
            titles
        } else {
            listOf(titles.first(), ScheduleNormalDisplay.dailySummaryMoreLine(titles.size - 1))
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
