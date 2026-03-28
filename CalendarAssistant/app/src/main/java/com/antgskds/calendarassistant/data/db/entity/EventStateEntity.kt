package com.antgskds.calendarassistant.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "event_states",
    foreignKeys = [
        ForeignKey(
            entity = EventRuleEntity::class,
            parentColumns = ["ruleId"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["ruleId"])
    ]
)
data class EventStateEntity(
    @PrimaryKey
    val stateId: String,
    val ruleId: String,
    val name: String,
    val isTerminal: Boolean,
    val displayTemplate: String
)
