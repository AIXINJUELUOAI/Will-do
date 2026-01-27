package com.antgskds.calendarassistant.data.source

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.data.model.Course
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class CourseJsonDataSource(private val context: Context) {
    private val fileName = "courses.json"
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        coerceInputValues = true
    }

    suspend fun loadCourses(): List<Course> = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return@withContext emptyList()

        try {
            val content = file.readText()
            if (content.isBlank()) return@withContext emptyList()
            json.decodeFromString<List<Course>>(content)
        } catch (e: Exception) {
            Log.e("CourseJsonDataSource", "Error loading courses", e)
            emptyList()
        }
    }

    suspend fun saveCourses(courses: List<Course>) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, fileName)
            val content = json.encodeToString(courses)
            file.writeText(content)
        } catch (e: Exception) {
            Log.e("CourseJsonDataSource", "Error saving courses", e)
        }
    }
}