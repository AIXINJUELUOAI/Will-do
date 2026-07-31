package com.antgskds.calendarassistant.app.ui.theme

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.app.ui.theme.material.CalendarAssistantTheme
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

@Composable
fun EditionTheme(darkTheme: Boolean, dynamicColor: Boolean, themeColorScheme: ThemeColorScheme, customThemeColorHex: String, content: @Composable () -> Unit) {
    CalendarAssistantTheme(
        darkTheme = darkTheme,
        dynamicColor = false,
        themeColorScheme = ThemeColorScheme.CUSTOM,
        customThemeColorHex = "#3482FF",
    ) {
        MiuixTheme(
            colors = if (darkTheme) darkColorScheme() else lightColorScheme(),
            content = content,
        )
    }
}
