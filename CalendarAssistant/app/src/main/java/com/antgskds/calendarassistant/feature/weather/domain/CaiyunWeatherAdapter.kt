package com.antgskds.calendarassistant.feature.weather.domain

import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherAlertData
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherDailyForecast
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherData
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherHourlyForecast
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal object CaiyunWeatherAdapter {
    fun parse(root: JSONObject, location: WeatherLocation): WeatherData {
        ensureSuccess(root)
        val result = root.optJSONObject("result") ?: throw IllegalStateException("Caiyun missing result")
        val realtime = result.optJSONObject("realtime") ?: throw IllegalStateException("Caiyun missing realtime")
        val skycon = realtime.optString("skycon")
        val sky = mapSkycon(skycon)
        val wind = realtime.optJSONObject("wind")
        val precipitation = realtime.optJSONObject("precipitation")
            ?.optJSONObject("local")
            ?.optDoubleOrNull("intensity")
        val locationName = location.name.ifBlank {
            when (location.source) {
                "cached" -> "最近位置"
                "manual" -> "手动位置"
                else -> "当前位置"
            }
        }

        return WeatherData(
            temperature = formatNumber(realtime.optDoubleOrNull("temperature")),
            feelsLike = formatNumber(realtime.optDoubleOrNull("apparent_temperature")),
            text = sky.text,
            icon = sky.icon,
            windDir = wind?.optDoubleOrNull("direction")?.let(::windDirection).orEmpty(),
            windScale = wind?.optDoubleOrNull("speed")?.let(::windScale).orEmpty(),
            windSpeed = formatNumber(wind?.optDoubleOrNull("speed")),
            humidity = formatPercent(realtime.optDoubleOrNull("humidity")),
            precip = formatNumber(precipitation),
            pressure = formatNumber(realtime.optDoubleOrNull("pressure")?.div(100.0)),
            vis = formatNumber(realtime.optDoubleOrNull("visibility")),
            obsTime = epochSecondsToIso(root.optLongOrNull("server_time"), root.optInt("tzshift", 0)),
            city = locationName,
            locationId = location.locationId,
            locationName = locationName,
            adm1 = location.adm1,
            adm2 = location.adm2,
            country = location.country,
            latitude = location.latitude,
            longitude = location.longitude,
            locationSource = location.source,
            provider = WeatherApiAdapter.PROVIDER_CAIYUN,
            updateTime = System.currentTimeMillis(),
            hourlyForecast = parseHourly(result.optJSONObject("hourly")),
            dailyForecast = parseDaily(result.optJSONObject("daily")),
            alerts = parseAlerts(result.optJSONObject("alert")),
            attributions = listOf("彩云天气")
        )
    }

    private fun parseHourly(hourly: JSONObject?): List<WeatherHourlyForecast> {
        if (hourly == null) return emptyList()
        val temperatures = hourly.optJSONArray("temperature") ?: return emptyList()
        return buildList {
            for (index in 0 until temperatures.length()) {
                val temperature = temperatures.optJSONObject(index) ?: continue
                val skyValue = hourly.seriesObject("skycon", index)?.optString("value").orEmpty()
                val sky = mapSkycon(skyValue)
                val wind = hourly.seriesObject("wind", index)
                val precipitation = hourly.seriesObject("precipitation", index)
                add(
                    WeatherHourlyForecast(
                        fxTime = temperature.optString("datetime"),
                        temp = formatNumber(temperature.optDoubleOrNull("value")),
                        icon = sky.icon,
                        text = sky.text,
                        windDir = wind?.optDoubleOrNull("direction")?.let(::windDirection).orEmpty(),
                        windScale = wind?.optDoubleOrNull("speed")?.let(::windScale).orEmpty(),
                        windSpeed = formatNumber(wind?.optDoubleOrNull("speed")),
                        humidity = formatPercent(hourly.seriesObject("humidity", index)?.optDoubleOrNull("value")),
                        pop = formatNumber(precipitation?.optDoubleOrNull("probability"), decimals = 0),
                        precip = formatNumber(precipitation?.optDoubleOrNull("value")),
                        pressure = formatNumber(hourly.seriesObject("pressure", index)?.optDoubleOrNull("value")?.div(100.0)),
                        cloud = formatPercent(hourly.seriesObject("cloudrate", index)?.optDoubleOrNull("value"))
                    )
                )
            }
        }
    }

    private fun parseDaily(daily: JSONObject?): List<WeatherDailyForecast> {
        if (daily == null) return emptyList()
        val temperatures = daily.optJSONArray("temperature") ?: return emptyList()
        return buildList {
            for (index in 0 until temperatures.length()) {
                val temperature = temperatures.optJSONObject(index) ?: continue
                val daySky = mapSkycon(daily.seriesObject("skycon_08h_20h", index)?.optString("value").orEmpty())
                val nightSky = mapSkycon(daily.seriesObject("skycon_20h_32h", index)?.optString("value").orEmpty())
                val fallbackSky = mapSkycon(daily.seriesObject("skycon", index)?.optString("value").orEmpty())
                val dayWind = daily.seriesObject("wind_08h_20h", index)?.optJSONObject("avg")
                val nightWind = daily.seriesObject("wind_20h_32h", index)?.optJSONObject("avg")
                val humidity = daily.seriesObject("humidity", index)
                val precipitation = daily.seriesObject("precipitation", index)
                val ultraviolet = daily.optJSONObject("life_index")?.seriesObject("ultraviolet", index)
                add(
                    WeatherDailyForecast(
                        fxDate = temperature.optString("date").substringBefore('T'),
                        tempMax = formatNumber(temperature.optDoubleOrNull("max")),
                        tempMin = formatNumber(temperature.optDoubleOrNull("min")),
                        iconDay = daySky.icon.ifBlank { fallbackSky.icon },
                        textDay = daySky.text.ifBlank { fallbackSky.text },
                        iconNight = nightSky.icon.ifBlank { fallbackSky.icon },
                        textNight = nightSky.text.ifBlank { fallbackSky.text },
                        windDirDay = dayWind?.optDoubleOrNull("direction")?.let(::windDirection).orEmpty(),
                        windScaleDay = dayWind?.optDoubleOrNull("speed")?.let(::windScale).orEmpty(),
                        windDirNight = nightWind?.optDoubleOrNull("direction")?.let(::windDirection).orEmpty(),
                        windScaleNight = nightWind?.optDoubleOrNull("speed")?.let(::windScale).orEmpty(),
                        humidity = formatPercent(humidity?.optDoubleOrNull("avg")),
                        precip = formatNumber(precipitation?.optDoubleOrNull("avg")),
                        uvIndex = ultraviolet?.optString("index").orEmpty()
                    )
                )
            }
        }
    }

    private fun parseAlerts(alert: JSONObject?): List<WeatherAlertData> {
        val content = alert?.optJSONArray("content") ?: return emptyList()
        return buildList {
            for (index in 0 until content.length()) {
                val item = content.optJSONObject(index) ?: continue
                val title = item.optString("title")
                val description = item.optString("description")
                val status = item.optString("status")
                add(
                    WeatherAlertData(
                        id = item.firstNonBlank("alertId", "id", "code").ifBlank { "$title-${item.optLong("pubtimestamp")}" },
                        senderName = item.firstNonBlank("source", "province", "city", "county"),
                        eventName = extractEventName(title),
                        eventCode = item.optString("code"),
                        severity = severityFromTitle(title),
                        colorCode = colorFromTitle(title),
                        messageTypeCode = if (status.contains("解除") || title.contains("解除")) {
                            WeatherWarningText.MESSAGE_TYPE_CANCEL
                        } else {
                            WeatherWarningText.MESSAGE_TYPE_ALERT
                        },
                        issuedTime = epochSecondsToIso(item.optLongOrNull("pubtimestamp"), 8 * 60 * 60),
                        headline = title,
                        description = description,
                        instruction = item.firstNonBlank("instruction", "advice")
                    )
                )
            }
        }
    }

    private fun ensureSuccess(root: JSONObject) {
        val status = root.optString("status")
        if (status != "ok") {
            val detail = root.firstNonBlank("error", "message", "api_status").ifBlank { status }
            throw IllegalStateException("Caiyun error ${detail.ifBlank { "unknown" }}")
        }
    }

    private fun mapSkycon(value: String): Skycon {
        return when (value.uppercase(Locale.US)) {
            "CLEAR_DAY" -> Skycon("晴", "100")
            "CLEAR_NIGHT" -> Skycon("晴", "150")
            "PARTLY_CLOUDY_DAY" -> Skycon("多云", "101")
            "PARTLY_CLOUDY_NIGHT" -> Skycon("多云", "151")
            "CLOUDY" -> Skycon("多云", "102")
            "OVERCAST" -> Skycon("阴", "104")
            "LIGHT_HAZE" -> Skycon("轻度霾", "502")
            "MODERATE_HAZE" -> Skycon("中度霾", "502")
            "HEAVY_HAZE" -> Skycon("重度霾", "502")
            "LIGHT_RAIN" -> Skycon("小雨", "305")
            "MODERATE_RAIN" -> Skycon("中雨", "306")
            "HEAVY_RAIN" -> Skycon("大雨", "307")
            "STORM_RAIN" -> Skycon("暴雨", "310")
            "FOG" -> Skycon("雾", "501")
            "LIGHT_SNOW" -> Skycon("小雪", "400")
            "MODERATE_SNOW" -> Skycon("中雪", "401")
            "HEAVY_SNOW" -> Skycon("大雪", "402")
            "STORM_SNOW" -> Skycon("暴雪", "403")
            "DUST" -> Skycon("浮尘", "504")
            "SAND" -> Skycon("沙尘", "503")
            "WIND" -> Skycon("大风", "200")
            else -> Skycon(value, "")
        }
    }

    private fun windDirection(degrees: Double): String {
        val directions = arrayOf("北风", "北东北风", "东北风", "东东北风", "东风", "东东南风", "东南风", "南东南风", "南风", "南西南风", "西南风", "西西南风", "西风", "西西北风", "西北风", "北西北风")
        val normalized = ((degrees % 360) + 360) % 360
        return directions[((normalized + 11.25) / 22.5).toInt() % directions.size]
    }

    private fun windScale(speedKmPerHour: Double): String {
        val thresholds = doubleArrayOf(1.0, 6.0, 12.0, 20.0, 29.0, 39.0, 50.0, 62.0, 75.0, 89.0, 103.0, 118.0)
        return thresholds.indexOfFirst { speedKmPerHour < it }
            .let { if (it < 0) 12 else it }
            .toString()
    }

    private fun formatPercent(value: Double?): String {
        if (value == null) return ""
        val percent = if (value in 0.0..1.0) value * 100.0 else value
        return percent.roundToInt().toString()
    }

    private fun formatNumber(value: Double?, decimals: Int = 1): String {
        if (value == null || !value.isFinite()) return ""
        if (decimals == 0 || abs(value - value.roundToInt()) < 0.05) return value.roundToInt().toString()
        return String.format(Locale.US, "%.${decimals}f", value).trimEnd('0').trimEnd('.')
    }

    private fun epochSecondsToIso(value: Long?, offsetSeconds: Int): String {
        if (value == null || value <= 0L) return ""
        val safeOffset = offsetSeconds.coerceIn(-18 * 60 * 60, 18 * 60 * 60)
        return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
            Instant.ofEpochSecond(value).atOffset(ZoneOffset.ofTotalSeconds(safeOffset))
        )
    }

    private fun JSONObject.seriesObject(name: String, index: Int): JSONObject? {
        return optJSONArray(name)?.optJSONObject(index)
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return optDouble(name).takeIf { it.isFinite() }
    }

    private fun JSONObject.optLongOrNull(name: String): Long? {
        if (!has(name) || isNull(name)) return null
        return optLong(name).takeIf { it > 0L }
    }

    private fun JSONObject.firstNonBlank(vararg names: String): String {
        return names.firstNotNullOfOrNull { name -> optString(name).takeIf(String::isNotBlank) }.orEmpty()
    }

    private fun extractEventName(title: String): String {
        val types = listOf("雷暴大风", "雷雨大风", "短时强降雨", "强降雨", "暴雨", "大风", "台风", "暴雪", "寒潮", "低温", "霜冻", "冰冻", "道路结冰", "高温", "冰雹", "雷电", "强对流", "大雾", "霾", "沙尘", "森林火险", "山洪", "内涝")
        return types.firstOrNull(title::contains).orEmpty()
    }

    private fun colorFromTitle(title: String): String {
        return when {
            title.contains("红色") -> "red"
            title.contains("橙色") -> "orange"
            title.contains("黄色") -> "yellow"
            title.contains("蓝色") -> "blue"
            else -> ""
        }
    }

    private fun severityFromTitle(title: String): String {
        return when {
            title.contains("红色") -> "extreme"
            title.contains("橙色") -> "severe"
            title.contains("黄色") -> "moderate"
            title.contains("蓝色") -> "minor"
            else -> "unknown"
        }
    }

    private data class Skycon(val text: String, val icon: String)
}
