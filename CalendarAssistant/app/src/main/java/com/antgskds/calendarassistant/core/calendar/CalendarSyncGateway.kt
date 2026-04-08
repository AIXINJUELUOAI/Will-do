package com.antgskds.calendarassistant.core.calendar

import android.content.Context
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.TimeNode

class CalendarSyncGateway(context: Context) {
    private val legacyManager = CalendarSyncManager(context.applicationContext)
    private val v2Manager = CalendarSyncManagerV2(context.applicationContext)

    suspend fun syncAllToCalendar(
        useV2: Boolean,
        events: List<MyEvent>,
        courses: List<Course>,
        semesterStart: String?,
        totalWeeks: Int,
        timeNodes: List<TimeNode>
    ): Result<Unit> {
        return if (useV2) {
            v2Manager.syncAllToCalendar(events, courses, semesterStart, totalWeeks, timeNodes)
        } else {
            legacyManager.syncAllToCalendar(events, courses, semesterStart, totalWeeks, timeNodes)
        }
    }

    suspend fun syncEventToCalendar(useV2: Boolean, event: MyEvent): Result<Unit> {
        return if (useV2) v2Manager.syncEventToCalendar(event) else legacyManager.syncEventToCalendar(event)
    }

    suspend fun enableSync(useV2: Boolean): Result<Unit> {
        return if (useV2) v2Manager.enableSync() else legacyManager.enableSync()
    }

    suspend fun disableSync(useV2: Boolean): Result<Unit> {
        return if (useV2) v2Manager.disableSync() else legacyManager.disableSync()
    }

    suspend fun getSyncStatus(useV2: Boolean): CalendarSyncManager.SyncStatus {
        return if (useV2) v2Manager.getSyncStatus().toLegacySyncStatus() else legacyManager.getSyncStatus()
    }

    suspend fun syncFromCalendar(
        useV2: Boolean,
        onEventAdded: suspend (MyEvent) -> Unit,
        onEventUpdated: suspend (MyEvent) -> Unit,
        onEventDeleted: suspend (String) -> Unit,
        allowRecurringSync: Boolean,
        activeEvents: List<MyEvent>,
        archivedEvents: List<MyEvent>
    ): Result<Int> {
        return if (useV2) {
            v2Manager.syncFromCalendar(
                onEventAdded = onEventAdded,
                onEventUpdated = onEventUpdated,
                onEventDeleted = onEventDeleted,
                allowRecurringSync = allowRecurringSync,
                activeEvents = activeEvents,
                archivedEvents = archivedEvents
            )
        } else {
            legacyManager.syncFromCalendar(
                onEventAdded = onEventAdded,
                onEventUpdated = onEventUpdated,
                onEventDeleted = onEventDeleted,
                allowRecurringSync = allowRecurringSync,
                activeEvents = activeEvents,
                archivedEvents = archivedEvents
            )
        }
    }

    suspend fun getV2Status(): CalendarSyncManagerV2.SyncStatus = v2Manager.getSyncStatus()

    private fun CalendarSyncManagerV2.SyncStatus.toLegacySyncStatus(): CalendarSyncManager.SyncStatus {
        return CalendarSyncManager.SyncStatus(
            isEnabled = isEnabled,
            hasPermission = hasPermission,
            targetCalendarId = targetCalendarId,
            lastSyncTime = lastSyncTime,
            mappedEventCount = mappedEventCount
        )
    }
}
