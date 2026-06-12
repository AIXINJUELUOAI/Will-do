package com.antgskds.calendarassistant.core.weather

import com.antgskds.calendarassistant.data.model.WeatherHourlyForecast
import com.antgskds.calendarassistant.data.model.WeatherRiskAlert
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.math.roundToInt

object WeatherRiskAnalyzer {
    fun analyze(hourly: List<WeatherHourlyForecast>, lookaheadHours: Int): List<WeatherRiskAlert> {
        val selectedByCategory = linkedMapOf<String, RiskCandidate>()
        val selectedHours = hourly.take(lookaheadHours.coerceIn(1, 168))
        selectedHours
            .forEachIndexed { index, hour ->
                val candidate = riskForHour(hour, index, selectedHours) ?: return@forEachIndexed
                selectedByCategory.putIfAbsent(candidate.category, candidate)
            }
        return selectedByCategory.values
            .sortedWith(compareBy<RiskCandidate> { it.index }.thenByDescending { it.severityScore })
            .map { it.alert }
    }

    private fun riskForHour(hour: WeatherHourlyForecast, index: Int, hourly: List<WeatherHourlyForecast>): RiskCandidate? {
        val text = hour.text
        val temp = hour.temp.toIntOrNull()
        val precip = hour.precip.toDoubleOrNull() ?: 0.0
        val pop = hour.pop.toIntOrNull() ?: 0
        val windScaleMax = Regex("\\d+").findAll(hour.windScale).mapNotNull { it.value.toIntOrNull() }.maxOrNull() ?: 0
        val temperatureDrop = temperatureDropBefore(hourly, index, temp)

        val type = when {
            text.contains("高温") || (temp != null && temp >= 35) -> RiskType(
                category = "heat",
                title = "高温",
                level = if (temp != null && temp >= 38) "high" else "medium",
                advice = "请减少长时间户外活动，注意补水和防暑降温。"
            )
            text.contains("寒潮") || text.contains("强降温") || temperatureDrop >= STRONG_COOLING_DROP_CELSIUS -> RiskType(
                category = "cold",
                title = if (text.contains("寒潮")) "寒潮" else "强降温",
                level = if (text.contains("寒潮") || temperatureDrop >= SEVERE_COOLING_DROP_CELSIUS) "high" else "medium",
                advice = "请及时添衣保暖，留意温差变化和出行影响。"
            )
            text.contains("低温") || text.contains("寒冷") || text.contains("霜冻") || text.contains("冰冻") || (temp != null && temp <= 0) -> RiskType(
                category = "cold",
                title = when {
                    text.contains("霜冻") -> "霜冻"
                    text.contains("冰冻") -> "冰冻"
                    text.contains("寒冷") -> "寒冷"
                    else -> "低温"
                },
                level = if (temp != null && temp <= -5) "high" else "medium",
                advice = "请做好保暖，注意老人儿童和清晨夜间低温。"
            )
            text.contains("雷") || text.contains("强对流") || text.contains("冰雹") || text.contains("雹") -> RiskType(
                category = "thunder",
                title = when {
                    text.contains("雷阵雨") -> "雷阵雨"
                    text.contains("冰雹") || text.contains("雹") -> "冰雹"
                    text.contains("强对流") -> "强对流"
                    else -> "雷电"
                },
                level = if (text.contains("冰雹") || text.contains("雹") || text.contains("强对流")) "high" else "medium",
                advice = "请减少户外活动，远离高处、树下和金属设施。"
            )
            text.contains("雪") || text.contains("冻雨") || text.contains("道路结冰") -> RiskType(
                category = "snow",
                title = when {
                    text.contains("暴雪") -> "暴雪"
                    text.contains("大雪") -> "大雪"
                    text.contains("中雪") -> "中雪"
                    text.contains("冻雨") -> "冻雨"
                    text.contains("道路结冰") -> "道路结冰"
                    else -> "降雪"
                },
                level = if (text.contains("暴雪") || text.contains("大雪") || text.contains("道路结冰")) "high" else "medium",
                advice = "请注意保暖和路面湿滑，出行预留更多时间。"
            )
            text.contains("雨") || precip >= 0.1 || pop >= 60 -> RiskType(
                category = "rain",
                title = rainTitle(text, precip),
                level = when {
                    text.contains("暴雨") || precip >= 7.0 -> "high"
                    text.contains("中雨") || text.contains("大雨") || precip >= 1.0 || pop >= 80 -> "medium"
                    else -> "low"
                },
                advice = "建议出门带伞，注意路面湿滑和通勤延误。"
            )
            text.contains("台风") || text.contains("大风") || windScaleMax >= 5 -> RiskType(
                category = "wind",
                title = if (text.contains("台风")) "台风" else "大风",
                level = if (text.contains("台风") || windScaleMax >= 6) "medium" else "low",
                advice = "请收好阳台物品，外出注意高空坠物和骑行安全。"
            )
            text.contains("雾") || text.contains("霾") || text.contains("沙") || text.contains("尘") -> RiskType(
                category = "visibility",
                title = when {
                    text.contains("霾") -> "霾"
                    text.contains("沙") || text.contains("尘") -> "沙尘"
                    else -> "雾"
                },
                level = "low",
                advice = "请注意能见度变化，敏感人群做好防护。"
            )
            else -> null
        } ?: return null

        val message = buildList {
            add("${leadTimeText(hour.fxTime, index)}可能出现${type.title}")
            detailLine(hour, windScaleMax)?.let(::add)
            add(type.advice)
        }.joinToString("\n")

        val alert = WeatherRiskAlert(
            id = "${type.category}_${hour.fxTime.ifBlank { index.toString() }}",
            title = type.title,
            level = type.level,
            fxTime = hour.fxTime,
            weatherText = text,
            message = message
        )
        return RiskCandidate(
            category = type.category,
            index = index,
            severityScore = severityScore(type.level),
            alert = alert
        )
    }

    private fun rainTitle(text: String, precip: Double): String {
        return when {
            text.contains("特大暴雨") -> "特大暴雨"
            text.contains("大暴雨") -> "大暴雨"
            text.contains("暴雨") || precip >= 7.0 -> "暴雨"
            text.contains("大雨") -> "大雨"
            text.contains("中雨") -> "中雨"
            text.contains("阵雨") -> "阵雨"
            text.contains("小雨") -> "小雨"
            text.contains("雨") -> text
            else -> "降雨"
        }
    }

    private fun temperatureDropBefore(hourly: List<WeatherHourlyForecast>, index: Int, temp: Int?): Int {
        if (temp == null || index <= 0) return 0
        val start = (index - COOLING_LOOKBACK_HOURS).coerceAtLeast(0)
        val previousMax = hourly.subList(start, index)
            .mapNotNull { it.temp.toIntOrNull() }
            .maxOrNull() ?: return 0
        return previousMax - temp
    }

    private fun detailLine(hour: WeatherHourlyForecast, windScaleMax: Int): String? {
        val details = buildList {
            if (hour.temp.isNotBlank()) add("气温 ${hour.temp}°C")
            if (hour.pop.isNotBlank()) add("降水概率 ${hour.pop}%")
            if (hour.precip.isNotBlank()) add("预计降水 ${hour.precip}mm")
            if (hour.windScale.isNotBlank() || windScaleMax > 0) add("风力 ${hour.windScale.ifBlank { windScaleMax.toString() }}级")
        }
        return details.takeIf { it.isNotEmpty() }?.joinToString("，")
    }

    private fun leadTimeText(fxTime: String, index: Int): String {
        val parsed = runCatching { OffsetDateTime.parse(fxTime) }.getOrNull()
        if (parsed != null) {
            val minutes = Duration.between(OffsetDateTime.now(parsed.offset), parsed).toMinutes()
            return when {
                minutes <= 30 -> "即将"
                minutes < 90 -> "约 1 小时后"
                else -> "约 ${(minutes / 60.0).roundToInt()} 小时后"
            }
        }
        return if (index <= 0) "即将" else "约 $index 小时后"
    }

    private fun severityScore(level: String): Int {
        return when (level) {
            "high" -> 3
            "medium" -> 2
            else -> 1
        }
    }

    private data class RiskType(
        val category: String,
        val title: String,
        val level: String,
        val advice: String
    )

    private data class RiskCandidate(
        val category: String,
        val index: Int,
        val severityScore: Int,
        val alert: WeatherRiskAlert
    )

    private const val COOLING_LOOKBACK_HOURS = 6
    private const val STRONG_COOLING_DROP_CELSIUS = 6
    private const val SEVERE_COOLING_DROP_CELSIUS = 10
}
