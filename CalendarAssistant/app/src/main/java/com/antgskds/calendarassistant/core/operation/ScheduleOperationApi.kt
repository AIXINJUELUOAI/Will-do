package com.antgskds.calendarassistant.core.operation

import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.MyEvent

interface ScheduleOperationApi {
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

    suspend fun addCourse(course: Course)
    suspend fun updateCourse(course: Course)
    suspend fun deleteCourse(course: Course)

    suspend fun archiveEvent(eventId: String)
    suspend fun restoreEvent(archivedEventId: String)
    suspend fun deleteArchivedEvent(archivedEventId: String)
    suspend fun clearAllArchives()
    suspend fun autoArchiveExpiredEvents(): Int
}
