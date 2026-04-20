package com.antgskds.calendarassistant.data.operation

import com.antgskds.calendarassistant.core.operation.ScheduleOperationApi
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.repository.AppRepository

class AppRepositoryScheduleOperationApi(
    private val repository: AppRepository
) : ScheduleOperationApi {
    override suspend fun addEvent(event: MyEvent) {
        repository.addEvent(event)
    }

    override suspend fun updateEvent(event: MyEvent) {
        repository.updateEvent(event)
    }

    override suspend fun deleteEvent(eventId: String) {
        repository.deleteEvent(eventId)
    }

    override suspend fun detachRecurringInstance(
        parentEventId: String,
        sourceInstanceId: String,
        sourceInstanceKey: String,
        detachedEvent: MyEvent
    ) {
        repository.detachRecurringInstance(parentEventId, sourceInstanceId, sourceInstanceKey, detachedEvent)
    }

    override suspend fun performPrimaryRuleAction(eventId: String): Boolean {
        return repository.performPrimaryRuleAction(eventId)
    }

    override suspend fun completeScheduleEvent(eventId: String) {
        repository.completeScheduleEvent(eventId)
    }

    override suspend fun addCourse(course: Course) {
        repository.addCourse(course)
    }

    override suspend fun updateCourse(course: Course) {
        repository.updateCourse(course)
    }

    override suspend fun deleteCourse(course: Course) {
        repository.deleteCourse(course)
    }

    override suspend fun archiveEvent(eventId: String) {
        repository.archiveEvent(eventId)
    }

    override suspend fun restoreEvent(archivedEventId: String) {
        repository.restoreEvent(archivedEventId)
    }

    override suspend fun deleteArchivedEvent(archivedEventId: String) {
        repository.deleteArchivedEvent(archivedEventId)
    }

    override suspend fun clearAllArchives() {
        repository.clearAllArchives()
    }

    override suspend fun autoArchiveExpiredEvents(): Int {
        return repository.autoArchiveExpiredEvents()
    }
}
