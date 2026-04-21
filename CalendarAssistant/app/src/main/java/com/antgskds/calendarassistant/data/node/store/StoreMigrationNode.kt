package com.antgskds.calendarassistant.data.node.store

import android.util.Log
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.MyEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal object StoreMigrationNode {
    fun migrateEventTypes(
        scope: CoroutineScope,
        shouldSkipMigration: () -> Boolean,
        loadEvents: suspend () -> List<MyEvent>,
        saveEvents: suspend (List<MyEvent>) -> Unit,
        onEventsUpdated: (List<MyEvent>) -> Unit
    ) {
        scope.launch {
            if (shouldSkipMigration()) {
                return@launch
            }

            val events = loadEvents()
            val needMigration = events.any { it.tag == "temp" || it.tag == "pickup" }
            if (!needMigration) {
                return@launch
            }

            val migratedEvents = events.map {
                when (it.tag) {
                    "temp", "pickup" -> it.copy(
                        tag = EventTags.PICKUP
                    )

                    else -> it
                }
            }
            saveEvents(migratedEvents)
            onEventsUpdated(migratedEvents)
            Log.i("StoreMigrationNode", "已迁移 ${events.size} 条旧数据: temp/pickup -> event + tag=pickup")
        }
    }

    fun migrateEventTags(
        scope: CoroutineScope,
        shouldSkipMigration: () -> Boolean,
        loadEvents: suspend () -> List<MyEvent>,
        saveEvents: suspend (List<MyEvent>) -> Unit,
        resolveRuleId: (String) -> String?,
        onEventsUpdated: (List<MyEvent>) -> Unit
    ) {
        scope.launch {
            if (shouldSkipMigration()) {
                return@launch
            }

            val events = loadEvents()
            val needMigration = events.any { it.tag.isBlank() }

            val migratedEvents = if (needMigration) {
                events.map { event ->
                    if (event.tag.isBlank()) {
                        val resolved = resolveRuleId(event.description)
                        val newTag = resolved?.ifBlank { null } ?: EventTags.GENERAL
                        event.copy(tag = newTag)
                    } else {
                        event
                    }
                }
            } else {
                events
            }

            val calibratedEvents = migratedEvents.map { event ->
                val correctTag = resolveRuleId(event.description)
                if (!correctTag.isNullOrBlank() && correctTag != event.tag) {
                    Log.d("StoreMigrationNode", "校准事件 tag: ${event.title}, ${event.tag} -> $correctTag")
                    event.copy(tag = correctTag)
                } else {
                    event
                }
            }

            saveEvents(calibratedEvents)
            onEventsUpdated(calibratedEvents)

            if (needMigration) {
                Log.i("StoreMigrationNode", "已迁移 ${events.size} 条旧数据的 tag")
            }
        }
    }
}
