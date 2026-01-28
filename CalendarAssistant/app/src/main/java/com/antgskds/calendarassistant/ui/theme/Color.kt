package com.antgskds.calendarassistant.ui.theme

import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

// 移植的颜色逻辑
val EventColors = listOf(
    Color(0xFF91A3B0), Color(0xFFB4C3A1), Color(0xFFD1B29E),
    Color(0xFF968D8D), Color(0xFFBCCAD6), Color(0xFFCFD1D3),
    Color(0xFFA2B5BB), Color(0xFFE2C4C4)
)

// APP 内创建日程使用的颜色（不包含青灰色，青灰色留给系统日历同步的日程）
val AppEventColors = listOf(
    Color(0xFF91A3B0), Color(0xFFB4C3A1), Color(0xFFD1B29E),
    Color(0xFF968D8D), Color(0xFFBCCAD6), Color(0xFFCFD1D3),
    Color(0xFFE2C4C4)
)

fun getNextColor(index: Int): Color = EventColors[index % EventColors.size]

fun getNextAppColor(index: Int): Color = AppEventColors[index % AppEventColors.size]