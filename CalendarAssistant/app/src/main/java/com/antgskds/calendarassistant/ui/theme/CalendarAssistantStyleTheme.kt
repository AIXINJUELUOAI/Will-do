package com.antgskds.calendarassistant.ui.theme

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.ui.flavor.EditionTheme

@Composable
fun CalendarAssistantStyleTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    themeColorScheme: ThemeColorScheme,
    customThemeColorHex: String,
    content: @Composable () -> Unit
) {
    EditionTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        themeColorScheme = themeColorScheme,
        customThemeColorHex = customThemeColorHex,
        content = content
    )
}
