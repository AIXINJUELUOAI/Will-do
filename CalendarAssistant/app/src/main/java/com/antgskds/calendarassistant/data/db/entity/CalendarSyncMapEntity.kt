package com.antgskds.calendarassistant.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "calendar_sync_map",
    indices = [
        Index(value = ["calendarId", "systemEventId"], unique = true)
    ]
)
data class CalendarSyncMapEntity(
    @PrimaryKey
    val localMasterId: String,
    val systemEventId: Long,
    val calendarId: Long,
    val accountName: String,
    val accountType: String,
    val displayName: String,
    val lastSyncHash: Int
)
