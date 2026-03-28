package com.antgskds.calendarassistant.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "event_transitions",
    foreignKeys = [
        ForeignKey(
            entity = EventRuleEntity::class,
            parentColumns = ["ruleId"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["ruleId"]),
        Index(value = ["fromStateId"])
    ]
)
data class EventTransitionEntity(
    @PrimaryKey
    val transitionId: String,
    val ruleId: String,
    val fromStateId: String,
    val toStateId: String,
    val actionLabel: String,
    val autoTransitionAfterMillis: Long?
)
