package com.antgskds.calendarassistant.data.node.store

import android.util.Log
import androidx.compose.ui.graphics.toArgb
import com.antgskds.calendarassistant.data.model.MyEvent

internal object StoreRoomDiffNode {
    fun logRoomDiff(label: String, jsonEvents: List<MyEvent>, roomEvents: List<MyEvent>) {
        val jsonMap = jsonEvents.associateBy { it.id }
        val roomMap = roomEvents.associateBy { it.id }
        val jsonIds = jsonMap.keys
        val roomIds = roomMap.keys
        val missingInRoom = jsonIds - roomIds
        val missingInJson = roomIds - jsonIds

        val mismatches = jsonIds.intersect(roomIds).mapNotNull { id ->
            val jsonSnapshot = EventCompareSnapshot.from(jsonMap[id] ?: return@mapNotNull null)
            val roomSnapshot = EventCompareSnapshot.from(roomMap[id] ?: return@mapNotNull null)
            val diffs = jsonSnapshot.diff(roomSnapshot)
            if (diffs.isEmpty()) null else id to diffs
        }

        if (missingInRoom.isEmpty() && missingInJson.isEmpty() && mismatches.isEmpty()) {
            Log.d("RoomRead", "$label list match: ${jsonEvents.size} items")
            return
        }

        val sampleMissingRoom = missingInRoom.take(5)
        val sampleMissingJson = missingInJson.take(5)
        val sampleMismatch = mismatches.take(5).joinToString(", ") { (id, diffs) ->
            "$id(${diffs.joinToString("/")})"
        }

        Log.w(
            "RoomRead",
            "$label list mismatch: json=${jsonEvents.size}, room=${roomEvents.size}, " +
                "missingInRoom=${missingInRoom.size} sample=$sampleMissingRoom, " +
                "missingInJson=${missingInJson.size} sample=$sampleMissingJson, " +
                "fieldMismatch=${mismatches.size} sample=$sampleMismatch"
        )
    }

    private data class EventCompareSnapshot(
        val title: String,
        val startDate: java.time.LocalDate,
        val endDate: java.time.LocalDate,
        val startTime: String,
        val endTime: String,
        val location: String,
        val description: String,
        val colorArgb: Int,
        val isImportant: Boolean,
        val sourceImagePath: String?,
        val reminders: List<Int>,
        val tag: String,
        val isCompleted: Boolean,
        val completedAtPresent: Boolean,
        val isCheckedIn: Boolean,
        val archivedAt: Long?,
        val skipCalendarSync: Boolean,
        val isRecurring: Boolean,
        val isRecurringParent: Boolean
    ) {
        fun diff(other: EventCompareSnapshot): List<String> {
            val diffs = mutableListOf<String>()
            if (title != other.title) diffs.add("title")
            if (startDate != other.startDate) diffs.add("startDate")
            if (endDate != other.endDate) diffs.add("endDate")
            if (startTime != other.startTime) diffs.add("startTime")
            if (endTime != other.endTime) diffs.add("endTime")
            if (location != other.location) diffs.add("location")
            if (description != other.description) diffs.add("description")
            if (colorArgb != other.colorArgb) diffs.add("color")
            if (isImportant != other.isImportant) diffs.add("isImportant")
            if (sourceImagePath != other.sourceImagePath) diffs.add("sourceImagePath")
            if (reminders != other.reminders) diffs.add("reminders")
            if (tag != other.tag) diffs.add("tag")
            if (isCompleted != other.isCompleted) diffs.add("isCompleted")
            if (completedAtPresent != other.completedAtPresent) diffs.add("completedAt")
            if (isCheckedIn != other.isCheckedIn) diffs.add("isCheckedIn")
            if (archivedAt != other.archivedAt) diffs.add("archivedAt")
            if (skipCalendarSync != other.skipCalendarSync) diffs.add("skipCalendarSync")
            if (isRecurring != other.isRecurring) diffs.add("isRecurring")
            if (isRecurringParent != other.isRecurringParent) diffs.add("isRecurringParent")
            return diffs
        }

        companion object {
            fun from(event: MyEvent): EventCompareSnapshot {
                return EventCompareSnapshot(
                    title = event.title,
                    startDate = event.startDate,
                    endDate = event.endDate,
                    startTime = event.startTime,
                    endTime = event.endTime,
                    location = event.location,
                    description = event.description,
                    colorArgb = event.color.toArgb(),
                    isImportant = event.isImportant,
                    sourceImagePath = event.sourceImagePath,
                    reminders = event.reminders,
                    tag = event.tag,
                    isCompleted = event.isCompleted,
                    completedAtPresent = event.completedAt != null,
                    isCheckedIn = event.isCheckedIn,
                    archivedAt = event.archivedAt,
                    skipCalendarSync = event.skipCalendarSync,
                    isRecurring = event.isRecurring,
                    isRecurringParent = event.isRecurringParent
                )
            }
        }
    }
}
