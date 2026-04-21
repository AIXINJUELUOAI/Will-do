package com.antgskds.calendarassistant.core.operation

import com.antgskds.calendarassistant.data.model.MyEvent

interface ScheduleOperationApi {
    fun refreshAndScheduleAll()

    suspend fun addEvent(event: MyEvent)
    suspend fun updateEvent(event: MyEvent)
    suspend fun deleteEvent(eventId: String)

    suspend fun detachRecurringInstance(
        parentEventId: String,
        sourceInstanceId: String,
        sourceInstanceKey: String,
        detachedEvent: MyEvent
    )

    suspend fun performPrimaryRuleAction(eventId: String): Boolean
    suspend fun completeScheduleEvent(eventId: String)

    suspend fun archiveEvent(eventId: String)
    suspend fun restoreEvent(archivedEventId: String)
    suspend fun deleteArchivedEvent(archivedEventId: String)
    suspend fun clearAllArchives()
    suspend fun autoArchiveExpiredEvents(): Int
}
