package com.antgskds.calendarassistant.feature.weather.domain

import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherDailyForecast
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class WeatherDateSummaryMapperTest {
    private val today = LocalDate.of(2026, 9, 14)
    private val data = WeatherData(
        temperature = "28", text = "晴", icon = "100",
        dailyForecast = listOf(
            WeatherDailyForecast(fxDate = "2026-09-16", tempMin = "-3", tempMax = "2", textDay = "雪", iconDay = "400"),
            WeatherDailyForecast(fxDate = "2026-09-14", tempMin = "20", tempMax = "30", textDay = "多云", iconDay = "101"),
            WeatherDailyForecast(fxDate = "2026-09-15", tempMin = "19", tempMax = "26", textDay = "阴", iconDay = "104"),
        ),
    )

    @Test fun `today uses current conditions instead of the daily forecast`() {
        val summary = WeatherDateSummaryMapper.forDate(data, today, today)!!
        assertEquals("28°C", summary.temperatureText)
        assertEquals("晴", summary.description)
        assertEquals("100", summary.icon)
    }

    @Test fun `future summaries match dates instead of forecast array positions`() {
        val summary = WeatherDateSummaryMapper.forDate(data, today.plusDays(1), today)!!
        assertEquals("19～26°C", summary.temperatureText)
        assertEquals("阴", summary.description)
        assertEquals("104", summary.icon)
        assertEquals("-3～2°C", WeatherDateSummaryMapper.forDate(data, today.plusDays(2), today)?.temperatureText)
    }

    @Test fun `historical and missing dates do not reuse current weather`() {
        assertNull(WeatherDateSummaryMapper.forDate(data, today.minusDays(1), today))
        assertNull(WeatherDateSummaryMapper.forDate(data, today.plusDays(3), today))
        assertNull(WeatherDateSummaryMapper.forDate(null, today, today))
    }

    @Test fun `incomplete observations and forecasts omit the entire summary`() {
        assertNull(WeatherDateSummaryMapper.forDate(data.copy(temperature = "--"), today, today))
        assertNull(WeatherDateSummaryMapper.forDate(data.copy(text = ""), today, today))
        val incomplete = data.copy(dailyForecast = listOf(
            WeatherDailyForecast(fxDate = "2026-09-15", tempMin = "19", textDay = "阴"),
        ))
        assertNull(WeatherDateSummaryMapper.forDate(incomplete, today.plusDays(1), today))
    }
}
