package com.antgskds.calendarassistant.ui.theme

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.data.model.UiStyle

@Composable
fun CalendarAssistantStyleTheme(
    uiStyle: UiStyle,
    darkTheme: Boolean,
    dynamicColor: Boolean,
    themeColorScheme: ThemeColorScheme,
    customThemeColorHex: String,
    content: @Composable () -> Unit
) {
    when (uiStyle) {
        UiStyle.MIUI -> com.antgskds.calendarassistant.miui.theme.CalendarAssistantTheme(
            darkTheme = darkTheme,
            dynamicColor = dynamicColor,
            themeColorScheme = themeColorScheme,
            customThemeColorHex = customThemeColorHex,
            content = content
        )
        UiStyle.MATERIAL3 -> CalendarAssistantTheme(
            darkTheme = darkTheme,
            dynamicColor = dynamicColor,
            themeColorScheme = themeColorScheme,
            customThemeColorHex = customThemeColorHex,
            content = content
        )
    }
}
