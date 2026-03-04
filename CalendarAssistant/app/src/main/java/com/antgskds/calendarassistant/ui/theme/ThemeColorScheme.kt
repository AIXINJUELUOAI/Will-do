package com.antgskds.calendarassistant.ui.theme

import androidx.compose.ui.graphics.Color

enum class ThemeColorScheme(
    val displayName: String,
    val primaryColor: Color
) {
    PURPLE("经典紫", Color(0xFF6750A4)),
    INDIGO("靛蓝", Color(0xFF3F51B5)),
    BLUE_GREY("湖水蓝", Color(0xFF006D77)),
    TEAL("薄荷青", Color(0xFF00796B)),
    BLUE("晴空蓝", Color(0xFF1565C0)),
    CYAN("青蓝", Color(0xFF00838F)),
    GREEN("清新绿", Color(0xFF2E7D32)),
    AMBER("琥珀黄", Color(0xFFF9A825)),
    ORANGE("活力橙", Color(0xFFEF6C00)),
    RED("朱红", Color(0xFFD84315)),
    PURPLE_DEEP("冷紫", Color(0xFF4C1D95)),
    BLUE_DEEP("深海蓝", Color(0xFF1E3A8A)),
    LIME("青柠绿", Color(0xFF558B2F)),
    GOLDEN("蜂蜜金", Color(0xFFC77800)),
    PINK("樱桃红", Color(0xFFC2185B)),
    DEFAULT("跟随系统", Color(0xFF6750A4));

    companion object {
        fun fromName(name: String): ThemeColorScheme {
            return entries.find { it.name == name } ?: DEFAULT
        }
    }
}
