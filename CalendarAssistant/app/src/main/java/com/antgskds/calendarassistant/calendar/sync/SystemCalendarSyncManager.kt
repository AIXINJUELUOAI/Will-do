package com.antgskds.calendarassistant.calendar.sync

import android.accounts.Account
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import android.provider.CalendarContract.Reminders
import androidx.core.content.ContextCompat
import com.antgskds.calendarassistant.calendar.data.EventsDatabase
import com.antgskds.calendarassistant.calendar.helpers.CALDAV
import com.antgskds.calendarassistant.calendar.helpers.CalendarConfig
import com.antgskds.calendarassistant.calendar.helpers.FLAG_ALL_DAY
import com.antgskds.calendarassistant.calendar.helpers.REMINDER_EMAIL
import com.antgskds.calendarassistant.calendar.helpers.REMINDER_NOTIFICATION
import com.antgskds.calendarassistant.calendar.helpers.REMINDER_OFF
import com.antgskds.calendarassistant.calendar.helpers.SCHEDULE_CALDAV_REQUEST_CODE
import com.antgskds.calendarassistant.calendar.helpers.STATE_PENDING
import com.antgskds.calendarassistant.calendar.helpers.TAG_GENERAL
import com.antgskds.calendarassistant.calendar.models.CalDAVCalendar
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.EventType
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.calendar.models.isNoteTag
import com.antgskds.calendarassistant.calendar.receivers.CalDAVSyncReceiver
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class SystemCalendarSyncManager(private val context: Context) {
    private val appContext = context.applicationContext
    private val config = CalendarConfig.newInstance(appContext)
    private val db = EventsDatabase.getInstance(appContext)

    fun recheckCalDAVCalendars(scheduleNextCalDAVSync: Boolean) {
        if (!config.caldavSync || !hasCalendarPermission()) return
        refreshCalendars()
        if (scheduleNextCalDAVSync) {
            scheduleCalDAVSync(true)
        }
    }

    @SuppressLint("MissingPermission")
    fun refreshCalendars() {
        val calendars = getCalDAVCalendars(config.caldavSyncedCalendarIds)
        calendars.forEach { calendar ->
            val eventTypeId = ensureEventTypeForCalendar(calendar)
            fetchCalDAVCalendarEvents(calendar, eventTypeId)
        }
    }

    @SuppressLint("MissingPermission")
    fun getCalDAVCalendars(ids: String): ArrayList<CalDAVCalendar> {
        val calendars = ArrayList<CalDAVCalendar>()
        if (!hasCalendarPermission()) return calendars

        val projection = arrayOf(
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE,
            Calendars.OWNER_ACCOUNT,
            Calendars.CALENDAR_COLOR,
            Calendars.CALENDAR_ACCESS_LEVEL
        )

        val selection = if (ids.isBlank()) null else "${Calendars._ID} IN ($ids)"
        appContext.contentResolver.query(Calendars.CONTENT_URI, projection, selection, null, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Calendars._ID)
            val displayNameIndex = cursor.getColumnIndexOrThrow(Calendars.CALENDAR_DISPLAY_NAME)
            val accountNameIndex = cursor.getColumnIndexOrThrow(Calendars.ACCOUNT_NAME)
            val accountTypeIndex = cursor.getColumnIndexOrThrow(Calendars.ACCOUNT_TYPE)
            val ownerNameIndex = cursor.getColumnIndexOrThrow(Calendars.OWNER_ACCOUNT)
            val colorIndex = cursor.getColumnIndexOrThrow(Calendars.CALENDAR_COLOR)
            val accessLevelIndex = cursor.getColumnIndexOrThrow(Calendars.CALENDAR_ACCESS_LEVEL)

            while (cursor.moveToNext()) {
                calendars.add(
                    CalDAVCalendar(
                        id = cursor.getInt(idIndex),
                        displayName = cursor.getString(displayNameIndex) ?: "",
                        accountName = cursor.getString(accountNameIndex) ?: "",
                        accountType = cursor.getString(accountTypeIndex) ?: "",
                        ownerName = cursor.getString(ownerNameIndex) ?: "",
                        color = cursor.getInt(colorIndex),
                        accessLevel = cursor.getInt(accessLevelIndex)
                    )
                )
            }
        }

        return calendars
    }

    fun scheduleCalDAVSync(activate: Boolean) {
        val syncIntent = Intent(appContext, CalDAVSyncReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            SCHEDULE_CALDAV_REQUEST_CODE,
            syncIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)

        if (activate) {
            val syncCheckInterval = 2 * AlarmManager.INTERVAL_HOUR
            alarmManager.setRepeating(
                AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + syncCheckInterval,
                syncCheckInterval,
                pendingIntent
            )
        }
    }

    fun refreshCalDAVCalendars(ids: String, manual: Boolean) {
        val uri = CalendarContract.Calendars.CONTENT_URI
        val calendars = getCalDAVCalendars(ids)
        val accounts = calendars.map { Account(it.accountName, it.accountType) }.toSet()

        val extras = android.os.Bundle().apply {
            putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
            if (manual) {
                putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true)
            }
        }

        accounts.forEach { account ->
            ContentResolver.requestSync(account, uri.authority ?: CalendarContract.AUTHORITY, extras)
        }
    }

    @SuppressLint("MissingPermission")
    fun insertCalDAVEvent(event: Event): Event {
        if (isNoteTag(event.tag)) {
            android.util.Log.i("CalDAVSync", "insertCalDAVEvent skipped note-tag localId=${event.id} title=${event.title}")
            return event
        }
        if (!hasCalendarPermission()) {
            android.util.Log.w("CalDAVSync", "insertCalDAVEvent skipped no-calendar-permission localId=${event.id} title=${event.title}")
            return event
        }
        val calendarId = resolveCalendarId(event)
        if (calendarId == 0) {
            android.util.Log.w(
                "CalDAVSync",
                "insertCalDAVEvent skipped calendarId=0 localId=${event.id} title=${event.title} " +
                    "syncedIds=${config.caldavSyncedCalendarIds} lastUsed=${config.lastUsedCaldavCalendarId} source=${event.source}"
            )
            return event
        }

        android.util.Log.i(
            "CalDAVSync",
            "insertCalDAVEvent start localId=${event.id} title=${event.title} calendarId=$calendarId " +
                "recurring=${event.rrule.isNotBlank()} source=${event.source} importId=${event.importId}"
        )

        val values = buildEventContentValues(event, calendarId)
        val newUri = appContext.contentResolver.insert(Events.CONTENT_URI, values)
        if (newUri == null) {
            android.util.Log.w(
                "CalDAVSync",
                "insertCalDAVEvent failed-null-uri localId=${event.id} title=${event.title} calendarId=$calendarId rrule=${event.rrule}"
            )
            return event
        }
        val eventRemoteId = newUri.lastPathSegment?.toLongOrNull()
        if (eventRemoteId == null || eventRemoteId == 0L) {
            android.util.Log.w("CalDAVSync", "insertCalDAVEvent: bad remoteId=$eventRemoteId, uri=$newUri, title=${event.title}, rrule=${event.rrule}, " +
                "dtstart=${values.getAsLong("dtstart")}, dtend=${values.getAsLong("dtend")}, duration=${values.getAsString("duration")}")
            return event
        }

        val source = "$CALDAV-$calendarId"
        val importId = "$source-$eventRemoteId"
        val synced = event.copy(importId = importId, source = source)
        synced.id?.let { db.eventsDao().updateEventImportIdAndSource(importId, source, it) }

        android.util.Log.i(
            "CalDAVSync",
            "insertCalDAVEvent success localId=${event.id} remoteId=$eventRemoteId importId=$importId source=$source"
        )

        setupCalDAVEventReminders(synced)
        setupCalDAVEventAttendees(synced)
        return synced
    }

    @SuppressLint("MissingPermission")
    fun updateCalDAVEvent(event: Event): Event {
        if (isNoteTag(event.tag)) {
            android.util.Log.i("CalDAVSync", "updateCalDAVEvent skipped note-tag localId=${event.id} title=${event.title}")
            return event
        }
        if (!hasCalendarPermission()) {
            android.util.Log.w("CalDAVSync", "updateCalDAVEvent skipped no-calendar-permission localId=${event.id} title=${event.title}")
            return event
        }
        val eventRemoteId = event.getCalDAVEventId()
        if (eventRemoteId == 0L) {
            android.util.Log.i("CalDAVSync", "updateCalDAVEvent no-remote-id fallback-insert localId=${event.id} title=${event.title}")
            return insertCalDAVEvent(event)
        }

        val calendarId = resolveCalendarId(event)
        if (calendarId == 0) {
            android.util.Log.w(
                "CalDAVSync",
                "updateCalDAVEvent skipped calendarId=0 localId=${event.id} remoteId=$eventRemoteId title=${event.title} " +
                    "syncedIds=${config.caldavSyncedCalendarIds} lastUsed=${config.lastUsedCaldavCalendarId} source=${event.source}"
            )
            return event
        }

        android.util.Log.i(
            "CalDAVSync",
            "updateCalDAVEvent start localId=${event.id} remoteId=$eventRemoteId title=${event.title} calendarId=$calendarId recurring=${event.rrule.isNotBlank()}"
        )

        val values = buildEventContentValues(event, calendarId)
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventRemoteId)
        val rows = appContext.contentResolver.update(uri, values, null, null)
        android.util.Log.i(
            "CalDAVSync",
            "updateCalDAVEvent finish localId=${event.id} remoteId=$eventRemoteId rows=$rows"
        )
        setupCalDAVEventReminders(event)
        setupCalDAVEventAttendees(event)
        return event
    }

    @SuppressLint("MissingPermission")
    fun deleteCalDAVEvent(event: Event) {
        if (!hasCalendarPermission()) return
        val remoteId = event.getCalDAVEventId()
        if (remoteId == 0L) return
        val uri = ContentUris.withAppendedId(Events.CONTENT_URI, remoteId)
        appContext.contentResolver.delete(uri, null, null)
    }

    @SuppressLint("MissingPermission")
    fun insertEventRepeatException(parentEvent: Event, occurrenceTs: Long) {
        if (!hasCalendarPermission()) return

        val parentRemoteId = parentEvent.getCalDAVEventId()
        val calendarId = resolveCalendarId(parentEvent)
        if (parentRemoteId == 0L || calendarId == 0) return

        val startMillis = occurrenceTs * 1000L
        val durationMillis = (parentEvent.endTS - parentEvent.startTS) * 1000L
        val values = ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.DTSTART, startMillis)
            put(Events.DTEND, startMillis + durationMillis)
            put(Events.EVENT_TIMEZONE, parentEvent.getTimeZoneString())
            put(Events.ORIGINAL_ID, parentRemoteId)
            put(Events.ORIGINAL_INSTANCE_TIME, startMillis)
            put(Events.STATUS, Events.STATUS_CANCELED)
            put(Events.ORIGINAL_ALL_DAY, if (parentEvent.getIsAllDay()) 1 else 0)
        }

        appContext.contentResolver.insert(Events.CONTENT_URI, values)
    }

    private fun resolveCalendarId(event: Event): Int {
        val fromSource = event.getCalDAVCalendarId()
        if (fromSource != 0) return fromSource

        val stored = config.lastUsedCaldavCalendarId
        if (stored != 0) return stored

        val first = config.getSyncedCalendarIdsAsList().firstOrNull() ?: 0
        if (first != 0) config.lastUsedCaldavCalendarId = first
        return first
    }

    private fun hasCalendarPermission(): Boolean {
        val read = ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.READ_CALENDAR)
        val write = ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.WRITE_CALENDAR)
        return read == android.content.pm.PackageManager.PERMISSION_GRANTED &&
            write == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun ensureEventTypeForCalendar(calendar: CalDAVCalendar): Long {
        val dao = db.eventTypesDao()
        val existing = dao.getEventTypeWithCalDAVCalendarId(calendar.id)
        if (existing != null) {
            if (existing.title != calendar.displayName || existing.color != calendar.color) {
                dao.insertOrUpdate(
                    existing.copy(
                        title = calendar.displayName,
                        caldavDisplayName = calendar.displayName,
                        caldavEmail = calendar.accountName,
                        color = calendar.color
                    )
                )
            }
            return existing.id ?: 0L
        }

        val id = dao.insertOrUpdate(
            EventType(
                id = null,
                title = calendar.displayName,
                color = calendar.color,
                caldavCalendarId = calendar.id,
                caldavDisplayName = calendar.displayName,
                caldavEmail = calendar.accountName
            )
        )
        config.lastUsedCaldavCalendarId = calendar.id
        return id
    }

    @SuppressLint("MissingPermission")
    private fun fetchCalDAVCalendarEvents(calendar: CalDAVCalendar, eventTypeId: Long) {
        val source = "$CALDAV-${calendar.id}"
        val existing = db.eventsDao().getEventsFromCalDAVCalendar(source).associateBy { it.importId }.toMutableMap()
        val fetchedImportIds = HashSet<String>()

        // 系统日历事件可能没有自定义颜色 (EVENT_COLOR=0)，优先保留已有本地颜色
        val calendarFallbackColor = if (calendar.color != 0) calendar.color else 0xFFA2B5BB.toInt()

        val projection = arrayOf(
            Events._ID,
            Events.TITLE,
            Events.DESCRIPTION,
            Events.DTSTART,
            Events.DTEND,
            Events.DURATION,
            Events.EXDATE,
            Events.ALL_DAY,
            Events.RRULE,
            Events.ORIGINAL_ID,
            Events.ORIGINAL_INSTANCE_TIME,
            Events.EVENT_LOCATION,
            Events.EVENT_TIMEZONE,
            Events.CALENDAR_TIME_ZONE,
            Events.DELETED,
            Events.AVAILABILITY,
            Events.STATUS,
            Events.EVENT_COLOR
        )

        val selection = "${Events.CALENDAR_ID} = ?"
        val args = arrayOf(calendar.id.toString())
        appContext.contentResolver.query(Events.CONTENT_URI, projection, selection, args, null)?.use { cursor ->
            val idIndex = cursor.getColumnIndexOrThrow(Events._ID)
            val titleIndex = cursor.getColumnIndexOrThrow(Events.TITLE)
            val descriptionIndex = cursor.getColumnIndexOrThrow(Events.DESCRIPTION)
            val startIndex = cursor.getColumnIndexOrThrow(Events.DTSTART)
            val endIndex = cursor.getColumnIndexOrThrow(Events.DTEND)
            val durationIndex = cursor.getColumnIndexOrThrow(Events.DURATION)
            val exdateIndex = cursor.getColumnIndexOrThrow(Events.EXDATE)
            val allDayIndex = cursor.getColumnIndexOrThrow(Events.ALL_DAY)
            val rruleIndex = cursor.getColumnIndexOrThrow(Events.RRULE)
            val originalIdIndex = cursor.getColumnIndexOrThrow(Events.ORIGINAL_ID)
            val originalInstanceTimeIndex = cursor.getColumnIndexOrThrow(Events.ORIGINAL_INSTANCE_TIME)
            val locationIndex = cursor.getColumnIndexOrThrow(Events.EVENT_LOCATION)
            val eventTimeZoneIndex = cursor.getColumnIndexOrThrow(Events.EVENT_TIMEZONE)
            val calendarTimeZoneIndex = cursor.getColumnIndexOrThrow(Events.CALENDAR_TIME_ZONE)
            val deletedIndex = cursor.getColumnIndexOrThrow(Events.DELETED)
            val availabilityIndex = cursor.getColumnIndexOrThrow(Events.AVAILABILITY)
            val statusIndex = cursor.getColumnIndexOrThrow(Events.STATUS)
            val colorIndex = cursor.getColumnIndexOrThrow(Events.EVENT_COLOR)

            while (cursor.moveToNext()) {
                if (cursor.getInt(deletedIndex) == 1) continue

                val remoteId = cursor.getLong(idIndex)
                val title = cursor.getString(titleIndex) ?: ""
                val status = cursor.getInt(statusIndex)

                val startTs = cursor.getLong(startIndex) / 1000L
                var endTs = cursor.getLong(endIndex) / 1000L
                if (endTs == 0L) {
                    endTs = startTs + parseDurationSeconds(cursor.getString(durationIndex) ?: "")
                }

                val allDay = cursor.getInt(allDayIndex) == 1
                val exdate = parseExdates(cursor.getString(exdateIndex) ?: "")
                val timezone = cursor.getString(eventTimeZoneIndex)
                    ?: cursor.getString(calendarTimeZoneIndex)
                    ?: ZoneId.systemDefault().id
                val importId = "$source-$remoteId"
                fetchedImportIds.add(importId)

                val originalId = if (!cursor.isNull(originalIdIndex)) cursor.getLong(originalIdIndex) else 0L
                val originalInstanceTime = if (!cursor.isNull(originalInstanceTimeIndex)) cursor.getLong(originalInstanceTimeIndex) else 0L

                val reminders = getCalDAVEventReminders(remoteId)

                val flags = if (allDay) FLAG_ALL_DAY else 0
                val base = existing[importId]

                if (originalInstanceTime != 0L) {
                    val parentImportId = "$source-$originalId"
                    val parentEvent = db.eventsDao().getEventOrTaskWithImportId(parentImportId)
                    if (parentEvent != null) {
                        val occurrenceToken = formatExdateUtc(originalInstanceTime)
                        val parentUpdated = if (occurrenceToken in parentEvent.exdates) {
                            parentEvent
                        } else {
                            parentEvent.copy(exdates = (parentEvent.exdates + occurrenceToken).distinct())
                        }
                        if (!isSameForSync(parentEvent, parentUpdated)) {
                            db.eventsDao().insertOrUpdate(parentUpdated)
                        }

                        if (status != Events.STATUS_CANCELED && title.isNotBlank()) {
                            val exceptionEvent = Event(
                                id = base?.id,
                                startTS = startTs,
                                endTS = endTs,
                                title = title,
                                location = cursor.getString(locationIndex) ?: "",
                                description = cursor.getString(descriptionIndex) ?: "",
                                reminder1Minutes = reminders.getOrNull(0)?.minutes ?: REMINDER_OFF,
                                reminder2Minutes = reminders.getOrNull(1)?.minutes ?: REMINDER_OFF,
                                reminder3Minutes = reminders.getOrNull(2)?.minutes ?: REMINDER_OFF,
                                reminder1Type = reminders.getOrNull(0)?.type ?: REMINDER_NOTIFICATION,
                                reminder2Type = reminders.getOrNull(1)?.type ?: REMINDER_NOTIFICATION,
                                reminder3Type = reminders.getOrNull(2)?.type ?: REMINDER_NOTIFICATION,
                                rrule = "",
                                exdates = emptyList(),
                                attendees = getCalDAVEventAttendees(remoteId, calendar),
                                importId = importId,
                                timeZone = timezone,
                                flags = flags,
                                eventType = parentUpdated.eventType,
                                parentId = parentUpdated.id ?: 0,
                                lastUpdated = System.currentTimeMillis(),
                                source = source,
                                availability = cursor.getInt(availabilityIndex),
                                color = resolveInboundEventColor(
                                    rawColor = cursor.getInt(colorIndex),
                                    base = base,
                                    fallbackColor = calendarFallbackColor
                                ),
                                type = base?.type ?: 0,
                                state = base?.state ?: STATE_PENDING,
                                tag = when {
                                    base == null -> TAG_GENERAL
                                    isNoteTag(base.tag) -> EventTags.NOTE
                                    else -> base.tag
                                }
                            )
                            db.eventsDao().insertOrUpdate(exceptionEvent)
                        } else {
                            base?.id?.let { db.eventsDao().deleteEvent(it) }
                        }
                        existing.remove(importId)
                        continue
                    }
                }

                if (title.isBlank()) {
                    existing.remove(importId)
                    continue
                }

                val event = Event(
                    id = base?.id,
                    startTS = startTs,
                    endTS = endTs,
                    title = title,
                    location = cursor.getString(locationIndex) ?: "",
                    description = cursor.getString(descriptionIndex) ?: "",
                    reminder1Minutes = reminders.getOrNull(0)?.minutes ?: REMINDER_OFF,
                    reminder2Minutes = reminders.getOrNull(1)?.minutes ?: REMINDER_OFF,
                    reminder3Minutes = reminders.getOrNull(2)?.minutes ?: REMINDER_OFF,
                    reminder1Type = reminders.getOrNull(0)?.type ?: REMINDER_NOTIFICATION,
                    reminder2Type = reminders.getOrNull(1)?.type ?: REMINDER_NOTIFICATION,
                    reminder3Type = reminders.getOrNull(2)?.type ?: REMINDER_NOTIFICATION,
                    rrule = cursor.getString(rruleIndex) ?: "",
                    exdates = exdate,
                    attendees = getCalDAVEventAttendees(remoteId, calendar),
                    importId = importId,
                    timeZone = timezone,
                    flags = flags,
                    eventType = base?.eventType ?: eventTypeId,
                    parentId = base?.parentId ?: 0,
                    lastUpdated = System.currentTimeMillis(),
                    source = source,
                    availability = cursor.getInt(availabilityIndex),
                    color = resolveInboundEventColor(
                        rawColor = cursor.getInt(colorIndex),
                        base = base,
                        fallbackColor = calendarFallbackColor
                    ),
                    type = base?.type ?: 0,
                    state = base?.state ?: STATE_PENDING,
                    tag = when {
                        base == null -> TAG_GENERAL
                        isNoteTag(base.tag) -> EventTags.NOTE
                        else -> base.tag
                    }
                )

                if (base == null || !isSameForSync(base, event)) {
                    db.eventsDao().insertOrUpdate(event)
                }
                existing.remove(importId)
            }
        }

        if (existing.isNotEmpty()) {
            val idsToDelete = existing.values.mapNotNull { it.id }
            if (idsToDelete.isNotEmpty()) {
                db.eventsDao().deleteEvents(idsToDelete)
            }
        }
    }

    private fun parseDurationSeconds(durationRaw: String): Long {
        if (durationRaw.isBlank()) return 0L
        return try {
            // 标准 ISO-8601: PT1800S, PT1H30M, P1DT5H
            Duration.parse(durationRaw).seconds
        } catch (_: Exception) {
            try {
                // 系统日历常见的非标格式: P1800S, P30M, P1H
                // 在 P 后面、第一个时间单位(H/M/S)前面插入 T
                val fixed = durationRaw.replace(Regex("^P(\\d)"), "PT$1")
                Duration.parse(fixed).seconds
            } catch (_: Exception) {
                // 兜底：手动解析 RFC 5545 duration
                parseRfc5545Duration(durationRaw)
            }
        }
    }

    /**
     * 手动解析 RFC 5545 DURATION 值。
     * 支持格式: P15DT5H0M20S, P1800S, PT30M, P7W, P1D 等
     */
    private fun parseRfc5545Duration(raw: String): Long {
        if (raw.isBlank()) return 0L
        var s = raw.uppercase().removePrefix("+").removePrefix("-")
        if (!s.startsWith("P")) return 0L
        s = s.removePrefix("P")

        var totalSeconds = 0L
        var inTimePart = false

        // 处理周: P7W
        val weekMatch = Regex("(\\d+)W").find(s)
        if (weekMatch != null) {
            totalSeconds += weekMatch.groupValues[1].toLong() * 7 * 86400
            s = s.replace(weekMatch.value, "")
        }

        // 分离日期部分和时间部分
        val tIndex = s.indexOf('T')
        val datePart = if (tIndex >= 0) s.substring(0, tIndex) else s
        val timePart = if (tIndex >= 0) s.substring(tIndex + 1) else ""

        // 如果没有 T 且包含 H/M/S，整体当作时间部分
        val effectiveTimePart = if (tIndex < 0 && s.contains(Regex("[HMS]"))) s else timePart

        // 日期部分: D
        Regex("(\\d+)D").find(datePart)?.let {
            totalSeconds += it.groupValues[1].toLong() * 86400
        }

        // 时间部分: H, M, S
        Regex("(\\d+)H").find(effectiveTimePart)?.let {
            totalSeconds += it.groupValues[1].toLong() * 3600
        }
        Regex("(\\d+)M").find(effectiveTimePart)?.let {
            totalSeconds += it.groupValues[1].toLong() * 60
        }
        Regex("(\\d+)S").find(effectiveTimePart)?.let {
            totalSeconds += it.groupValues[1].toLong()
        }

        return totalSeconds
    }

    private fun parseExdates(exdateRaw: String): List<String> {
        if (exdateRaw.isBlank()) return emptyList()
        return exdateRaw
            .split('\n', ',')
            .map { it.substringAfterLast(';').trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun formatExdateUtc(millis: Long): String {
        return DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC)
            .format(Instant.ofEpochMilli(millis))
    }

    private fun isSameForSync(old: Event, new: Event): Boolean {
        return old.copy(id = null, lastUpdated = 0L) == new.copy(id = null, lastUpdated = 0L)
    }

    private fun resolveInboundEventColor(rawColor: Int, base: Event?, fallbackColor: Int): Int {
        return when {
            rawColor != 0 -> rawColor
            base != null && base.color != 0 -> base.color
            else -> fallbackColor
        }
    }

    private fun buildEventContentValues(event: Event, calendarId: Int): ContentValues {
        return ContentValues().apply {
            put(Events.CALENDAR_ID, calendarId)
            put(Events.TITLE, event.title)
            put(Events.DESCRIPTION, event.description)
            put(Events.EVENT_LOCATION, event.location)
            put(Events.STATUS, Events.STATUS_CONFIRMED)
            put(Events.AVAILABILITY, event.availability)
            if (event.color != 0) {
                put(Events.EVENT_COLOR, event.color)
            }

            if (event.rrule.isBlank()) {
                putNull(Events.RRULE)
            } else {
                put(Events.RRULE, event.rrule)
            }

            if (event.exdates.isEmpty()) {
                putNull(Events.EXDATE)
            } else {
                put(Events.EXDATE, event.exdates.joinToString(","))
            }

            if (event.getIsAllDay()) {
                put(Events.ALL_DAY, 1)
            } else {
                put(Events.ALL_DAY, 0)
            }

            put(Events.DTSTART, event.startTS * 1000L)
            put(Events.EVENT_TIMEZONE, event.getTimeZoneString())

            // CalendarContract 要求重复日程使用 DURATION 而非 DTEND
            if (event.rrule.isNotBlank() && event.parentId == 0L) {
                val durationSec = event.endTS - event.startTS
                val durationCode = if (event.getIsAllDay()) {
                    val days = maxOf(1L, durationSec / 86400L)
                    "P${days}D"
                } else {
                    "PT${durationSec}S"
                }
                put(Events.DURATION, durationCode)
                putNull(Events.DTEND)
            } else {
                put(Events.DTEND, event.endTS * 1000L)
                putNull(Events.DURATION)
            }

            if (event.parentId != 0L) {
                val parentEvent = db.eventsDao().getEventOrTaskWithId(event.parentId)
                val parentRemoteId = parentEvent?.getCalDAVEventId() ?: 0L
                if (parentRemoteId > 0L) {
                    put(Events.ORIGINAL_ID, parentRemoteId)
                    put(Events.ORIGINAL_INSTANCE_TIME, event.startTS * 1000L)
                    put(Events.ORIGINAL_ALL_DAY, if (parentEvent?.getIsAllDay() == true) 1 else 0)
                }
            }
        }
    }

    private fun setupCalDAVEventReminders(event: Event) {
        val eventId = event.getCalDAVEventId()
        if (eventId == 0L) return

        appContext.contentResolver.delete(
            Reminders.CONTENT_URI,
            "${Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString())
        )

        event.getReminders().forEach { reminder ->
            val values = ContentValues().apply {
                put(Reminders.MINUTES, reminder.minutes)
                put(Reminders.METHOD, if (reminder.type == REMINDER_EMAIL) Reminders.METHOD_EMAIL else Reminders.METHOD_ALERT)
                put(Reminders.EVENT_ID, eventId)
            }
            appContext.contentResolver.insert(Reminders.CONTENT_URI, values)
        }
    }

    private fun getCalDAVEventReminders(eventId: Long): List<com.antgskds.calendarassistant.calendar.models.Reminder> {
        val reminders = ArrayList<com.antgskds.calendarassistant.calendar.models.Reminder>()
        val projection = arrayOf(Reminders.MINUTES, Reminders.METHOD)
        val selection = "${Reminders.EVENT_ID} = ?"
        val args = arrayOf(eventId.toString())
        appContext.contentResolver.query(Reminders.CONTENT_URI, projection, selection, args, null)?.use { cursor ->
            val minutesIndex = cursor.getColumnIndexOrThrow(Reminders.MINUTES)
            val methodIndex = cursor.getColumnIndexOrThrow(Reminders.METHOD)
            while (cursor.moveToNext()) {
                val method = cursor.getInt(methodIndex)
                if (method == Reminders.METHOD_ALERT || method == Reminders.METHOD_EMAIL) {
                    reminders.add(
                        com.antgskds.calendarassistant.calendar.models.Reminder(
                            minutes = cursor.getInt(minutesIndex),
                            type = if (method == Reminders.METHOD_EMAIL) REMINDER_EMAIL else REMINDER_NOTIFICATION
                        )
                    )
                }
            }
        }
        return reminders.sortedBy { it.minutes }
    }

    private fun setupCalDAVEventAttendees(event: Event) {
        val eventId = event.getCalDAVEventId()
        if (eventId == 0L) return

        appContext.contentResolver.delete(
            Attendees.CONTENT_URI,
            "${Attendees.EVENT_ID} = ?",
            arrayOf(eventId.toString())
        )

        event.attendees.forEach { attendee ->
            val values = ContentValues().apply {
                put(Attendees.ATTENDEE_NAME, attendee.name)
                put(Attendees.ATTENDEE_EMAIL, attendee.email)
                put(Attendees.ATTENDEE_STATUS, attendee.status)
                put(Attendees.ATTENDEE_RELATIONSHIP, attendee.relationship)
                put(Attendees.EVENT_ID, eventId)
            }
            appContext.contentResolver.insert(Attendees.CONTENT_URI, values)
        }
    }

    @SuppressLint("MissingPermission")
    private fun getCalDAVEventAttendees(eventId: Long, calendar: CalDAVCalendar) =
        buildList {
            val projection = arrayOf(
                Attendees.ATTENDEE_NAME,
                Attendees.ATTENDEE_EMAIL,
                Attendees.ATTENDEE_STATUS,
                Attendees.ATTENDEE_RELATIONSHIP
            )
            val selection = "${Attendees.EVENT_ID} = ?"
            val args = arrayOf(eventId.toString())
            appContext.contentResolver.query(Attendees.CONTENT_URI, projection, selection, args, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndexOrThrow(Attendees.ATTENDEE_NAME)
                val emailIndex = cursor.getColumnIndexOrThrow(Attendees.ATTENDEE_EMAIL)
                val statusIndex = cursor.getColumnIndexOrThrow(Attendees.ATTENDEE_STATUS)
                val relationIndex = cursor.getColumnIndexOrThrow(Attendees.ATTENDEE_RELATIONSHIP)

                while (cursor.moveToNext()) {
                    add(
                        com.antgskds.calendarassistant.calendar.models.Attendee(
                            contactId = 0,
                            name = cursor.getString(nameIndex) ?: "",
                            email = cursor.getString(emailIndex) ?: "",
                            status = cursor.getInt(statusIndex),
                            photoUri = "",
                            isMe = (cursor.getString(emailIndex) ?: "") == calendar.ownerName,
                            relationship = cursor.getInt(relationIndex)
                        )
                    )
                }
            }
        }
}
