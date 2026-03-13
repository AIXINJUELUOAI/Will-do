package com.antgskds.calendarassistant.data.source

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.core.util.CrashHandler
import com.antgskds.calendarassistant.core.util.DataSanitizer
import com.antgskds.calendarassistant.data.model.MyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class EventJsonDataSource(private val context: Context) {
    private val fileName = "events.json"
    private val backupFileName = "events.json.bak"
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        coerceInputValues = true
    }

    @Volatile
    private var lastCleanupInfo: String = ""

    suspend fun loadEvents(): List<MyEvent> = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return@withContext emptyList()

        try {
            val content = file.readText()
            if (content.isBlank()) return@withContext emptyList()
            
            val events = json.decodeFromString<List<MyEvent>>(content)
            
            val result = DataSanitizer.sanitizeEvents(events)
            
            if (result.removedTitles.isNotEmpty()) {
                val sanitizedContent = json.encodeToString(result.data)
                file.writeText(sanitizedContent)
                lastCleanupInfo = "日程（${result.removedTitles.take(10).joinToString("、")}${if (result.removedTitles.size > 10) "等${result.removedTitles.size}个" else ""}）"
                Log.i("EventJsonDataSource", "数据自愈: 已清理 ${result.removedTitles.size} 条异常日程")
            }
            
            result.data
        } catch (e: Exception) {
            Log.e("EventJsonDataSource", "加载事件失败，尝试读取备份", e)
            lastCleanupInfo = "日程（JSON解析失败）"
            loadFromBackup()
        }
    }

    fun getAndClearCleanupInfo(): String {
        val info = lastCleanupInfo
        lastCleanupInfo = ""
        return info
    }

    private fun loadFromBackup(): List<MyEvent> {
        return try {
            val backupFile = File(context.filesDir, backupFileName)
            if (backupFile.exists()) {
                val content = backupFile.readText()
                if (content.isNotBlank()) {
                    val events = json.decodeFromString<List<MyEvent>>(content)
                    val result = DataSanitizer.sanitizeEvents(events)
                    
                    val currentFile = File(context.filesDir, fileName)
                    currentFile.writeText(json.encodeToString(result.data))
                    
                    Log.i("EventJsonDataSource", "从备份恢复成功")
                    return result.data
                }
            }
            Log.w("EventJsonDataSource", "无备份或备份损坏，返回空列表")
            emptyList()
        } catch (e: Exception) {
            Log.e("EventJsonDataSource", "从备份恢复失败", e)
            emptyList()
        }
    }

    suspend fun saveEvents(events: List<MyEvent>) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, fileName)
            val backupFile = File(context.filesDir, backupFileName)
            
            if (file.exists()) {
                file.copyTo(backupFile, overwrite = true)
            }
            
            val content = json.encodeToString(events)
            file.writeText(content)
        } catch (e: Exception) {
            Log.e("EventJsonDataSource", "Error saving events", e)
        }
    }
}