package com.antgskds.calendarassistant.calendar.models

import androidx.compose.ui.graphics.Color
import com.antgskds.calendarassistant.calendar.helpers.REMINDER_OFF
import com.antgskds.calendarassistant.calendar.helpers.STATE_CHECKED_IN
import com.antgskds.calendarassistant.calendar.helpers.STATE_COMPLETED
import com.antgskds.calendarassistant.calendar.helpers.STATE_PENDING
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

// ── Tag 常量（统一定义，供 UI 和 Rule 使用）────────────────────────────
object EventTags {
    const val GENERAL = "general"
    const val PICKUP  = "pickup"
    const val FOOD    = "food"
    const val TRAIN   = "train"
    const val TAXI    = "taxi"
    const val FLIGHT  = "flight"
    const val TICKET  = "ticket"
    const val SENDER  = "sender"
    const val COURSE  = "course"
}

fun normalizeEventTag(tag: String?): String = tag?.trim()?.lowercase().orEmpty()

fun isRetiredNoteTag(tag: String?): Boolean {
    return when (normalizeEventTag(tag)) {
        "note", "便签" -> true
        else -> false
    }
}

fun inferEventTagFromDescription(description: String?, fallbackTag: String = EventTags.GENERAL): String {
    val header = extractDescriptionHeader(description)
    val headerTag = when (header?.trim()?.lowercase()) {
        "general", "日程" -> EventTags.GENERAL
        "train", "列车", "火车", "高铁" -> EventTags.TRAIN
        "flight", "航班", "飞机" -> EventTags.FLIGHT
        "taxi", "用车", "打车" -> EventTags.TAXI
        "pickup", "取件" -> EventTags.PICKUP
        "food", "取餐", "外卖" -> EventTags.FOOD
        "ticket", "取票" -> EventTags.TICKET
        "sender", "寄件" -> EventTags.SENDER
        "course", "课程" -> EventTags.COURSE
        else -> null
    }
    return headerTag ?: normalizeEventTag(fallbackTag).ifBlank { EventTags.GENERAL }
}

private fun extractDescriptionHeader(description: String?): String? {
    val clean = description?.trim().orEmpty()
    if (clean.isBlank()) return null

    val cnStart = clean.indexOf('【')
    val cnEnd = if (cnStart >= 0) clean.indexOf('】', cnStart + 1) else -1
    if (cnStart >= 0 && cnEnd > cnStart) {
        return clean.substring(cnStart + 1, cnEnd)
    }

    val enStart = clean.indexOf('[')
    val enEnd = if (enStart >= 0) clean.indexOf(']', enStart + 1) else -1
    if (enStart >= 0 && enEnd > enStart) {
        return clean.substring(enStart + 1, enEnd)
    }

    return null
}

// ── 时间相关 ──────────────────────────────────────────────────────────

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
private val DEFAULT_ZONE: ZoneId = ZoneId.systemDefault()

fun Event.zone(): ZoneId = try {
    if (timeZone.isBlank()) DEFAULT_ZONE else ZoneId.of(timeZone)
} catch (_: Exception) { DEFAULT_ZONE }

fun Event.startZdt(): ZonedDateTime = Instant.ofEpochSecond(startTS).atZone(zone())
fun Event.endZdt(): ZonedDateTime   = Instant.ofEpochSecond(endTS).atZone(zone())

val Event.startDate: LocalDate  get() = startZdt().toLocalDate()
val Event.endDate: LocalDate    get() = endZdt().toLocalDate()
val Event.startTime: String     get() = startZdt().toLocalTime().format(TIME_FMT)
val Event.endTime: String       get() = endZdt().toLocalTime().format(TIME_FMT)
val Event.startLocalTime: LocalTime get() = startZdt().toLocalTime()
val Event.endLocalTime: LocalTime   get() = endZdt().toLocalTime()

/** 毫秒级 lastModified，兼容旧 UI */
val Event.lastModifiedMillis: Long get() = lastUpdated * 1000L

/** 毫秒级 archivedAt */
val Event.archivedAtMillis: Long? get() = archivedAt?.let { it * 1000L }

/** 毫秒级 startTS */
val Event.startMillis: Long get() = startTS * 1000L

// ── 状态相关 ──────────────────────────────────────────────────────────

val Event.isCompleted: Boolean  get() = state == STATE_COMPLETED
val Event.isCheckedIn: Boolean  get() = state == STATE_CHECKED_IN
val Event.isPending: Boolean    get() = state == STATE_PENDING

// ── 颜色 ──────────────────────────────────────────────────────────────

val Event.composeColor: Color get() = Color(color)

// ── 提醒 ──────────────────────────────────────────────────────────────

/** 提醒列表（分钟），过滤掉 OFF 的 */
val Event.reminderMinutes: List<Int> get() = listOfNotNull(
    reminder1Minutes.takeIf { it != REMINDER_OFF },
    reminder2Minutes.takeIf { it != REMINDER_OFF },
    reminder3Minutes.takeIf { it != REMINDER_OFF }
)

// ── 重复事件便捷属性 ──────────────────────────────────────────────────

/** 字符串 id，供 UI key 使用 */
val Event.idString: String get() = (id ?: 0L).toString()

/** 是否是交通类事件 */
val Event.isTransit: Boolean get() = inferEventTagFromDescription(description, tag).let { it == EventTags.FLIGHT || it == EventTags.TRAIN }
val Event.isCourse: Boolean get() = inferEventTagFromDescription(description, tag) == EventTags.COURSE

// ── 构造辅助 ──────────────────────────────────────────────────────────

/** 从 LocalDate + "HH:mm" 构建秒级时间戳 */
fun toEpochSeconds(date: LocalDate, time: String, zone: ZoneId = DEFAULT_ZONE): Long {
    return try {
        val lt = LocalTime.parse(time, TIME_FMT)
        date.atTime(lt).atZone(zone).toEpochSecond()
    } catch (_: Exception) {
        date.atStartOfDay(zone).toEpochSecond()
    }
}

/** 从 LocalDate + LocalTime 构建秒级时间戳 */
fun toEpochSeconds(date: LocalDate, time: LocalTime, zone: ZoneId = DEFAULT_ZONE): Long {
    return date.atTime(time).atZone(zone).toEpochSecond()
}
