package com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template

import com.antgskds.calendarassistant.feature.weather.domain.WeatherWarningText
import com.antgskds.calendarassistant.data.model.WeatherAlertData
import com.antgskds.calendarassistant.data.model.WeatherRiskAlert
import com.antgskds.calendarassistant.service.capsule.CapsuleDisplayModel
import java.time.Duration
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import kotlin.math.roundToInt

internal object WeatherLiveDisplaySupport {
    enum class Mode {
        COMPACT,
        FULL
    }

    private const val WEATHER_SHORT_TITLE_MAX_CHARS = 6
    private const val WEATHER_PRIMARY_TITLE_MAX_CHARS = 11

    fun officialAlert(
        locationName: String,
        alert: WeatherAlertData,
        mode: Mode
    ): CapsuleDisplayModel {
        if (WeatherWarningText.isCancel(alert)) {
            return canceledOfficialWeatherAlert(locationName, alert, mode)
        }

        val officialTitle = WeatherWarningText.officialTitle(alert)
        val location = compactLocationName(locationName)
        val headerTitle = joinParts(location, officialTitle) ?: officialTitle
        val shortTitle = officialWeatherShortTitle(alert)
        val primaryTitle = compactPrimaryTitle(shortTitle, officialTitle)
        val timeLine = officialTimeLine(alert)
        val factLine = officialFactLine(alert)
        val description = clean(alert.description)
        val instruction = clean(alert.instruction)
        val sender = clean(alert.senderName)?.let { "${it}${officialSenderAction(alert)}" }

        return compose(
            headerTitle = headerTitle,
            primaryText = primaryTitle,
            shortText = shortTitle,
            fullBodyLines = cleanLines(location, timeLine, sender, description, instruction),
            compactBodyLines = cleanLines(factLine, timeLine),
            mode = mode
        )
    }

    fun risk(
        locationName: String,
        risk: WeatherRiskAlert,
        mode: Mode
    ): CapsuleDisplayModel {
        val title = clean(risk.title)?.removePrefix("天气风险提醒：") ?: "天气风险"
        val location = compactLocationName(locationName)
        val riskTitle = riskHeaderTitle(title)
        val headerTitle = joinParts(location, riskTitle) ?: riskTitle
        val fullLines = risk.message.lineSequence().mapNotNull(::clean).toList()
            .ifEmpty { cleanLines(risk.weatherText) }
        val compactSummary = riskCompactSummary(risk)
        val compactAdvice = riskCompactAdvice(risk)
        val shortTitle = compactWeatherTitle(riskTitle, WEATHER_SHORT_TITLE_MAX_CHARS, fallback = "天气风险")

        return compose(
            headerTitle = headerTitle,
            primaryText = compactPrimaryTitle(shortTitle, riskTimingTitle(risk)),
            shortText = shortTitle,
            fullBodyLines = cleanLines(location, *fullLines.toTypedArray()),
            compactBodyLines = cleanLines(compactSummary, compactAdvice),
            mode = mode
        )
    }

    private fun canceledOfficialWeatherAlert(
        locationName: String,
        alert: WeatherAlertData,
        mode: Mode
    ): CapsuleDisplayModel {
        val location = compactLocationName(locationName)
        val event = clean(alert.eventName) ?: WeatherWarningText.officialShortText(alert).removeSuffix("解除").ifBlank { "天气" }
        val color = WeatherWarningText.colorName(alert.colorCode)
        val primary = "${event}预警已解除"
        val shortTitle = compactWeatherTitle("${event}解除", WEATHER_SHORT_TITLE_MAX_CHARS, fallback = "预警解除")
        val primaryTitle = compactPrimaryTitle(shortTitle, WeatherWarningText.officialTitle(alert))
        val statusLine = canceledStatusLine(location, color, alert)
        val factLine = officialFactLine(alert)?.takeIf { it != primary }
        val fullTitle = joinParts(location, WeatherWarningText.officialTitle(alert))
        val description = clean(alert.description)
        val instruction = clean(alert.instruction)

        return compose(
            headerTitle = primary,
            primaryText = primaryTitle,
            shortText = shortTitle,
            fullBodyLines = cleanLines(statusLine, fullTitle, factLine, description, instruction),
            compactBodyLines = cleanLines(statusLine, factLine),
            mode = mode
        )
    }

    private fun compose(
        headerTitle: String,
        shortText: String,
        primaryText: String = headerTitle,
        fullBodyLines: List<String>,
        compactBodyLines: List<String>,
        mode: Mode
    ): CapsuleDisplayModel {
        val bodyLines = if (mode == Mode.COMPACT) {
            compactBodyLines
        } else {
            fullBodyLines
        }.filterNot { it == headerTitle }

        val expandedLines = if (mode == Mode.COMPACT) {
            compactBodyLines
        } else {
            fullBodyLines
        }.filterNot { it == headerTitle }
        val fallbackLines = if (bodyLines.isNotEmpty()) bodyLines else expandedLines
        return CapsuleDisplayModel(
            shortText = shortText,
            primaryText = primaryText,
            secondaryText = fallbackLines.getOrNull(0),
            tertiaryText = if (mode == Mode.COMPACT) null else fallbackLines.getOrNull(1),
            expandedText = expandedLines.joinToString("\n").ifBlank { fallbackLines.joinToString("\n") }.ifBlank { null }
        )
    }

    private fun officialWeatherShortTitle(alert: WeatherAlertData): String {
        val event = clean(alert.eventName)?.removeSuffix("预警")
        val rawTitle = when {
            event != null && WeatherWarningText.isCancel(alert) -> "${event}解除"
            event != null && WeatherWarningText.isUpdate(alert) -> "${event}更新"
            event != null -> "${event}预警"
            else -> WeatherWarningText.officialShortText(alert)
        }
        return compactWeatherTitle(rawTitle, WEATHER_SHORT_TITLE_MAX_CHARS, fallback = "天气")
    }

    private fun compactWeatherTitle(value: String?, maxChars: Int, fallback: String): String {
        val clean = clean(value) ?: fallback
        if (clean.length <= maxChars) return clean
        val candidates = listOf(
            clean.replace("短时强降雨", "强降雨"),
            clean.replace("低温雨雪冰冻", "冰冻"),
            clean.replace("农业气象风险", "农业气象"),
            clean.replace("地质灾害", "地灾"),
            clean.replace("森林火险", "火险"),
            clean.replace("草原火险", "火险"),
            clean.replace("雷暴大风", "雷雨"),
            clean.replace("雷雨大风", "雷雨")
        ).mapNotNull(::clean)
        return candidates.firstOrNull { it.length <= maxChars }
            ?: if (maxChars <= 3) clean.take(maxChars) else clean.take(maxChars - 3) + "..."
    }

    private fun compactPrimaryTitle(shortTitle: String, detailTitle: String?): String {
        val short = clean(shortTitle) ?: "提醒"
        val detail = compactTitleDetail(detailTitle, WEATHER_PRIMARY_TITLE_MAX_CHARS - short.length - 1)
        if (detail == null || detail == short) return short.take(WEATHER_PRIMARY_TITLE_MAX_CHARS)
        val title = "$short|$detail"
        return if (title.length <= WEATHER_PRIMARY_TITLE_MAX_CHARS) title else short.take(WEATHER_PRIMARY_TITLE_MAX_CHARS)
    }

    private fun compactTitleDetail(value: String?, maxChars: Int): String? {
        if (maxChars <= 0) return null
        val clean = clean(value) ?: return null
        val candidates = listOf(
            clean,
            clean.removeSuffix("预警解除") + if (clean.endsWith("预警解除")) "解除" else "",
            clean.removeSuffix("预警更新") + if (clean.endsWith("预警更新")) "更新" else "",
            clean.removeSuffix("预警"),
            clean.removeSuffix("预警解除"),
            clean.removeSuffix("预警更新"),
            clean.replace("低温雨雪冰冻", "冰冻")
                .replace("短时强降雨", "强降雨")
                .replace("农业气象风险", "农业气象")
                .removeSuffix("预警")
        ).mapNotNull(::clean).distinct()
        return candidates.firstOrNull { it.length <= maxChars } ?: clean.take(maxChars)
    }

    private fun canceledStatusLine(location: String?, color: String, alert: WeatherAlertData): String? {
        val time = when {
            alert.issuedTime.isNotBlank() -> "${formatAlertTime(alert.issuedTime)}解除"
            alert.effectiveTime.isNotBlank() -> "${formatAlertTime(alert.effectiveTime)}解除"
            else -> null
        }
        val level = color.takeIf { it.isNotBlank() }?.let { "${it}预警" }
        return cleanLines(time, location, level).joinToString(" · ").ifBlank { null }
    }

    private fun officialFactLine(alert: WeatherAlertData): String? {
        val text = listOf(alert.headline, alert.description, alert.instruction).joinToString("。")
        if (WeatherWarningText.isCancel(alert)) {
            return cancelFactLine(alert, text)
        }
        if (WeatherWarningText.isUpdate(alert)) {
            updateFactLine(text)?.let { return it }
        }
        val normalized = normalizeWeatherText(text)
        val patterns = listOf(
            Regex("\\d+(?:-\\d+)?小时(?:内|后)?[^，。；\\n]{0,20}(?:降水|降雨|雷雨|阵雨)[^，。；\\n]{0,18}"),
            Regex("未来\\d+小时[^，。；\\n]{0,24}(?:阵风|风力)[^，。；\\n]{0,18}"),
            Regex("(?:阵风|风力)\\d+[～~\\-]\\d+级"),
            Regex("\\d+[～~\\-]\\d+毫米(?:降水|降雨)?"),
            Regex("地质灾害[^，。；\\n]{0,16}风险(?:较高|高|很高)"),
            Regex("(?:山洪|内涝|洪水)[^，。；\\n]{0,18}风险(?:较高|高|很高)?"),
            Regex("(?:森林|草原)?火险[^，。；\\n]{0,16}(?:等级|风险)?(?:较高|高|很高|极高)?"),
            Regex("(?:空气|大气)?重污染[^，。；\\n]{0,18}(?:风险|天气)?"),
            Regex("农业气象[^，。；\\n]{0,20}风险[^，。；\\n]{0,18}"),
            Regex("最高气温[^，。；\\n]{0,12}\\d+℃"),
            Regex("最低气温[^，。；\\n]{0,12}-?\\d+℃"),
            Regex("(?:降温|降幅|气温下降)[^，。；\\n]{0,16}\\d+℃"),
            Regex("(?:霜冻|冰冻|道路结冰|冻雨|低温|寒潮|强降温)[^，。；\\n]{0,22}"),
            Regex("能见度[^，。；\\n]{0,12}\\d+(?:米|公里)"),
            Regex("(?:沙尘|扬沙|浮尘|霾|大雾)[^，。；\\n]{0,22}")
        )
        return patterns.firstNotNullOfOrNull { pattern ->
            pattern.find(normalized)?.value?.let(::trimFact)
        } ?: firstSentenceContaining(
            normalized,
            "风险",
            "降水",
            "阵风",
            "高温",
            "低温",
            "寒潮",
            "强降温",
            "霜冻",
            "冰冻",
            "结冰",
            "污染",
            "火险"
        )
    }

    private fun cancelFactLine(alert: WeatherAlertData, text: String): String {
        val event = clean(alert.eventName) ?: "预警"
        val reason = firstSentenceContaining(text, "移出", "减弱", "结束", "解除", "取消")
        val shortReason = reason?.let { sentence ->
            when {
                sentence.contains("移出") -> firstClauseContaining(sentence, "移出")
                sentence.contains("减弱") -> firstClauseContaining(sentence, "减弱")
                sentence.contains("结束") -> firstClauseContaining(sentence, "结束")
                sentence.contains("解除") -> firstClauseContaining(sentence, "解除")
                sentence.contains("取消") -> firstClauseContaining(sentence, "取消")
                else -> sentence
            }
        }
        return shortReason?.let { "$it，预警解除" } ?: "${event}预警已解除"
    }

    private fun updateFactLine(text: String): String? {
        val normalized = normalizeWeatherText(text)
        return when {
            normalized.contains("继续发布") -> "预警继续生效"
            normalized.contains("更新") -> "预警已更新"
            else -> null
        }
    }

    private fun officialTimeLine(alert: WeatherAlertData): String? {
        if (WeatherWarningText.isCancel(alert)) {
            return when {
                alert.issuedTime.isNotBlank() -> "${formatAlertTime(alert.issuedTime)}解除"
                alert.effectiveTime.isNotBlank() -> "${formatAlertTime(alert.effectiveTime)}解除"
                else -> null
            }
        }
        return when {
            alert.expireTime.isNotBlank() -> "持续至${formatAlertTime(alert.expireTime)}"
            alert.onsetTime.isNotBlank() -> "${formatAlertTime(alert.onsetTime)}起生效"
            alert.effectiveTime.isNotBlank() -> "${formatAlertTime(alert.effectiveTime)}起生效"
            alert.issuedTime.isNotBlank() -> "${formatAlertTime(alert.issuedTime)}发布"
            else -> null
        }
    }

    private fun officialSenderAction(alert: WeatherAlertData): String {
        return when {
            WeatherWarningText.isCancel(alert) -> "解除"
            WeatherWarningText.isUpdate(alert) -> "更新"
            else -> "发布"
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
            weatherText.contains("强降温") || weatherText.contains("寒潮") -> "强降温"
            weatherText.contains("低温") || weatherText.contains("寒冷") -> "低温"
            weatherText.contains("霜冻") || weatherText.contains("冰冻") || weatherText.contains("冻雨") || weatherText.contains("结冰") -> "冰冻"
            weatherText.contains("高温") -> "高温"
            weatherText.contains("风") -> "大风"
            weatherText.contains("雾") -> "大雾"
            weatherText.contains("霾") || weatherText.contains("沙") || weatherText.contains("尘") -> "空气污染"
            else -> "天气变化"
        }
    }

    private fun riskLeadVerb(weather: String): String {
        return if (
            weather.contains("高温") ||
            weather.contains("低温") ||
            weather.contains("降温") ||
            weather.contains("冰冻") ||
            weather.contains("大风") ||
            weather.contains("雾") ||
            weather.contains("污染")
        ) {
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
            value.contains("强降温") || value.contains("寒潮") -> "建议添衣保暖，留意温差变化"
            value.contains("低温") || value.contains("寒冷") -> "建议做好保暖，减少受寒时间"
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

    private fun riskTimingTitle(risk: WeatherRiskAlert): String? {
        val parsed = runCatching { OffsetDateTime.parse(risk.fxTime) }.getOrNull()
        if (parsed != null) {
            val minutes = Duration.between(OffsetDateTime.now(parsed.offset), parsed).toMinutes()
            return when {
                minutes <= 30 -> "即将出现"
                minutes < 90 -> "一小时后"
                minutes < 24 * 60 -> "${chineseHour((minutes / 60.0).roundToInt().coerceAtLeast(1))}小时后"
                else -> null
            }
        }
        if (risk.message.contains("即将")) return "即将出现"
        val hours = Regex("约?(\\d+)小时后").find(risk.message)?.groupValues?.getOrNull(1)?.toIntOrNull()
        return hours?.let { "${chineseHour(it)}小时后" }
    }

    private fun chineseHour(hour: Int): String {
        return when (hour.coerceAtLeast(1)) {
            1 -> "一"
            2 -> "两"
            3 -> "三"
            4 -> "四"
            5 -> "五"
            6 -> "六"
            7 -> "七"
            8 -> "八"
            9 -> "九"
            10 -> "十"
            else -> hour.toString()
        }
    }

    private fun firstSentenceContaining(text: String, vararg markers: String): String? {
        return text.split('。', '；', '\n')
            .mapNotNull(::clean)
            .firstOrNull { sentence -> markers.any { sentence.contains(it) } }
            ?.let(::trimFact)
    }

    private fun firstClauseContaining(text: String, marker: String): String? {
        return text.split('，', ',', '。', '；', '\n')
            .mapNotNull(::clean)
            .firstOrNull { it.contains(marker) }
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
            else -> parsed.toLocalDate().toString() + " " + time
        }
    }

    private fun compactLocationName(value: String): String? {
        val clean = clean(value) ?: return null
        if (clean == "当前位置" || clean == "最近位置" || clean == "手动位置") return null
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
