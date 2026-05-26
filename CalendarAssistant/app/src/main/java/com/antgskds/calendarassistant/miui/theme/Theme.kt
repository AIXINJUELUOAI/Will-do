package com.antgskds.calendarassistant.miui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.ui.theme.ThemeColorScheme

@Composable
fun CalendarAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    themeColorScheme: ThemeColorScheme = ThemeColorScheme.DEFAULT,
    customThemeColorHex: String = "#6750A4",
    content: @Composable () -> Unit
) {
    com.antgskds.calendarassistant.ui.theme.CalendarAssistantTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        themeColorScheme = themeColorScheme,
        customThemeColorHex = customThemeColorHex,
        content = content
    )
}
