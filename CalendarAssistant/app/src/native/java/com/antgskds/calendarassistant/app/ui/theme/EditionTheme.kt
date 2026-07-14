package com.antgskds.calendarassistant.app.ui.theme

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.ui.theme.CalendarAssistantTheme

@Composable
fun EditionTheme(darkTheme: Boolean, dynamicColor: Boolean, themeColorScheme: ThemeColorScheme, customThemeColorHex: String, content: @Composable () -> Unit) {
    CalendarAssistantTheme(darkTheme, dynamicColor, themeColorScheme, customThemeColorHex, content)
}
