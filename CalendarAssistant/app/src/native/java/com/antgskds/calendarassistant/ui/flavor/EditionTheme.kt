package com.antgskds.calendarassistant.ui.flavor

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.ui.theme.CalendarAssistantTheme
import com.antgskds.calendarassistant.ui.theme.ThemeColorScheme

@Composable
fun EditionTheme(darkTheme: Boolean, dynamicColor: Boolean, themeColorScheme: ThemeColorScheme, customThemeColorHex: String, content: @Composable () -> Unit) {
    CalendarAssistantTheme(darkTheme, dynamicColor, themeColorScheme, customThemeColorHex, content)
}
