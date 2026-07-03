package com.antgskds.calendarassistant.core.rule

import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.core.model.RecognitionDraft
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

object RegexScheduleRecognizer {
    private val templateTokenRegex = Regex("\\{([A-Za-z][A-Za-z0-9_]*)\\}")

    data class Result(
        val draft: RecognitionDraft,
        val rule: RegexScheduleRule,
    )

    fun analyze(
        text: String,
        rules: List<RegexScheduleRule>,
        defaultDurationMinutes: Int,
        now: LocalDateTime = LocalDateTime.now(),
        zoneId: ZoneId = ZoneId.systemDefault(),
    ): List<Result> {
        val cleanText = text.trim()
        if (cleanText.isBlank()) return emptyList()

        return rules.asSequence()
            .withIndex()
            .filter { it.value.enabled && it.value.pattern.isNotBlank() }
            .mapNotNull { indexed ->
                runCatching { matchRule(cleanText, indexed.value, defaultDurationMinutes, now, zoneId) }
                    .getOrNull()
                    ?.let { indexed.index to it }
            }
            .sortedWith(compareBy<Pair<Int, Result>>({ tagPriority(it.second.draft.tag) }, { rulePriority(it.second.rule) }, { it.first }))
            .map { it.second }
            .toList()
    }

    private fun tagPriority(tag: String): Int {
        return when (tag.trim().lowercase()) {
            EventTags.TRAIN,
            EventTags.FLIGHT,
            EventTags.TAXI,
            EventTags.PICKUP,
            EventTags.FOOD,
            EventTags.TICKET,
            EventTags.SENDER,
            EventTags.COURSE -> 0
            else -> 1
        }
    }

    private fun rulePriority(rule: RegexScheduleRule): Int {
        val id = rule.id.lowercase()
        return when {
            id.endsWith("_detail") || id.endsWith("_order") -> 0
            rule.locationTemplate.isNotBlank() || rule.locationGroup.isNotBlank() -> 1
            else -> 2
        }
    }

    private fun matchRule(
        text: String,
        rule: RegexScheduleRule,
        defaultDurationMinutes: Int,
        now: LocalDateTime,
        zoneId: ZoneId,
    ): Result? {
        val regex = runCatching { Regex(rule.pattern, setOf(RegexOption.IGNORE_CASE)) }.getOrNull() ?: return null
        val match = regex.find(text) ?: return null
        val dateText = match.group(rule.dateGroup).orEmpty()
        val timeText = match.group(rule.timeGroup).orEmpty()
        val date = parseDate(dateText, now.toLocalDate()) ?: if (rule.useCurrentTimeWhenMissing) now.toLocalDate() else return null
        val time = parseTime(timeText) ?: if (rule.useCurrentTimeWhenMissing) now.toLocalTime() else return null
        val rawTitle = match.group(rule.titleGroup).orEmpty().ifBlank { text }
        val title = applyTemplate(rule.titleTemplate, rawTitle, match).cleanTitle()
        if (title.isBlank()) return null

        val start = date.atTime(time)
        val startTs = start.atZone(zoneId).toEpochSecond()
        val endTs = start.plusMinutes(defaultDurationMinutes.coerceAtLeast(1).toLong()).atZone(zoneId).toEpochSecond()
        val rawLocation = match.group(rule.locationGroup).orEmpty()
        val location = if (rule.locationTemplate.isBlank()) {
            rawLocation.trim()
        } else {
            applyTemplate(rule.locationTemplate, rawLocation, match).trim()
                .takeIf { it.hasMeaningfulContent() }
                .orEmpty()
        }
        val payload = applyTemplate(rule.descriptionTemplate, text, match).ifBlank { text }
        val draft = RecognitionDraft(
            title = title,
            startTS = startTs,
            endTS = endTs,
            location = location,
            description = RecognitionRuleCatalog.formatDescription(rule.tag, payload),
            timeZone = zoneId.id,
            tag = rule.tag.ifBlank { EventTags.GENERAL },
        )
        return Result(draft = draft, rule = rule)
    }

    private fun MatchResult.group(name: String): String? {
        if (name.isBlank()) return null
        return runCatching { (groups as? MatchNamedGroupCollection)?.get(name)?.value }.getOrNull()?.trim()
    }

    private fun applyTemplate(template: String, title: String, match: MatchResult): String {
        val base = template.ifBlank { "{title}" }
        return templateTokenRegex.replace(base) { token ->
            val name = token.groupValues.getOrNull(1).orEmpty()
            when (name) {
                "title" -> title
                "code" -> match.group(name).orEmpty().normalizeCode()
                else -> match.group(name).orEmpty()
            }
        }
    }

    private fun String.normalizeCode(): String {
        return trim()
            .trimStart('*')
            .replace(Regex("\\s+"), "")
            .trim(':', '：', ',', '，', '。', ';', '；', ' ')
    }

    private fun String.cleanTitle(): String {
        return trim()
            .trim('，', ',', '。', '；', ';', '：', ':')
            .replace(Regex("\\s+-\\s*"), "-")
            .replace(Regex("\\s+->\\s+"), " -> ")
            .replace(Regex("\\s+"), " ")
    }

    private fun String.hasMeaningfulContent(): Boolean {
        return any { it.isLetterOrDigit() }
    }

    private fun parseDate(raw: String, today: LocalDate): LocalDate? {
        val text = raw.trim().replace("号", "日")
        if (text.isBlank()) return null
        return when (text) {
            "今天" -> today
            "明天" -> today.plusDays(1)
            "后天" -> today.plusDays(2)
            else -> parseExplicitDate(text, today) ?: parseWeekday(text, today)
        }
    }

    private fun parseExplicitDate(text: String, today: LocalDate): LocalDate? {
        val full = Regex("(\\d{4})[-/.年](\\d{1,2})[-/.月](\\d{1,2})日?").find(text)
        if (full != null) {
            return runCatching {
                LocalDate.of(
                    full.groupValues[1].toInt(),
                    full.groupValues[2].toInt(),
                    full.groupValues[3].toInt()
                )
            }.getOrNull()
        }

        val monthDay = Regex("(\\d{1,2})月(\\d{1,2})日?").find(text)
            ?: Regex("(\\d{1,2})[-/.](\\d{1,2})").find(text)
        if (monthDay != null) {
            return runCatching {
                var date = LocalDate.of(today.year, monthDay.groupValues[1].toInt(), monthDay.groupValues[2].toInt())
                if (date.isBefore(today)) date = date.plusYears(1)
                date
            }.getOrNull()
        }
        return null
    }

    private fun parseWeekday(text: String, today: LocalDate): LocalDate? {
        val value = Regex("(?:周|星期)([一二三四五六日天])").find(text)?.groupValues?.getOrNull(1) ?: return null
        val dayOfWeek = when (value) {
            "一" -> DayOfWeek.MONDAY
            "二" -> DayOfWeek.TUESDAY
            "三" -> DayOfWeek.WEDNESDAY
            "四" -> DayOfWeek.THURSDAY
            "五" -> DayOfWeek.FRIDAY
            "六" -> DayOfWeek.SATURDAY
            "日", "天" -> DayOfWeek.SUNDAY
            else -> return null
        }
        var date = today.with(TemporalAdjusters.nextOrSame(dayOfWeek))
        if (date == today) date = date.plusWeeks(1)
        return date
    }

    private fun parseTime(raw: String): LocalTime? {
        val clean = raw.replace(" ", "").trim()
        if (clean.isBlank()) return null
        val period = Regex("^(上午|下午|晚上|中午|凌晨|早上)").find(clean)?.value.orEmpty()
        val numbers = Regex("\\d{1,2}").findAll(clean).map { it.value.toInt() }.toList()
        if (numbers.isEmpty()) return null
        var hour = numbers[0]
        val minute = when {
            clean.contains("半") -> 30
            numbers.size >= 2 -> numbers[1]
            else -> 0
        }
        hour = when (period) {
            "下午", "晚上" -> if (hour < 12) hour + 12 else hour
            "中午" -> if (hour in 1..10) hour + 12 else hour
            "凌晨" -> if (hour == 12) 0 else hour
            else -> hour
        }
        return runCatching { LocalTime.of(hour, minute) }.getOrNull()
    }
}
