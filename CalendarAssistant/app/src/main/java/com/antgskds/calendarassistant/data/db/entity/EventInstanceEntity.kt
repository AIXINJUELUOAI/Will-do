package com.antgskds.calendarassistant.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "event_instances",
    foreignKeys = [
        ForeignKey(
            entity = EventMasterEntity::class,
            parentColumns = ["masterId"],
            childColumns = ["masterId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["masterId"]),
        Index(value = ["startTime", "endTime"]),
        Index(value = ["syncFingerprint"], unique = true)
    ]
)
data class EventInstanceEntity(
    @PrimaryKey
    val instanceId: String,
    val masterId: String,
    val startTime: Long,
    val endTime: Long,
    val currentStateId: String,
    val completedAt: Long?,
    val archivedAt: Long?,
    val syncFingerprint: String,
    val isSynced: Boolean,
    val isCancelled: Boolean
)
