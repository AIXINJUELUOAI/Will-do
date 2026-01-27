package com.antgskds.calendarassistant.data.source

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.data.model.MyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class EventJsonDataSource(private val context: Context) {
    private val fileName = "events.json"
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        coerceInputValues = true
    }

    suspend fun loadEvents(): List<MyEvent> = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return@withContext emptyList()

        try {
            val content = file.readText()
            if (content.isBlank()) return@withContext emptyList()
            json.decodeFromString<List<MyEvent>>(content)
        } catch (e: Exception) {
            Log.e("EventJsonDataSource", "Error loading events", e)
            emptyList()
        }
    }

    suspend fun saveEvents(events: List<MyEvent>) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, fileName)
            val content = json.encodeToString(events)
            file.writeText(content)
        } catch (e: Exception) {
            Log.e("EventJsonDataSource", "Error saving events", e)
        }
    }
}