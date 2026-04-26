package com.antgskds.calendarassistant.calendar.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.antgskds.calendarassistant.calendar.helpers.CALDAV
import com.antgskds.calendarassistant.calendar.helpers.FLAG_ALL_DAY
import com.antgskds.calendarassistant.calendar.helpers.REGULAR_EVENT_TYPE_ID
import com.antgskds.calendarassistant.calendar.helpers.REMINDER_NOTIFICATION
import com.antgskds.calendarassistant.calendar.helpers.REMINDER_OFF
import com.antgskds.calendarassistant.calendar.helpers.STATE_PENDING
import com.antgskds.calendarassistant.calendar.helpers.SOURCE_SIMPLE_CALENDAR
import com.antgskds.calendarassistant.calendar.helpers.TAG_GENERAL
import com.antgskds.calendarassistant.calendar.helpers.TYPE_EVENT
import java.time.ZoneId

@Entity(tableName = "events", indices = [Index(value = ["id"], unique = true)])
data class Event(
    @PrimaryKey(autoGenerate = true) var id: Long?,
    @ColumnInfo(name = "start_ts") var startTS: Long = 0L,
    @ColumnInfo(name = "end_ts") var endTS: Long = 0L,
    @ColumnInfo(name = "title") var title: String = "",
    @ColumnInfo(name = "location") var location: String = "",
    @ColumnInfo(name = "description") var description: String = "",
    @ColumnInfo(name = "reminder_1_minutes") var reminder1Minutes: Int = REMINDER_OFF,
    @ColumnInfo(name = "reminder_2_minutes") var reminder2Minutes: Int = REMINDER_OFF,
    @ColumnInfo(name = "reminder_3_minutes") var reminder3Minutes: Int = REMINDER_OFF,
    @ColumnInfo(name = "reminder_1_type") var reminder1Type: Int = REMINDER_NOTIFICATION,
    @ColumnInfo(name = "reminder_2_type") var reminder2Type: Int = REMINDER_NOTIFICATION,
    @ColumnInfo(name = "reminder_3_type") var reminder3Type: Int = REMINDER_NOTIFICATION,
    @ColumnInfo(name = "rrule") var rrule: String = "",
    @ColumnInfo(name = "exdates") var exdates: List<String> = emptyList(),
    @ColumnInfo(name = "attendees") var attendees: List<Attendee> = emptyList(),
    @ColumnInfo(name = "import_id") var importId: String = "",
    @ColumnInfo(name = "time_zone") var timeZone: String = "",
    @ColumnInfo(name = "flags") var flags: Int = 0,
    @ColumnInfo(name = "event_type") var eventType: Long = REGULAR_EVENT_TYPE_ID,
    @ColumnInfo(name = "parent_id") var parentId: Long = 0,
    @ColumnInfo(name = "last_updated") var lastUpdated: Long = 0L,
    @ColumnInfo(name = "source") var source: String = SOURCE_SIMPLE_CALENDAR,
    @ColumnInfo(name = "availability") var availability: Int = 0,
    @ColumnInfo(name = "color") var color: Int = 0,
    @ColumnInfo(name = "type") var type: Int = TYPE_EVENT,
    @ColumnInfo(name = "state") var state: Int = STATE_PENDING,
    @ColumnInfo(name = "tag") var tag: String = TAG_GENERAL,
    @ColumnInfo(name = "archived_at") var archivedAt: Long? = null  // 秒级时间戳，null=未归档
) {
    fun getIsAllDay(): Boolean = flags and FLAG_ALL_DAY != 0

    fun getReminders(): List<Reminder> = listOf(
        Reminder(reminder1Minutes, reminder1Type),
        Reminder(reminder2Minutes, reminder2Type),
        Reminder(reminder3Minutes, reminder3Type)
    ).filter { it.minutes != REMINDER_OFF }

    fun getCalDAVEventId(): Long {
        return importId.split("-").lastOrNull()?.toLongOrNull() ?: 0L
    }

    fun getCalDAVCalendarId(): Int {
        return if (source.startsWith(CALDAV)) {
            source.split("-").lastOrNull()?.toIntOrNull() ?: 0
        } else {
            0
        }
    }

    fun getTimeZoneString(): String {
        return try {
            if (timeZone.isBlank()) ZoneId.systemDefault().id else ZoneId.of(timeZone).id
        } catch (_: Exception) {
            ZoneId.systemDefault().id
        }
    }

    val isArchived: Boolean get() = archivedAt != null
    val isRecurring: Boolean get() = rrule.isNotBlank() && parentId == 0L
    val isException: Boolean get() = parentId != 0L
}
