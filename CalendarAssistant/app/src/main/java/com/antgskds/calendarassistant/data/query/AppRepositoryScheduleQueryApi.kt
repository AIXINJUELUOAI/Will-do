package com.antgskds.calendarassistant.data.query

import com.antgskds.calendarassistant.core.query.ScheduleQueryApi
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.repository.AppRepository
import kotlinx.coroutines.flow.StateFlow

class AppRepositoryScheduleQueryApi(
    private val repository: AppRepository
) : ScheduleQueryApi {
    override val events: StateFlow<List<MyEvent>>
        get() = repository.events

    override val courses: StateFlow<List<Course>>
        get() = repository.courses

    override val archivedEvents: StateFlow<List<MyEvent>>
        get() = repository.archivedEvents

    override fun fetchArchivedEvents() {
        repository.fetchArchivedEvents()
    }

    override fun getEventsCount(): Int {
        return repository.getEventsCount()
    }

    override fun getTotalEventsCount(): Int {
        return repository.getTotalEventsCount()
    }

    override fun getCoursesCount(): Int {
        return repository.getCoursesCount()
    }
}
