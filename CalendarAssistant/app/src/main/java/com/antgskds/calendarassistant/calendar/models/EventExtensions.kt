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
    const val NOTE    = "note"
}

fun normalizeEventTag(tag: String?): String = tag?.trim()?.lowercase().orEmpty()

fun isNoteTag(tag: String?): Boolean = normalizeEventTag(tag) == EventTags.NOTE

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
val Event.isTransit: Boolean get() = tag == EventTags.FLIGHT || tag == EventTags.TRAIN
val Event.isCourse: Boolean get() = tag == EventTags.COURSE

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
