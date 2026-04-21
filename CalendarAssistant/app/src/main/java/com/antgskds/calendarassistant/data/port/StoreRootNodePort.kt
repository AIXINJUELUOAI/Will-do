package com.antgskds.calendarassistant.data.port

import com.antgskds.calendarassistant.core.calendar.CalendarManager
import com.antgskds.calendarassistant.core.calendar.CalendarSyncManager
import com.antgskds.calendarassistant.core.importer.ImportMode
import com.antgskds.calendarassistant.data.model.ImportResult
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings
import kotlinx.coroutines.flow.StateFlow

interface StoreRootNodePort {
    val events: StateFlow<List<MyEvent>>
    val archivedEvents: StateFlow<List<MyEvent>>
    val settings: StateFlow<MySettings>

    fun bindCapsuleRefreshHandler(handler: (() -> Unit)?)

    fun loadAndScheduleAll()
    fun fetchArchivedEvents()

    suspend fun addEvent(event: MyEvent, triggerSync: Boolean = true)
    suspend fun updateEvent(event: MyEvent, triggerSync: Boolean = true)
    suspend fun deleteEvent(
        eventId: String,
        triggerSync: Boolean = true,
        removeFromRoom: Boolean = true
    )

    suspend fun detachRecurringInstance(
        parentEventId: String,
        sourceInstanceId: String,
        sourceInstanceKey: String,
        detachedEvent: MyEvent
    )

    suspend fun performPrimaryRuleAction(eventId: String): Boolean
    suspend fun completeScheduleEvent(id: String)

    suspend fun archiveEvent(eventId: String)
    suspend fun restoreEvent(archivedEventId: String)
    suspend fun deleteArchivedEvent(archivedEventId: String)
    suspend fun clearAllArchives()
    suspend fun autoArchiveExpiredEvents(): Int

    fun getEventsCount(): Int
    fun getTotalEventsCount(): Int

    fun updateSettings(newSettings: MySettings)

    suspend fun exportCoursesData(): String
    suspend fun importCoursesData(jsonString: String): Result<Unit>
    suspend fun importWakeUpFile(
        content: String,
        mode: ImportMode,
        importSettings: Boolean
    ): Result<Int>
    suspend fun exportEventsData(): String
    suspend fun importEventsData(jsonString: String): Result<ImportResult>

    suspend fun enableCalendarSyncAndSyncNow(): Result<Unit>
    suspend fun manualSync(): Result<Unit>
    suspend fun enableCalendarSync(): Result<Unit>
    suspend fun disableCalendarSync(): Result<Unit>
    suspend fun getSyncStatus(): CalendarSyncManager.SyncStatus
    suspend fun getSelectableSyncCalendars(): List<CalendarManager.CalendarInfo>
    suspend fun updateSourceCalendars(calendarIds: List<Long>): Result<Unit>
    suspend fun syncFromCalendar(): Result<Int>
}
