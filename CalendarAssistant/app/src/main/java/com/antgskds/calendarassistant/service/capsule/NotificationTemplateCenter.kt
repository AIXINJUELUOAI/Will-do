package com.antgskds.calendarassistant.service.capsule

import com.antgskds.calendarassistant.core.util.OsUtils
import com.antgskds.calendarassistant.core.weather.WeatherWarningText
import com.antgskds.calendarassistant.data.model.LiveNotificationTemplateMode
import com.antgskds.calendarassistant.data.model.WeatherAlertData
import com.antgskds.calendarassistant.data.model.WeatherRiskAlert
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

enum class NotificationTemplateMode {
    FULL_MULTILINE,
    COMPACT_TWO_LINE
}

object NotificationTemplateCenter {
    fun nativeCapsuleMode(templateMode: String = LiveNotificationTemplateMode.AUTO): NotificationTemplateMode {
        return when (LiveNotificationTemplateMode.normalize(templateMode)) {
            LiveNotificationTemplateMode.FULL -> NotificationTemplateMode.FULL_MULTILINE
            LiveNotificationTemplateMode.COMPACT -> NotificationTemplateMode.COMPACT_TWO_LINE
            else -> if (OsUtils.supportsNativeMultilineLiveNotification()) {
                NotificationTemplateMode.FULL_MULTILINE
            } else {
                NotificationTemplateMode.COMPACT_TWO_LINE
            }
        }
    }

    fun composeSchedule(
        title: String,
        time: String?,
        location: String?,
        description: String?,
        templateMode: String = LiveNotificationTemplateMode.AUTO,
        action: CapsuleActionSpec? = null
    ): CapsuleDisplayModel {
        val headerTitle = clean(title) ?: "日程提醒"
        val fullLines = cleanLines(time, location, description)
        val compactLines = scheduleCompactLines(time, location, description)
        return compose(
            headerTitle = headerTitle,
            shortText = headerTitle,
            fullBodyLines = fullLines,
            compactBodyLines = compactLines,
            templateMode = templateMode,
            action = action
        )
    }

    fun composeBody(
        headerTitle: String,
        shortText: String = headerTitle,
        fullBodyLines: List<String?>,
        compactBodyLines: List<String?> = fullBodyLines,
        templateMode: String = LiveNotificationTemplateMode.AUTO,
        tapOpensPickupList: Boolean = false,
        action: CapsuleActionSpec? = null
    ): CapsuleDisplayModel {
        return compose(
            headerTitle = clean(headerTitle) ?: "提醒",
            shortText = clean(shortText) ?: clean(headerTitle) ?: "提醒",
            fullBodyLines = cleanLines(*fullBodyLines.toTypedArray()),
            compactBodyLines = cleanLines(*compactBodyLines.toTypedArray()),
            templateMode = templateMode,
            tapOpensPickupList = tapOpensPickupList,
            action = action
        )
    }

    fun composeOfficialWeatherAlert(
        locationName: String,
        alert: WeatherAlertData,
        templateMode: String = LiveNotificationTemplateMode.AUTO
    ): CapsuleDisplayModel {
        val officialTitle = WeatherWarningText.officialTitle(alert)
        val location = compactLocationName(locationName)
        val headerTitle = joinParts(location, officialTitle) ?: officialTitle
        val timeLine = officialTimeLine(alert)
        val factLine = officialFactLine(alert)
        val description = clean(alert.description)
        val instruction = clean(alert.instruction)
        val sender = clean(alert.senderName)?.let { "${it}发布" }

        return compose(
            headerTitle = headerTitle,
            shortText = officialTitle.removeSuffix("预警").ifBlank { officialTitle },
            fullBodyLines = cleanLines(timeLine, sender, description, instruction),
            compactBodyLines = cleanLines(factLine, timeLine),
            templateMode = templateMode,
        )
    }

    fun composeWeatherRisk(
        locationName: String,
        risk: WeatherRiskAlert,
        templateMode: String = LiveNotificationTemplateMode.AUTO
    ): CapsuleDisplayModel {
        val title = clean(risk.title)?.removePrefix("天气风险提醒：") ?: "天气风险"
        val location = compactLocationName(locationName)
        val headerTitle = joinParts(location, riskHeaderTitle(title)) ?: riskHeaderTitle(title)
        val fullLines = risk.message.lineSequence().mapNotNull(::clean).toList()
            .ifEmpty { cleanLines(risk.weatherText) }
        val compactSummary = riskCompactSummary(risk)
        val compactAdvice = riskCompactAdvice(risk)

        return compose(
            headerTitle = headerTitle,
            shortText = riskHeaderTitle(title),
            fullBodyLines = fullLines,
            compactBodyLines = cleanLines(compactSummary, compactAdvice),
            templateMode = templateMode
        )
    }

    private fun compose(
        headerTitle: String,
        shortText: String,
        fullBodyLines: List<String>,
        compactBodyLines: List<String>,
        templateMode: String = LiveNotificationTemplateMode.AUTO,
        tapOpensPickupList: Boolean = false,
        action: CapsuleActionSpec? = null
    ): CapsuleDisplayModel {
        val mode = nativeCapsuleMode(templateMode)
        val bodyLines = if (mode == NotificationTemplateMode.COMPACT_TWO_LINE) {
            compactBodyLines
        } else {
            fullBodyLines
        }.filterNot { it == headerTitle }

        val expandedLines = if (mode == NotificationTemplateMode.COMPACT_TWO_LINE) {
            compactBodyLines
        } else {
            fullBodyLines
        }.filterNot { it == headerTitle }
        val fallbackLines = if (bodyLines.isNotEmpty()) bodyLines else expandedLines
        return CapsuleDisplayModel(
            shortText = shortText,
            primaryText = headerTitle,
            secondaryText = fallbackLines.getOrNull(0),
            tertiaryText = if (mode == NotificationTemplateMode.COMPACT_TWO_LINE) null else fallbackLines.getOrNull(1),
            expandedText = expandedLines.joinToString("\n").ifBlank { fallbackLines.joinToString("\n") }.ifBlank { null },
            tapOpensPickupList = tapOpensPickupList,
            action = action
        )
    }

    private fun officialFactLine(alert: WeatherAlertData): String? {
        val text = listOf(alert.description, alert.headline).joinToString("。")
        if (text.contains("解除") || text.contains("取消")) {
            return firstSentenceContaining(text, "解除", "取消", "移出") ?: "预警已解除"
        }
        val normalized = normalizeWeatherText(text)
        val patterns = listOf(
            Regex("\\d+(?:-\\d+)?小时(?:内|后)?[^，。；\\n]{0,20}(?:降水|降雨|雷雨|阵雨)[^，。；\\n]{0,18}"),
            Regex("未来\\d+小时[^，。；\\n]{0,24}(?:阵风|风力)[^，。；\\n]{0,18}"),
            Regex("(?:阵风|风力)\\d+[～~\\-]\\d+级"),
            Regex("\\d+[～~\\-]\\d+毫米(?:降水|降雨)?"),
            Regex("地质灾害[^，。；\\n]{0,16}风险(?:较高|高|很高)"),
            Regex("最高气温[^，。；\\n]{0,12}\\d+℃")
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(normalized)?.value?.let(::trimFact)
        } ?: firstSentenceContaining(normalized, "风险", "降水", "阵风", "高温")
    }

    private fun officialTimeLine(alert: WeatherAlertData): String? {
        return when {
            alert.expireTime.isNotBlank() -> "持续至${formatAlertTime(alert.expireTime)}"
            alert.onsetTime.isNotBlank() -> "${formatAlertTime(alert.onsetTime)}起生效"
            alert.effectiveTime.isNotBlank() -> "${formatAlertTime(alert.effectiveTime)}起生效"
            alert.issuedTime.isNotBlank() -> "${formatAlertTime(alert.issuedTime)}发布"
            else -> null
        }
    }

    private fun riskCompactSummary(risk: WeatherRiskAlert): String {
        val title = clean(risk.title)?.removePrefix("天气风险提醒：") ?: "天气变化"
        val lead = riskLeadText(risk.fxTime, risk.message)
        val weather = riskPhenomenon(title, risk.weatherText)
        val probability = Regex("降水概率\\s*(\\d+)%").find(risk.message)?.groupValues?.getOrNull(1)
        val probabilityText = if (shouldAttachPrecipitationProbability(weather)) {
            probability?.let { "，降水概率$it%" } ?: ""
        } else {
            ""
        }
        return if (lead == "即将") {
            "即将出现$weather$probabilityText"
        } else {
            "$lead${riskLeadVerb(weather)}$weather$probabilityText"
        }
    }

    private fun riskCompactAdvice(risk: WeatherRiskAlert): String {
        return riskAdviceForText(risk.title)
            ?: riskAdviceForText(risk.weatherText)
            ?: riskAdviceForText(risk.message)
            ?: risk.message.lineSequence().mapNotNull(::clean).lastOrNull()
            ?: "建议留意天气变化"
    }

    private fun riskPhenomenon(title: String, weatherText: String): String {
        val cleanTitle = title.removeSuffix("风险").removeSuffix("提醒")
        return when {
            cleanTitle.isNotBlank() && cleanTitle != "天气变化" -> cleanTitle
            weatherText.contains("小雨") -> "小雨"
            weatherText.contains("中雨") -> "中雨"
            weatherText.contains("大雨") || weatherText.contains("暴雨") -> "强降雨"
            weatherText.contains("雨") -> "降雨"
            weatherText.contains("雪") -> "降雪"
            weatherText.contains("高温") -> "高温"
            weatherText.contains("风") -> "大风"
            weatherText.contains("雾") -> "大雾"
            weatherText.contains("霾") || weatherText.contains("沙") || weatherText.contains("尘") -> "空气污染"
            else -> "天气变化"
        }
    }

    private fun riskLeadVerb(weather: String): String {
        return if (weather.contains("高温") || weather.contains("大风") || weather.contains("雾") || weather.contains("污染")) {
            "出现"
        } else {
            "有"
        }
    }

    private fun shouldAttachPrecipitationProbability(weather: String): Boolean {
        return weather.contains("雨") || weather.contains("雪")
    }

    private fun riskAdviceForText(value: String): String? {
        return when {
            value.contains("雨") -> "建议带伞，注意路滑和延误"
            value.contains("雷") || value.contains("强对流") || value.contains("冰雹") -> "建议减少户外，远离树下高处"
            value.contains("高温") -> "建议多喝水，少晒太阳"
            value.contains("雪") || value.contains("结冰") || value.contains("冻雨") -> "建议慢点走，预留出行时间"
            value.contains("风") || value.contains("台风") -> "建议收好窗边和阳台物品"
            value.contains("雾") || value.contains("霾") || value.contains("沙") || value.contains("尘") -> "建议戴好口罩再出门"
            else -> null
        }
    }

    private fun riskLeadText(fxTime: String, message: String): String {
        val parsed = runCatching { OffsetDateTime.parse(fxTime) }.getOrNull()
        if (parsed != null) {
            val minutes = Duration.between(OffsetDateTime.now(parsed.offset), parsed).toMinutes()
            return when {
                minutes <= 30 -> "即将"
                minutes < 90 -> "预计约1小时后"
                minutes < 24 * 60 -> "预计约${(minutes / 60.0).roundToInt().coerceAtLeast(1)}小时后"
                else -> "预计${parsed.toLocalDate()}"
            }
        }
        return Regex("约?\\d+小时后|即将").find(message)?.value?.let {
            when {
                it == "即将" -> "即将"
                it.startsWith("预计") -> it
                else -> "预计$it"
            }
        } ?: "预计"
    }

    private fun riskHeaderTitle(title: String): String {
        return when {
            title.contains("风险") -> title
            title.contains("提醒") -> title
            else -> "${title}风险"
        }
    }

    private fun scheduleCompactLines(time: String?, location: String?, description: String?): List<String> {
        val cleanDescription = clean(description)
        val cleanLocation = clean(location)
        val cleanTime = clean(time)
        return when {
            cleanDescription != null && cleanLocation != null -> listOf(cleanDescription, cleanLocation)
            cleanDescription != null -> listOf(cleanDescription)
            cleanLocation != null -> listOf(cleanLocation)
            cleanTime != null -> listOf(cleanTime)
            else -> emptyList()
        }
    }

    private fun firstSentenceContaining(text: String, vararg markers: String): String? {
        return text.split('。', '；', '\n')
            .mapNotNull(::clean)
            .firstOrNull { sentence -> markers.any { sentence.contains(it) } }
            ?.let(::trimFact)
    }

    private fun formatAlertTime(value: String): String {
        val parsed = try {
            OffsetDateTime.parse(value)
        } catch (_: DateTimeParseException) {
            return value
        }
        val time = parsed.format(DateTimeFormatter.ofPattern("HH:mm"))
        val today = OffsetDateTime.now(parsed.offset).toLocalDate()
        return when (parsed.toLocalDate()) {
            today -> time
            today.plusDays(1) -> "明日$time"
            else -> parsed.toLocalDate().toString() + " $time"
        }
    }

    private fun compactLocationName(value: String): String? {
        val clean = clean(value) ?: return null
        return clean.split(' ', '·').lastOrNull()?.takeIf { it.isNotBlank() } ?: clean
    }

    private fun normalizeWeatherText(value: String): String {
        return value
            .replace("～", "-")
            .replace("--", "-")
            .replace("将出现", "将出现")
    }

    private fun trimFact(value: String): String {
        val clean = value.trim().trim('，', '。', '；')
        return if (clean.length > 32) clean.take(31) + "..." else clean
    }

    private fun cleanLines(vararg values: String?): List<String> {
        return values.mapNotNull(::clean).distinct()
    }

    private fun joinParts(vararg values: String?): String? {
        return cleanLines(*values).joinToString(" · ").ifBlank { null }
    }

    private fun clean(value: String?): String? {
        val clean = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (clean.equals("null", ignoreCase = true)) null else clean
    }
}
