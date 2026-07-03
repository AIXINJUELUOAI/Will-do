package com.antgskds.calendarassistant.platform.widget

import android.content.Context
import com.antgskds.calendarassistant.data.model.MySettings

class WidgetInstanceConfigStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getConfig(appWidgetId: Int, fallbackType: WidgetType, settings: MySettings): WidgetInstanceConfig {
        val prefix = keyPrefix(appWidgetId)
        val storedType = WidgetType.fromStorageKey(prefs.getString(prefix + KEY_TYPE, null)) ?: fallbackType
        val themeMode = prefs.getInt(prefix + KEY_THEME_MODE, defaultAppearance(fallbackType, settings).themeMode)
        val alpha = prefs.getFloat(prefix + KEY_ALPHA, defaultAppearance(fallbackType, settings).backgroundAlpha)
        val courseSegment = CourseWidgetSegment.fromStorageKey(prefs.getString(prefix + KEY_COURSE_SEGMENT, null))
        return WidgetInstanceConfig(
            type = storedType,
            appearance = WidgetAppearanceConfig(themeMode, alpha).normalized(),
            courseSegment = courseSegment
        )
    }

    fun ensureConfig(appWidgetId: Int, type: WidgetType, settings: MySettings): WidgetInstanceConfig {
        val prefix = keyPrefix(appWidgetId)
        if (!prefs.contains(prefix + KEY_TYPE)) {
            val appearance = defaultAppearance(type, settings).normalized()
            prefs.edit()
                .putString(prefix + KEY_TYPE, type.storageKey)
                .putInt(prefix + KEY_THEME_MODE, appearance.themeMode)
                .putFloat(prefix + KEY_ALPHA, appearance.backgroundAlpha)
                .putString(prefix + KEY_COURSE_SEGMENT, CourseWidgetSegment.MORNING.storageKey)
                .apply()
            return WidgetInstanceConfig(type, appearance)
        }
        return getConfig(appWidgetId, type, settings)
    }

    fun saveConfig(appWidgetId: Int, config: WidgetInstanceConfig) {
        val prefix = keyPrefix(appWidgetId)
        val appearance = config.appearance.normalized()
        prefs.edit()
            .putString(prefix + KEY_TYPE, config.type.storageKey)
            .putInt(prefix + KEY_THEME_MODE, appearance.themeMode)
            .putFloat(prefix + KEY_ALPHA, appearance.backgroundAlpha)
            .putString(prefix + KEY_COURSE_SEGMENT, config.courseSegment.storageKey)
                .apply()
    }

    fun saveDefaultAppearance(type: WidgetType, appearance: WidgetAppearanceConfig) {
        val prefix = defaultKeyPrefix(type)
        val normalized = appearance.normalized()
        prefs.edit()
            .putInt(prefix + KEY_THEME_MODE, normalized.themeMode)
            .putFloat(prefix + KEY_ALPHA, normalized.backgroundAlpha)
            .apply()
    }

    fun deleteConfig(appWidgetId: Int) {
        val prefix = keyPrefix(appWidgetId)
        prefs.edit()
            .remove(prefix + KEY_TYPE)
            .remove(prefix + KEY_THEME_MODE)
            .remove(prefix + KEY_ALPHA)
            .remove(prefix + KEY_COURSE_SEGMENT)
            .apply()
    }

    fun defaultAppearance(type: WidgetType, settings: MySettings): WidgetAppearanceConfig {
        val fallback = WidgetAppearanceConfig(settings.widgetThemeMode, settings.widgetBackgroundAlpha).normalized()
        val prefix = defaultKeyPrefix(type)
        if (!prefs.contains(prefix + KEY_THEME_MODE) && !prefs.contains(prefix + KEY_ALPHA)) return fallback
        return WidgetAppearanceConfig(
            themeMode = prefs.getInt(prefix + KEY_THEME_MODE, fallback.themeMode),
            backgroundAlpha = prefs.getFloat(prefix + KEY_ALPHA, fallback.backgroundAlpha)
        ).normalized()
    }

    private fun keyPrefix(appWidgetId: Int): String = "widget_$appWidgetId."
    private fun defaultKeyPrefix(type: WidgetType): String = "default_${type.storageKey}."

    private companion object {
        const val PREFS_NAME = "widget_instance_config"
        const val KEY_TYPE = "type"
        const val KEY_THEME_MODE = "theme_mode"
        const val KEY_ALPHA = "alpha"
        const val KEY_COURSE_SEGMENT = "course_segment"
    }
}
