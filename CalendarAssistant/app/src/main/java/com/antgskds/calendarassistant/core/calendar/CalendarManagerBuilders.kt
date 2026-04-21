package com.antgskds.calendarassistant.core.calendar

import android.content.ContentResolver
import android.content.ContentValues
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.util.Log
import com.antgskds.calendarassistant.data.db.entity.EventInstanceEntity
import com.antgskds.calendarassistant.data.db.entity.EventMasterEntity
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.TimeNode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

internal object CalendarManagerBuilders {
    fun queryCalendars(
        contentResolver: ContentResolver,
        minAccessLevel: Int,
        visibleOnly: Boolean = false,
        syncEnabledOnly: Boolean = false,
        logTag: String
    ): List<CalendarManager.CalendarInfo> {
        val calendars = mutableListOf<CalendarManager.CalendarInfo>()
        val projection = arrayOf(
            Calendars._ID,
            Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.CALENDAR_ACCESS_LEVEL,
            Calendars.VISIBLE,
            Calendars.SYNC_EVENTS
        )

        val conditions = mutableListOf<String>()
        val args = mutableListOf<String>()

        conditions += "${Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        args += minAccessLevel.toString()

        if (visibleOnly) {
            conditions += "${Calendars.VISIBLE} = 1"
        }
        if (syncEnabledOnly) {
            conditions += "${Calendars.SYNC_EVENTS} = 1"
        }

        val selection = conditions.joinToString(" AND ")

        try {
            contentResolver.query(
                Calendars.CONTENT_URI,
                projection,
                selection,
                args.toTypedArray(),
                "${Calendars.ACCOUNT_NAME} COLLATE NOCASE ASC, ${Calendars.CALENDAR_DISPLAY_NAME} COLLATE NOCASE ASC"
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndexOrThrow(Calendars._ID)
                val displayNameIndex = cursor.getColumnIndex(Calendars.CALENDAR_DISPLAY_NAME)
                val accountNameIndex = cursor.getColumnIndex(Calendars.ACCOUNT_NAME)
                val accountTypeIndex = cursor.getColumnIndex(Calendars.ACCOUNT_TYPE)
                val accessLevelIndex = cursor.getColumnIndex(Calendars.CALENDAR_ACCESS_LEVEL)
                val visibleIndex = cursor.getColumnIndex(Calendars.VISIBLE)
                val syncEventsIndex = cursor.getColumnIndex(Calendars.SYNC_EVENTS)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val displayName = if (displayNameIndex >= 0) cursor.getString(displayNameIndex) else "日历 $id"
                    val accessLevel = if (accessLevelIndex >= 0) cursor.getInt(accessLevelIndex) else Calendars.CAL_ACCESS_NONE

                    calendars += CalendarManager.CalendarInfo(
                        id = id,
                        name = displayName,
                        accountName = if (accountNameIndex >= 0) cursor.getString(accountNameIndex) else null,
                        accountType = if (accountTypeIndex >= 0) cursor.getString(accountTypeIndex) else null,
                        accessLevel = accessLevel,
                        isVisible = visibleIndex >= 0 && cursor.getInt(visibleIndex) == 1,
                        syncEvents = syncEventsIndex >= 0 && cursor.getInt(syncEventsIndex) == 1,
                        isWritable = accessLevel >= Calendars.CAL_ACCESS_CONTRIBUTOR
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.e(logTag, "缺少日历读取权限", e)
            throw SecurityException("需要日历读取权限")
        } catch (e: Exception) {
            Log.e(logTag, "获取日历列表失败", e)
        }

        return calendars
    }

    fun buildEventContentValues(event: MyEvent, calendarId: Long): ContentValues {
        val values = ContentValues()
        values.put(Events.CALENDAR_ID, calendarId)
        values.put(Events.TITLE, event.title)
        values.put(Events.EVENT_LOCATION, event.location)
        values.put(Events.DESCRIPTION, event.description)

        val startMillis = getDateTimeMillis(event.startDate, event.startTime)
        val endMillis = getDateTimeMillis(event.endDate, event.endTime)
        values.put(Events.DTSTART, startMillis)
        values.put(Events.DTEND, endMillis)
        values.put(Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
        values.put(Events.ALL_DAY, 0)
        values.put(Events.EVENT_COLOR, event.color.hashCode())

        if (event.reminders.isNotEmpty()) {
            values.put(Events.HAS_ALARM, 1)
        }

        return values
    }

    fun buildRecurringEventContentValues(
        master: EventMasterEntity,
        instance: EventInstanceEntity,
        excludedStartTimes: List<Long>,
        calendarId: Long,
        systemZone: ZoneId,
        exDateFormatter: DateTimeFormatter
    ): ContentValues {
        val values = ContentValues()

        values.put(Events.CALENDAR_ID, calendarId)
        values.put(Events.TITLE, master.title)
        values.put(Events.EVENT_LOCATION, master.location)
        values.put(Events.DESCRIPTION, master.description)
        values.put(Events.DTSTART, instance.startTime)
        values.put(Events.DTEND, instance.endTime)
        values.put(Events.EVENT_TIMEZONE, systemZone.id)
        values.put(Events.ALL_DAY, 0)
        values.put(Events.EVENT_COLOR, master.colorArgb)

        val rrule = master.rrule?.trim().orEmpty()
        if (rrule.isNotBlank()) {
            values.put(Events.RRULE, rrule)
        }

        val exDate = buildExDate(excludedStartTimes, systemZone, exDateFormatter)
        if (!exDate.isNullOrBlank()) {
            values.put(Events.EXDATE, exDate)
        }

        if (master.remindersJson.isNotBlank() && master.remindersJson != "[]") {
            values.put(Events.HAS_ALARM, 1)
        }

        return values
    }

    fun buildCourseEventContentValues(
        course: Course,
        date: LocalDate,
        calendarId: Long,
        timeNodes: List<TimeNode>,
        managedEventMarker: String
    ): ContentValues {
        val values = ContentValues()

        val startNode = timeNodes.find { it.index == course.startNode }
        val endNode = timeNodes.find { it.index == course.endNode }
        val startTime = startNode?.startTime ?: "08:00"
        val endTime = endNode?.endTime ?: "09:00"

        values.put(Events.CALENDAR_ID, calendarId)
        values.put(Events.TITLE, course.name)
        values.put(Events.EVENT_LOCATION, course.location)

        val description = buildString {
            if (course.teacher.isNotBlank()) {
                append("教师: ${course.teacher}\n")
            }
            append("节次: 第${course.startNode}-${course.endNode}节")
            append(managedEventMarker)
        }
        values.put(Events.DESCRIPTION, description)

        val startDateTime = LocalDateTime.of(date, parseTime(startTime))
        val endDateTime = LocalDateTime.of(date, parseTime(endTime))
        values.put(Events.DTSTART, startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
        values.put(Events.DTEND, endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())

        values.put(Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
        values.put(Events.ALL_DAY, 0)
        values.put(Events.EVENT_COLOR, course.color.hashCode())

        return values
    }

    fun getRecurringLookAheadDays(recurringRule: String?): Long {
        if (recurringRule.isNullOrBlank()) return 120L

        val ruleMap = recurringRule
            .split(';')
            .mapNotNull { token ->
                val parts = token.split('=', limit = 2)
                if (parts.size == 2) parts[0].uppercase() to parts[1] else null
            }
            .toMap()

        val interval = ruleMap["INTERVAL"]?.toLongOrNull()?.coerceAtLeast(1L) ?: 1L
        return when (ruleMap["FREQ"]?.uppercase()) {
            "DAILY" -> max(60L, interval * 3L + 7L)
            "WEEKLY" -> max(120L, interval * 21L)
            "MONTHLY" -> max(730L, interval * 93L)
            "YEARLY" -> max(3660L, interval * 730L)
            else -> 365L
        }
    }

    private fun buildExDate(
        excludedStartTimes: List<Long>,
        systemZone: ZoneId,
        exDateFormatter: DateTimeFormatter
    ): String? {
        if (excludedStartTimes.isEmpty()) return null
        return excludedStartTimes
            .sorted()
            .joinToString(",") { millis ->
                Instant.ofEpochMilli(millis)
                    .atZone(systemZone)
                    .toLocalDateTime()
                    .format(exDateFormatter)
            }
    }

    private fun getDateTimeMillis(date: LocalDate, timeStr: String): Long {
        val time = parseTime(timeStr)
        return LocalDateTime.of(date, time)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }

    private fun parseTime(timeStr: String): LocalTime {
        return try {
            LocalTime.parse(timeStr)
        } catch (_: Exception) {
            LocalTime.of(9, 0)
        }
    }
}
