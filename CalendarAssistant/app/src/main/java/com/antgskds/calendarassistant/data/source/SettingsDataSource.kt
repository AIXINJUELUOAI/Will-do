package com.antgskds.calendarassistant.data.source

import android.content.Context
import com.antgskds.calendarassistant.data.model.MySettings
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 设置数据源：负责从 SharedPreferences 读取数据
 * 包含从旧版 (v2) 到新版 (v3 JSON) 的自动迁移逻辑
 */
class SettingsDataSource(context: Context) {
    // 必须保持和旧版一致的名字 "app_settings"
    private val prefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true // 容错处理
    }

    private val KEY_JSON = "settings_json"

    /**
     * 读取设置（含自动迁移逻辑）
     */
    fun loadSettings(): MySettings {
        // 1. 尝试读取新版 JSON
        val jsonString = prefs.getString(KEY_JSON, null)
        if (jsonString != null) {
            return try {
                json.decodeFromString<MySettings>(jsonString)
            } catch (e: Exception) {
                e.printStackTrace()
                MySettings() // 解析失败，返回默认
            }
        }

        // 2. 如果没有 JSON，检查是否有旧版数据 (Migration)
        // 检查一个典型的旧版 Key，例如 "model_key" 或 "semester_start_date"
        if (prefs.contains("model_key") || prefs.contains("semester_start_date")) {
            val migratedSettings = migrateFromLegacy()
            // 立即保存为新格式，下次启动就会走步骤 1
            saveSettings(migratedSettings)
            return migratedSettings
        }

        // 3. 全新用户
        return MySettings()
    }

    /**
     * 保存设置（统一存为 JSON）
     */
    fun saveSettings(settings: MySettings) {
        try {
            val jsonString = json.encodeToString(settings)
            prefs.edit().putString(KEY_JSON, jsonString).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 从旧版 SharedPreferences 字段手动迁移数据
     */
    private fun migrateFromLegacy(): MySettings {
        // 旧版没有 pickupAggregation，这里使用默认值 false
        return MySettings(
            modelKey = prefs.getString("model_key", "") ?: "",
            modelName = prefs.getString("model_name", "") ?: "gpt-3.5-turbo",
            modelUrl = prefs.getString("model_url", "") ?: "",
            modelProvider = prefs.getString("model_provider", "") ?: "",

            showTomorrowEvents = prefs.getBoolean("show_tomorrow_events", false),
            isDailySummaryEnabled = prefs.getBoolean("daily_summary_enabled", false),

            tempEventsUseRecognitionTime = prefs.getBoolean("temp_events_use_rec_time", true),
            screenshotDelayMs = prefs.getLong("screenshot_delay_ms", 500L),
            isLiveCapsuleEnabled = prefs.getBoolean("live_capsule_enabled", false),

            // 新字段，旧数据中不存在，使用默认值
            isPickupAggregationEnabled = false,

            semesterStartDate = prefs.getString("semester_start_date", "") ?: "",
            totalWeeks = prefs.getInt("semester_total_weeks", 20),
            timeTableJson = prefs.getString("time_table_json", "") ?: ""
        )
    }
}