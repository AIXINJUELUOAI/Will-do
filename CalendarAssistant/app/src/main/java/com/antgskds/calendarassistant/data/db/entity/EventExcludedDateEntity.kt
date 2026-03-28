package com.antgskds.calendarassistant.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "event_excluded_dates",
    foreignKeys = [
        ForeignKey(
            entity = EventMasterEntity::class,
            parentColumns = ["masterId"],
            childColumns = ["masterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["masterId", "excludedStartTime"], unique = true)
    ]
)
data class EventExcludedDateEntity(
    @PrimaryKey
    val excludedId: String,
    val masterId: String,
    val excludedStartTime: Long
)
