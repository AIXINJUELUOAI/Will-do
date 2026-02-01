package com.antgskds.calendarassistant.data.source

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.data.model.MyEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 归档事件数据源
 * 负责从 archives.json 读取和写入归档事件
 */
class ArchiveJsonDataSource(private val context: Context) {
    private val fileName = "archives.json"
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        coerceInputValues = true
    }

    suspend fun loadArchivedEvents(): List<MyEvent> = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) return@withContext emptyList()

        try {
            val content = file.readText()
            if (content.isBlank()) return@withContext emptyList()
            json.decodeFromString<List<MyEvent>>(content)
        } catch (e: Exception) {
            Log.e("ArchiveJsonDataSource", "Error loading archived events", e)
            emptyList()
        }
    }

    suspend fun saveArchivedEvents(events: List<MyEvent>) = withContext(Dispatchers.IO) {
        try {
            val content = json.encodeToString(events)
            val file = File(context.filesDir, fileName)

            // 🔥 修复：原子写入 (Atomic Write)
            val tmpFile = File(context.filesDir, "$fileName.tmp")
            tmpFile.writeText(content)

            // Android O (API 26+) 支持 Files.move 原子操作，或者是简单的 renameTo
            if (tmpFile.renameTo(file)) {
                // 成功
            } else {
                // 如果重命名失败（极少见），尝试回退到直接写入
                file.writeText(content)
                tmpFile.delete()
            }
        } catch (e: Exception) {
            Log.e("ArchiveJsonDataSource", "Error saving archived events", e)
        }
    }
}
