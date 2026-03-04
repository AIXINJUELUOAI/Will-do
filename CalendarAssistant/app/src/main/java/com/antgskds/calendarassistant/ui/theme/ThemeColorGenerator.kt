package com.antgskds.calendarassistant.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.antgskds.calendarassistant.materialcolor.hct.Hct
import com.antgskds.calendarassistant.materialcolor.scheme.SchemeMonochrome
import com.antgskds.calendarassistant.materialcolor.scheme.SchemeTonalSpot

private val LOW_CHROMA_SCHEMES = emptySet<String>()

object ThemeColorGenerator {

    fun generateColorScheme(
        seedColor: Color,
        darkTheme: Boolean,
        themeColorSchemeName: String = ""
    ): ColorScheme {
        val seedInt = seedColor.toArgb()
        val hct = Hct.fromInt(seedInt)
        val scheme = if (themeColorSchemeName in LOW_CHROMA_SCHEMES) {
            SchemeMonochrome(hct, darkTheme, 0.0)
        } else {
            SchemeTonalSpot(hct, darkTheme, 0.0)
        }

        return if (darkTheme) {
            darkColorScheme(
                primary = Color(scheme.primary),
                onPrimary = Color(scheme.onPrimary),
                primaryContainer = Color(scheme.primaryContainer),
                onPrimaryContainer = Color(scheme.onPrimaryContainer),
                secondary = Color(scheme.secondary),
                onSecondary = Color(scheme.onSecondary),
                secondaryContainer = Color(scheme.secondaryContainer),
                onSecondaryContainer = Color(scheme.onSecondaryContainer),
                tertiary = Color(scheme.tertiary),
                onTertiary = Color(scheme.onTertiary),
                tertiaryContainer = Color(scheme.tertiaryContainer),
                onTertiaryContainer = Color(scheme.onTertiaryContainer),
                error = Color(scheme.error),
                onError = Color(scheme.onError),
                errorContainer = Color(scheme.errorContainer),
                onErrorContainer = Color(scheme.onErrorContainer),
                background = Color(scheme.background),
                onBackground = Color(scheme.onBackground),
                surface = Color(scheme.surface),
                onSurface = Color(scheme.onSurface),
                surfaceVariant = Color(scheme.surfaceVariant),
                onSurfaceVariant = Color(scheme.onSurfaceVariant),
                outline = Color(scheme.outline),
                outlineVariant = Color(scheme.outlineVariant),
                scrim = Color(scheme.scrim),
                inverseSurface = Color(scheme.inverseSurface),
                inverseOnSurface = Color(scheme.inverseOnSurface),
                inversePrimary = Color(scheme.inversePrimary),
                surfaceTint = Color(scheme.surfaceTint),
                surfaceContainerLowest = Color(scheme.surfaceContainerLowest),
                surfaceContainerLow = Color(scheme.surfaceContainerLow),
                surfaceContainer = Color(scheme.surfaceContainer),
                surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
                surfaceContainerHighest = Color(scheme.surfaceContainerHighest),
                surfaceDim = Color(scheme.surfaceDim),
                surfaceBright = Color(scheme.surfaceBright),
            )
        } else {
            lightColorScheme(
                primary = Color(scheme.primary),
                onPrimary = Color(scheme.onPrimary),
                primaryContainer = Color(scheme.primaryContainer),
                onPrimaryContainer = Color(scheme.onPrimaryContainer),
                secondary = Color(scheme.secondary),
                onSecondary = Color(scheme.onSecondary),
                secondaryContainer = Color(scheme.secondaryContainer),
                onSecondaryContainer = Color(scheme.onSecondaryContainer),
                tertiary = Color(scheme.tertiary),
                onTertiary = Color(scheme.onTertiary),
                tertiaryContainer = Color(scheme.tertiaryContainer),
                onTertiaryContainer = Color(scheme.onTertiaryContainer),
                error = Color(scheme.error),
                onError = Color(scheme.onError),
                errorContainer = Color(scheme.errorContainer),
                onErrorContainer = Color(scheme.onErrorContainer),
                background = Color(scheme.background),
                onBackground = Color(scheme.onBackground),
                surface = Color(scheme.surface),
                onSurface = Color(scheme.onSurface),
                surfaceVariant = Color(scheme.surfaceVariant),
                onSurfaceVariant = Color(scheme.onSurfaceVariant),
                outline = Color(scheme.outline),
                outlineVariant = Color(scheme.outlineVariant),
                scrim = Color(scheme.scrim),
                inverseSurface = Color(scheme.inverseSurface),
                inverseOnSurface = Color(scheme.inverseOnSurface),
                inversePrimary = Color(scheme.inversePrimary),
                surfaceTint = Color(scheme.surfaceTint),
                surfaceContainerLowest = Color(scheme.surfaceContainerLowest),
                surfaceContainerLow = Color(scheme.surfaceContainerLow),
                surfaceContainer = Color(scheme.surfaceContainer),
                surfaceContainerHigh = Color(scheme.surfaceContainerHigh),
                surfaceContainerHighest = Color(scheme.surfaceContainerHighest),
                surfaceDim = Color(scheme.surfaceDim),
                surfaceBright = Color(scheme.surfaceBright),
            )
        }
    }
}
