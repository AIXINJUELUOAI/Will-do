package com.antgskds.calendarassistant.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    themeColorScheme: ThemeColorScheme = ThemeColorScheme.DEFAULT,
    customThemeColorHex: String = "#6750A4",
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && themeColorScheme == ThemeColorScheme.DEFAULT -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themeColorScheme == ThemeColorScheme.CUSTOM -> {
            ThemeColorGenerator.generateCustomColorScheme(parseThemeHexColor(customThemeColorHex), darkTheme)
        }
        else -> ThemeColorGenerator.generateColorScheme(themeColorScheme.primaryColor, darkTheme, themeColorScheme.name)
    }

    CompositionLocalProvider(LocalRippleConfiguration provides null) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

fun parseThemeHexColor(hex: String, fallback: Color = Color(0xFF6750A4)): Color {
    val normalized = hex.trim().removePrefix("#")
    if (!normalized.matches(Regex("[0-9A-Fa-f]{6}"))) return fallback
    val value = normalized.toLongOrNull(16) ?: return fallback
    return Color((0xFF000000 or value).toInt())
}

fun normalizeThemeHexColor(hex: String): String? {
    val normalized = hex.trim().removePrefix("#")
    if (!normalized.matches(Regex("[0-9A-Fa-f]{6}"))) return null
    return "#${normalized.uppercase()}"
}
