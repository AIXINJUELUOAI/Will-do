package com.antgskds.calendarassistant.feature.schedule.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.antgskds.calendarassistant.feature.schedule.domain.model.EventType

@Dao
interface EventTypesDao {
    @Query("SELECT * FROM event_types ORDER BY title ASC")
    fun getEventTypes(): List<EventType>

    @Query("SELECT * FROM event_types WHERE id = :id")
    fun getEventTypeWithId(id: Long): EventType?

    @Query("SELECT * FROM event_types WHERE caldav_calendar_id = :calendarId")
    fun getEventTypeWithCalDAVCalendarId(calendarId: Int): EventType?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(eventType: EventType): Long
}
