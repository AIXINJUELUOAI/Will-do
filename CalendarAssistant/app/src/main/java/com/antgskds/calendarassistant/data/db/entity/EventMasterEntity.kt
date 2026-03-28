package com.antgskds.calendarassistant.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event_masters")
data class EventMasterEntity(
    @PrimaryKey
    val masterId: String,
    val ruleId: String?,
    val title: String,
    val description: String,
    val location: String,
    val colorArgb: Int,
    val rrule: String?,
    val syncId: Long?,
    val eventType: String,
    val remindersJson: String,
    val isImportant: Boolean,
    val sourceImagePath: String?,
    val skipCalendarSync: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val source: String
)
