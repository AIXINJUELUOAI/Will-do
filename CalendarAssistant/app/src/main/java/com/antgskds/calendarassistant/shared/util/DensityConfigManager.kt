package com.antgskds.calendarassistant.shared.util

import android.content.Context
import android.content.res.Resources
import kotlin.math.roundToInt

object DensityConfigManager {
    const val PREFS_NAME = "app_settings"
    const val KEY_UI_SIZE = "key_ui_size_independent"
    const val KEY_UI_SCALE_SMALL = "key_ui_scale_small"
    const val KEY_UI_SCALE_MEDIUM = "key_ui_scale_medium"
    const val KEY_UI_SCALE_LARGE = "key_ui_scale_large"

    const val DEFAULT_SCALE_SMALL = 0.75f
    const val DEFAULT_SCALE_MEDIUM = 0.80f
    const val DEFAULT_SCALE_LARGE = 0.85f
    const val MIN_SCALE = 0.65f
    const val MAX_SCALE = 1.00f
    
    /**
     * UI 大小索引 -> 缩放系数
     * 保持 1-3 档位，映射为相对于设备原生密度的缩放
     * 
     * 1 (小): 0.75f  - 比设备原生小 25%
     * 2 (中): 0.80f  - 比设备原生小 20%
     * 3 (大): 0.85f  - 比设备原生小 15%
     */
    fun getScaleFactor(uiSize: Int): Float = when (uiSize) {
        1 -> DEFAULT_SCALE_SMALL
        2 -> DEFAULT_SCALE_MEDIUM
        3 -> DEFAULT_SCALE_LARGE
        else -> DEFAULT_SCALE_LARGE
    }

    /** Reads the app-only custom scale before the regular settings state is available. */
    fun getAppScaleFactor(context: Context, uiSize: Int): Float {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val key = when (uiSize) {
            1 -> KEY_UI_SCALE_SMALL
            2 -> KEY_UI_SCALE_MEDIUM
            else -> KEY_UI_SCALE_LARGE
        }
        return prefs.getFloat(key, getScaleFactor(uiSize)).coerceIn(MIN_SCALE, MAX_SCALE)
    }

    /**
     * 获取设备出厂默认的 density 和 densityDpi
     * 使用 Resources.getSystem() 获取系统原生值
     */
    fun getSystemDensity(): Pair<Float, Int> {
        val metrics = Resources.getSystem().displayMetrics
        return metrics.density to metrics.densityDpi
    }

    /**
     * 计算目标密度值
     * @param uiSize 用户选择的 UI 大小索引
     * @return Triple<density, densityDpi, scaledDensity>
     */
    fun calculateTargetDensity(uiSize: Int): Triple<Float, Int, Float> {
        val (systemDensity, systemDpi) = getSystemDensity()
        val systemConfig = Resources.getSystem().configuration
        val scale = getScaleFactor(uiSize)

        val targetDensity = systemDensity * scale
        val targetDpi = (systemDpi * scale).roundToInt()
        val targetScaledDensity = targetDensity * systemConfig.fontScale

        return Triple(targetDensity, targetDpi, targetScaledDensity)
    }

    /**
     * 从 SharedPreferences 快速获取 uiSize
     * 支持独立 key 和 JSON 两种方式的兼容读取
     */
    fun getUiSizeFromPrefs(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // 1. 优先尝试读取独立 Key (极快)
        var uiSize = prefs.getInt(KEY_UI_SIZE, -1)

        // 2. 如果独立 Key 不存在 (老用户升级)，则降级读取 JSON
        if (uiSize == -1) {
            val jsonString = prefs.getString("settings_json", null)
            uiSize = if (jsonString != null) {
                // 从 JSON 中提取 uiSize
                val regex = """"uiSize"\s*:\s*(\d+)""".toRegex()
                val match = regex.find(jsonString)
                match?.groupValues?.get(1)?.toIntOrNull() ?: 2
            } else {
                2 // 全新用户，默认中等
            }
        }

        return uiSize
    }
}
