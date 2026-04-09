package com.antgskds.calendarassistant.core.weather

import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.data.model.WeatherData

object WeatherIconMapper {
    fun iconRes(data: WeatherData): Int {
        val text = data.text.lowercase()
        val iconCode = data.icon.lowercase().trim()
        return when {
            // 雷雨
            text.contains("雷") -> R.drawable.ic_weather_thunderstorm

            // 冰雹
            text.contains("雹") -> R.drawable.ic_weather_hail

            // 雨夹雪/冻雨
            text.contains("雨夹雪") || text.contains("冻雨") || iconCode == "313" || iconCode == "314" || iconCode == "399" || iconCode == "404" || iconCode == "405" || iconCode == "406" -> R.drawable.ic_weather_sleet

            // 大雪/暴雪
            text.contains("暴雪") || text.contains("大雪") || iconCode == "402" || iconCode == "403" -> R.drawable.ic_weather_snow_heavy

            // 雪（小雪/中雪）
            text.contains("雪") -> R.drawable.ic_weather_snow

            // 大雨/暴雨
            text.contains("暴雨") || text.contains("大雨") || iconCode == "311" || iconCode == "312" || iconCode == "316" || iconCode == "317" || iconCode == "398" -> R.drawable.ic_weather_rain_heavy

            // 雨（小雨/中雨/阵雨）
            text.contains("雨") -> R.drawable.ic_weather_rain

            // 大风/台风
            text.contains("台风") || text.contains("飓风") || text.contains("龙卷") -> R.drawable.ic_weather_wind

            // 沙尘
            text.contains("沙") || text.contains("尘") -> R.drawable.ic_weather_haze

            // 霾
            text.contains("霾") -> R.drawable.ic_weather_haze

            // 雾
            text.contains("雾") -> R.drawable.ic_weather_fog

            // 阴
            text.contains("阴") -> R.drawable.ic_weather_overcast

            // 多云夜间
            text.contains("多云") && isNightIcon(iconCode) -> R.drawable.ic_weather_partly_cloudy_night

            // 多云白天
            text.contains("云") || text.contains("多云") -> R.drawable.ic_weather_partly_cloudy

            // 晴天夜间
            isNightIcon(iconCode) -> R.drawable.ic_weather_clear_night

            // 晴天白天
            text.contains("晴") || iconCode == "100" || iconCode == "150" -> R.drawable.ic_weather_sunny

            // 兜底
            else -> R.drawable.ic_weather_partly_cloudy
        }
    }

    private fun isNightIcon(iconCode: String): Boolean {
        return iconCode == "150" || iconCode == "151" || iconCode == "152" || iconCode == "153"
    }
}
