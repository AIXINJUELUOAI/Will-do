package com.antgskds.calendarassistant.feature.notification.bracelet

import com.antgskds.calendarassistant.feature.notification.model.NotificationSnapshot
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.schedule.domain.model.EventTags
import com.antgskds.calendarassistant.feature.schedule.domain.model.isCheckedIn
import com.antgskds.calendarassistant.feature.schedule.domain.model.isCompleted
import com.antgskds.calendarassistant.feature.schedule.domain.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.shared.query.DailySummaryPayload
import com.antgskds.calendarassistant.shared.util.stripSourceImageMarkers

data class BraceletNotificationContent(
    val title: String,
    val text: String
)

object BraceletNotificationFormatter {
    private const val MAX_TEXT_LENGTH = 18

    fun schedule(snapshot: NotificationSnapshot, event: Event?): BraceletNotificationContent {
        val important = event?.let(::extractScheduleImportantText)
            ?: extractImportantText(
                listOf(
                    snapshot.display.primaryText,
                    snapshot.display.expandedText,
                    snapshot.display.tertiaryText,
                    snapshot.display.secondaryText
                ).filterNotNull().joinToString(" ")
            )
            ?: snapshot.display.primaryText.takeIf { it.isNotBlank() }
            ?: snapshot.display.shortText

        return BraceletNotificationContent(
            title = "日程",
            text = compact(important)
        )
    }

    fun schedule(event: Event): BraceletNotificationContent {
        return BraceletNotificationContent(
            title = "日程",
            text = compact(extractScheduleImportantText(event))
        )
    }

    fun weatherWarning(title: String, content: String): BraceletNotificationContent {
        val text = extractWeatherText(title, content)
        return BraceletNotificationContent("天气", compact(text))
    }

    fun weatherForecast(title: String, content: String): BraceletNotificationContent {
        val text = extractWeatherText(title, content)
        return BraceletNotificationContent("天气", compact(text))
    }

    fun dailySummary(payload: DailySummaryPayload, isMorning: Boolean): BraceletNotificationContent {
        val firstLine = payload.compactLines.firstOrNull { it.isNotBlank() }
            ?: payload.fullLines.firstOrNull { it.isNotBlank() }
            ?: payload.content
        val prefix = if (isMorning) "今日" else "明日"
        val text = if (payload.eventCount > 0) {
            "$prefix${payload.eventCount}项 $firstLine"
        } else {
            "${prefix}无日程"
        }
        return BraceletNotificationContent("日程", compact(text))
    }

    fun quickMemo(text: String, failed: Boolean = false): BraceletNotificationContent {
        if (failed) return BraceletNotificationContent("随口记", "转写失败")
        val important = extractImportantText(text) ?: text
        return BraceletNotificationContent("随口记", compact(important))
    }

    private fun extractScheduleImportantText(event: Event): String {
        val payload = RuleMatchingEngine.resolvePayload(event)
        val fields = RuleMatchingEngine.splitFields(payload?.payload.orEmpty(), 5)
        val checked = event.isCheckedIn || event.isCompleted
        val byRule = when (payload?.ruleId ?: event.tag) {
            RuleMatchingEngine.RULE_PICKUP,
            EventTags.PICKUP -> firstNotBlank(
                RuleMatchingEngine.stripInstantCodeLabel(RuleMatchingEngine.RULE_PICKUP, fields.getOrNull(0).orEmpty()),
                fields.getOrNull(2),
                fields.getOrNull(1)
            )
            RuleMatchingEngine.RULE_FOOD,
            EventTags.FOOD -> firstNotBlank(
                RuleMatchingEngine.stripInstantCodeLabel(RuleMatchingEngine.RULE_FOOD, fields.getOrNull(0).orEmpty()),
                fields.getOrNull(1),
                event.title
            )
            RuleMatchingEngine.RULE_TRAIN,
            EventTags.TRAIN -> {
                val trainNo = fields.getOrNull(0).orEmpty()
                val gate = fields.getOrNull(1).orEmpty()
                val seat = fields.getOrNull(2).orEmpty()
                if (checked) firstNotBlank(joinCompact(trainNo, seat), seat, trainNo)
                else firstNotBlank(withSuffix(gate, "检票口"), trainNo, event.title)
            }
            RuleMatchingEngine.RULE_TAXI,
            EventTags.TAXI -> firstNotBlank(
                fields.getOrNull(2),
                joinCompact(fields.getOrNull(0), fields.getOrNull(1)),
                event.title
            )
            RuleMatchingEngine.RULE_FLIGHT,
            EventTags.FLIGHT -> {
                val flightNo = fields.getOrNull(0).orEmpty()
                val gate = fields.getOrNull(1).orEmpty()
                val seat = fields.getOrNull(2).orEmpty()
                if (checked) firstNotBlank(joinCompact(flightNo, seat), seat, flightNo)
                else firstNotBlank(withSuffix(gate, "登机口"), flightNo, event.title)
            }
            RuleMatchingEngine.RULE_TICKET,
            EventTags.TICKET -> firstNotBlank(
                RuleMatchingEngine.stripInstantCodeLabel(RuleMatchingEngine.RULE_TICKET, fields.getOrNull(0).orEmpty()),
                fields.getOrNull(1),
                event.title
            )
            RuleMatchingEngine.RULE_SENDER,
            EventTags.SENDER -> firstNotBlank(
                RuleMatchingEngine.stripInstantCodeLabel(RuleMatchingEngine.RULE_SENDER, fields.getOrNull(0).orEmpty()),
                fields.getOrNull(2),
                fields.getOrNull(1),
                event.title
            )
            RuleMatchingEngine.RULE_COURSE,
            EventTags.COURSE -> firstNotBlank(event.location, fields.getOrNull(1), event.title)
            else -> null
        }
        return byRule
            ?: extractImportantText("${event.title} ${event.location} ${stripSourceImageMarkers(event.description)}")
            ?: firstNotBlank(event.location, event.title)
            ?: "日程提醒"
    }

    private fun extractImportantText(raw: String): String? {
        val text = stripSourceImageMarkers(raw).replace('\n', ' ').trim()
        if (text.isBlank()) return null
        val patterns = listOf(
            Regex("[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤青藏川宁琼][A-Z][A-Z0-9]{5,6}"),
            Regex("(?:取餐码|取件码|取票码|寄件码|验证码|取餐号|取件号|柜号|桌号)\\s*[:：]?\\s*([A-Za-z0-9\\-]{2,12})"),
            Regex("(?:停车位|车位|座位|座位号|检票口|登机口|进站口)\\s*[:：]?\\s*([A-Za-z0-9\\-]{1,10})"),
            Regex("\\b[A-Z]\\d{1,4}\\b"),
            Regex("\\b\\d{1,2}车\\s*[A-Z0-9]{1,4}\\b")
        )
        patterns.forEach { pattern ->
            val match = pattern.find(text) ?: return@forEach
            return match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: match.value
        }
        return text.substringBefore('。').substringBefore('，').substringBefore(',').trim().ifBlank { null }
    }

    private fun extractWeatherText(title: String, content: String): String {
        val merged = "$title $content"
        val keyword = listOf("暴雨", "雷雨", "大风", "台风", "高温", "低温", "寒潮", "降温", "降雪", "冰雹", "大雾", "道路结冰")
            .firstOrNull { merged.contains(it) }
        if (keyword != null) return keyword + if (title.contains("预警") || content.contains("预警")) "预警" else "提醒"
        return title.ifBlank { content }.ifBlank { "天气提醒" }
    }

    private fun compact(value: String): String {
        val clean = value.replace(Regex("\\s+"), " ").trim()
        if (clean.length <= MAX_TEXT_LENGTH) return clean
        return clean.take(MAX_TEXT_LENGTH - 1) + "…"
    }

    private fun firstNotBlank(vararg values: String?): String? =
        values.firstOrNull { !it.isNullOrBlank() }?.trim()

    private fun joinCompact(vararg values: String?): String? {
        val parts = values.mapNotNull { it?.trim()?.takeIf(String::isNotBlank) }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    private fun withSuffix(value: String?, suffix: String): String? {
        val clean = value?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return if (clean.contains(suffix)) clean else "$clean$suffix"
    }
}
