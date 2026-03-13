package com.antgskds.calendarassistant.data.source

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.core.util.DataSanitizer
import com.antgskds.calendarassistant.data.model.Course
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class CourseJsonDataSource(private val context: Context) {
    private val fileName = "courses.json"
    private val backupFileName = "courses.json.bak"
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        coerceInputValues = true
    }

    @Volatile
    private var lastCleanupInfo: String = ""

    suspend fun loadCourses(): List<Course> = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return@withContext emptyList()

        try {
            val content = file.readText()
            if (content.isBlank()) return@withContext emptyList()
            
            val courses = json.decodeFromString<List<Course>>(content)
            
            val result = DataSanitizer.sanitizeCourses(courses)
            
            if (result.removedTitles.isNotEmpty()) {
                val sanitizedContent = json.encodeToString(result.data)
                file.writeText(sanitizedContent)
                lastCleanupInfo = "课程（${result.removedTitles.take(10).joinToString("、")}${if (result.removedTitles.size > 10) "等${result.removedTitles.size}个" else ""}）"
                Log.i("CourseJsonDataSource", "数据自愈: 已清理 ${result.removedTitles.size} 条异常课程")
            }
            
            result.data
        } catch (e: Exception) {
            Log.e("CourseJsonDataSource", "加载课程失败，尝试读取备份", e)
            lastCleanupInfo = "课程（JSON解析失败）"
            loadFromBackup()
        }
    }

    fun getAndClearCleanupInfo(): String {
        val info = lastCleanupInfo
        lastCleanupInfo = ""
        return info
    }

    private fun loadFromBackup(): List<Course> {
        return try {
            val backupFile = File(context.filesDir, backupFileName)
            if (backupFile.exists()) {
                val content = backupFile.readText()
                if (content.isNotBlank()) {
                    val courses = json.decodeFromString<List<Course>>(content)
                    val result = DataSanitizer.sanitizeCourses(courses)
                    
                    val currentFile = File(context.filesDir, fileName)
                    currentFile.writeText(json.encodeToString(result.data))
                    
                    Log.i("CourseJsonDataSource", "从备份恢复成功")
                    return result.data
                }
            }
            Log.w("CourseJsonDataSource", "无备份或备份损坏，返回空列表")
            emptyList()
        } catch (e: Exception) {
            Log.e("CourseJsonDataSource", "从备份恢复失败", e)
            emptyList()
        }
    }

    suspend fun saveCourses(courses: List<Course>) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, fileName)
            val backupFile = File(context.filesDir, backupFileName)
            
            if (file.exists()) {
                file.copyTo(backupFile, overwrite = true)
            }
            
            val content = json.encodeToString(courses)
            file.writeText(content)
        } catch (e: Exception) {
            Log.e("CourseJsonDataSource", "Error saving courses", e)
        }
    }
}