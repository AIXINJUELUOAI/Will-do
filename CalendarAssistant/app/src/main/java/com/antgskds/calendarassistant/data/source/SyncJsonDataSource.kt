package com.antgskds.calendarassistant.data.source

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.data.model.SyncData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * 同步数据源
 * 负责读写 sync_data.json，存储日历同步的配置和映射关系
 */
class SyncJsonDataSource(private val context: Context) {

    private val fileName = "sync_data.json"
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        coerceInputValues = true
    }

    /**
     * 加载同步数据
     */
    suspend fun loadSyncData(): SyncData = withContext(Dispatchers.IO) {
        val file = File(context.filesDir, fileName)
        if (!file.exists()) {
            Log.d("SyncJsonDataSource", "Sync data file not found, returning default")
            return@withContext SyncData()
        }

        try {
            val content = file.readText()
            if (content.isBlank()) {
                Log.d("SyncJsonDataSource", "Sync data file is empty, returning default")
                return@withContext SyncData()
            }
            json.decodeFromString<SyncData>(content)
        } catch (e: Exception) {
            Log.e("SyncJsonDataSource", "Error loading sync data", e)
            SyncData() // 出错时返回默认值
        }
    }

    /**
     * 保存同步数据
     */
    suspend fun saveSyncData(syncData: SyncData) = withContext(Dispatchers.IO) {
        try {
            val file = File(context.filesDir, fileName)
            val content = json.encodeToString(syncData)
            file.writeText(content)
            Log.d("SyncJsonDataSource", "Sync data saved successfully")
        } catch (e: Exception) {
            Log.e("SyncJsonDataSource", "Error saving sync data", e)
        }
    }

    companion object {
        @Volatile
        private var instance: SyncJsonDataSource? = null

        /**
         * 获取单例实例
         */
        fun getInstance(context: Context): SyncJsonDataSource {
            return instance ?: synchronized(this) {
                instance ?: SyncJsonDataSource(context.applicationContext).also { instance = it }
            }
        }
    }
}
