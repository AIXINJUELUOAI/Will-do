package com.antgskds.calendarassistant.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "event_rules",
    indices = [
        Index(value = ["isEnabled"]),
        Index(value = ["appliesToSchedule"])
    ]
)
data class EventRuleEntity(
    @PrimaryKey
    val ruleId: String,
    val name: String,
    val isEnabled: Boolean,
    val appliesToSchedule: Boolean,
    val aiTag: String,
    val aiPrompt: String,
    val aiTitlePrompt: String,
    val extractionConfigJson: String,
    val iconSourceJson: String,
    val initialStateId: String
)
