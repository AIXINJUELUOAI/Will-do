package com.antgskds.calendarassistant.platform.widget

import com.antgskds.calendarassistant.data.model.WidgetThemeMode

enum class WidgetType(val storageKey: String, val displayName: String) {
    SCHEDULE("schedule", "日程小组件"),
    WEATHER("weather", "天气小组件"),
    COURSE("course", "课表小组件");

    companion object {
        fun fromStorageKey(value: String?): WidgetType? = entries.firstOrNull { it.storageKey == value }
    }
}

data class WidgetAppearanceConfig(
    val themeMode: Int = WidgetThemeMode.FOLLOW_APP,
    val backgroundAlpha: Float = 0.9f
) {
    fun normalized(): WidgetAppearanceConfig = copy(
        themeMode = themeMode.coerceIn(WidgetThemeMode.FOLLOW_APP, WidgetThemeMode.DARK),
        backgroundAlpha = backgroundAlpha.coerceIn(0.6f, 1f)
    )
}

data class WidgetInstanceConfig(
    val type: WidgetType,
    val appearance: WidgetAppearanceConfig = WidgetAppearanceConfig(),
    val courseSegment: CourseWidgetSegment = CourseWidgetSegment.MORNING
)

enum class CourseWidgetSegment(val storageKey: String, val displayName: String) {
    MORNING("morning", "上午"),
    AFTERNOON("afternoon", "下午"),
    NIGHT("night", "晚上");

    companion object {
        fun fromStorageKey(value: String?): CourseWidgetSegment = entries.firstOrNull { it.storageKey == value } ?: MORNING
    }
}

enum class WidgetSize(val defaultWidthDp: Int, val defaultHeightDp: Int) {
    CELL_2X1(150, 70),
    CELL_2X2(150, 150),
    CELL_4X2(320, 150),
    CELL_4X4(320, 320)
}

data class WidgetColors(
    val background: Int,
    val card: Int,
    val primaryText: Int,
    val secondaryText: Int,
    val primary: Int
)

data class WidgetTextSizes(
    val dayPx: Float,
    val weekdayPx: Float,
    val lunarPx: Float,
    val groupLabelPx: Float,
    val titlePx: Float,
    val timePx: Float,
    val endTimePx: Float
)
