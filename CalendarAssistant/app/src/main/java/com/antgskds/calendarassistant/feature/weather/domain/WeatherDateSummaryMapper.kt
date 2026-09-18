package com.antgskds.calendarassistant.feature.weather.domain

import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherData
import java.time.LocalDate

data class WeatherDateSummary(
    val temperatureText: String,
    val description: String,
    val icon: String,
)

/** 不按数组下标猜预报日期，不把今日实时温度套用到未来或历史日期。 */
object WeatherDateSummaryMapper {
    fun forDate(data: WeatherData?, date: LocalDate, today: LocalDate): WeatherDateSummary? {
        if (data == null || date.isBefore(today)) return null
        if (date == today) {
            val temperature = data.temperature.trim()
            val description = data.text.trim()
            if (temperature.toDoubleOrNull()?.isFinite() != true || description.isEmpty()) return null
            return WeatherDateSummary("${temperature}°C", description, data.icon)
        }
        val forecast = data.dailyForecast.firstOrNull { it.fxDate.trim() == date.toString() } ?: return null
        val low = forecast.tempMin.trim()
        val high = forecast.tempMax.trim()
        val description = forecast.textDay.trim()
        if (low.toDoubleOrNull()?.isFinite() != true || high.toDoubleOrNull()?.isFinite() != true || description.isEmpty()) return null
        return WeatherDateSummary("$low～${high}°C", description, forecast.iconDay)
    }
}
