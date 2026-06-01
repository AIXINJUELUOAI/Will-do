package com.antgskds.calendarassistant.core.center

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.calendar.data.EventsDatabase
import com.antgskds.calendarassistant.calendar.helpers.CALDAV
import com.antgskds.calendarassistant.calendar.helpers.SOURCE_SIMPLE_CALENDAR
import com.antgskds.calendarassistant.calendar.helpers.STATE_PENDING
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.core.util.EventDuplicateSignature
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DuplicateEventCleanupResult(
    val scanned: Int,
    val duplicateGroups: Int,
    val mergedBindings: Int,
    val movedAttachments: Int,
    val deleted: Int,
    val skipped: Int = 0
)

class DuplicateEventCleanupCenter(context: Context) {
    private val appContext = context.applicationContext
    private val db = EventsDatabase.getInstance(appContext)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun runAutoCleanupIfNeeded(force: Boolean = false): DuplicateEventCleanupResult = withContext(Dispatchers.IO) {
        if (!force && prefs.getInt(KEY_CLEANUP_VERSION, 0) >= CLEANUP_VERSION) {
            return@withContext DuplicateEventCleanupResult(0, 0, 0, 0, 0)
        }
        cleanupExactDuplicates().also { result ->
            prefs.edit()
                .putInt(KEY_CLEANUP_VERSION, CLEANUP_VERSION)
                .putLong(KEY_CLEANUP_COMPLETED_AT, System.currentTimeMillis())
                .putString(KEY_CLEANUP_SUMMARY, result.toString())
                .apply()
            Log.i(TAG, "auto cleanup finished: $result")
        }
    }

    suspend fun cleanupExactDuplicates(): DuplicateEventCleanupResult = withContext(Dispatchers.IO) {
        val dao = db.eventsDao()
        val attachmentDao = db.eventAttachmentsDao()
        val events = (dao.getAllEventsOrTasks() + dao.getArchivedEvents())
            .filter { it.id != null }
            .distinctBy { it.id }
        if (events.isEmpty()) {
            return@withContext DuplicateEventCleanupResult(0, 0, 0, 0, 0)
        }

        val groups = events.groupBy(EventDuplicateSignature::key).values.filter { it.size > 1 }
        var mergedBindings = 0
        var movedAttachments = 0
        var deleted = 0

        db.runInTransaction {
            groups.forEach { group ->
                val keep = selectKeeper(group)
                val keepId = keep.id ?: return@forEach
                val duplicates = group.filter { it.id != keepId }
                val merged = mergeDuplicateGroup(keep, duplicates)
                if (merged != keep) {
                    dao.insertOrUpdate(merged)
                    if (merged.importId != keep.importId || merged.source != keep.source) {
                        mergedBindings++
                    }
                }

                duplicates.forEach { duplicate ->
                    val duplicateId = duplicate.id ?: return@forEach
                    val attachments = attachmentDao.getAttachmentsForEvent(duplicateId)
                    if (attachments.isNotEmpty()) {
                        attachmentDao.moveAttachmentsToEvent(duplicateId, keepId)
                        movedAttachments += attachments.size
                    }
                    dao.deleteEvent(duplicateId)
                    deleted++
                }
            }
        }

        DuplicateEventCleanupResult(
            scanned = events.size,
            duplicateGroups = groups.size,
            mergedBindings = mergedBindings,
            movedAttachments = movedAttachments,
            deleted = deleted
        ).also { Log.i(TAG, "manual cleanup finished: $it") }
    }

    private fun selectKeeper(events: List<Event>): Event {
        return events.maxWithOrNull(
            compareBy<Event> { if (it.archivedAt == null) 1 else 0 }
                .thenBy { if (hasSystemBinding(it)) 1 else 0 }
                .thenBy { statePriority(it) }
                .thenBy { it.getReminders().size }
                .thenBy { if (it.color != 0) 1 else 0 }
                .thenBy { normalizedLastUpdated(it) }
                .thenBy { it.id ?: 0L }
        ) ?: events.first()
    }

    private fun mergeDuplicateGroup(keep: Event, duplicates: List<Event>): Event {
        val systemBound = (listOf(keep) + duplicates).firstOrNull(::hasSystemBinding)
        val richestState = (listOf(keep) + duplicates).maxByOrNull(::statePriority)
        val richestReminder = (listOf(keep) + duplicates).maxByOrNull { it.getReminders().size }
        val richestColor = (listOf(keep) + duplicates).firstOrNull { it.color != 0 }
        val richestDescription = (listOf(keep) + duplicates).maxByOrNull { it.description.length }
        val fallbackLocation = duplicates.firstOrNull { it.location.isNotBlank() }?.location.orEmpty()
        return keep.copy(
            location = keep.location.ifBlank { fallbackLocation },
            description = richestDescription?.description ?: keep.description,
            importId = systemBound?.importId?.takeIf { it.isNotBlank() } ?: keep.importId,
            source = systemBound?.source?.takeIf { it.isNotBlank() } ?: keep.source.ifBlank { SOURCE_SIMPLE_CALENDAR },
            state = richestState?.state ?: keep.state,
            reminder1Minutes = richestReminder?.reminder1Minutes ?: keep.reminder1Minutes,
            reminder2Minutes = richestReminder?.reminder2Minutes ?: keep.reminder2Minutes,
            reminder3Minutes = richestReminder?.reminder3Minutes ?: keep.reminder3Minutes,
            reminder1Type = richestReminder?.reminder1Type ?: keep.reminder1Type,
            reminder2Type = richestReminder?.reminder2Type ?: keep.reminder2Type,
            reminder3Type = richestReminder?.reminder3Type ?: keep.reminder3Type,
            color = richestColor?.color ?: keep.color,
            archivedAt = keep.archivedAt,
            lastUpdated = maxOf(keep.lastUpdated, duplicates.maxOfOrNull { it.lastUpdated } ?: keep.lastUpdated)
        )
    }

    private fun hasSystemBinding(event: Event): Boolean {
        return event.source.startsWith(CALDAV, ignoreCase = true) ||
            event.importId.startsWith(CALDAV, ignoreCase = true)
    }

    private fun statePriority(event: Event): Int = if (event.state == STATE_PENDING) 0 else 1

    private fun normalizedLastUpdated(event: Event): Long {
        val value = event.lastUpdated
        if (value <= 0L) return 0L
        return if (value > 9_999_999_999L) value else value * 1000L
    }

    companion object {
        private const val TAG = "DuplicateCleanup"
        private const val PREFS_NAME = "duplicate_event_cleanup"
        private const val KEY_CLEANUP_VERSION = "duplicate_event_cleanup_version"
        private const val KEY_CLEANUP_COMPLETED_AT = "duplicate_event_cleanup_completed_at"
        private const val KEY_CLEANUP_SUMMARY = "duplicate_event_cleanup_summary"
        private const val CLEANUP_VERSION = 1
    }
}
