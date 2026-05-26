package com.antgskds.calendarassistant.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.antgskds.calendarassistant.materialcolor.hct.Hct
import com.antgskds.calendarassistant.materialcolor.scheme.SchemeTonalSpot
import kotlin.math.pow

object ThemeColorGenerator {

    fun generateColorScheme(
        seedColor: Color,
        darkTheme: Boolean,
        themeColorSchemeName: String = ""
    ): ColorScheme {
        val seedInt = seedColor.toArgb()
        val hct = Hct.fromInt(seedInt)
        val scheme = SchemeTonalSpot(hct, darkTheme, 0.0)

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

    fun generateCustomColorScheme(
        seedColor: Color,
        darkTheme: Boolean
    ): ColorScheme {
        val primary = seedColor.copy(alpha = 1f)
        val base = generateColorScheme(primary, darkTheme, ThemeColorScheme.CUSTOM.name)
        val primaryContainer = customContainer(primary, darkTheme, 0.55f, 0.78f)
        val secondary = blend(primary, if (darkTheme) Color.White else Color.Black, 0.16f)
        val secondaryContainer = customContainer(primary, darkTheme, 0.68f, 0.86f)
        val tertiary = blend(primary, if (darkTheme) Color.White else Color.Black, 0.08f)
        val tertiaryContainer = customContainer(primary, darkTheme, 0.74f, 0.9f)
        val background = customSurface(primary, darkTheme, 0)
        val surface = customSurface(primary, darkTheme, 1)
        val surfaceContainerLowest = customSurface(primary, darkTheme, 0)
        val surfaceContainerLow = customSurface(primary, darkTheme, 2)
        val surfaceContainer = customSurface(primary, darkTheme, 3)
        val surfaceContainerHigh = customSurface(primary, darkTheme, 4)
        val surfaceContainerHighest = customSurface(primary, darkTheme, 5)
        val surfaceDim = customSurface(primary, darkTheme, 5)
        val surfaceBright = customSurface(primary, darkTheme, 1)
        val surfaceVariant = customContainer(primary, darkTheme, 0.88f, 0.93f)
        val outline = if (darkTheme) blend(primary, Color.White, 0.35f) else blend(primary, Color.Black, 0.28f)
        val outlineVariant = customContainer(primary, darkTheme, 0.8f, 0.84f)
        return base.copy(
            primary = primary,
            onPrimary = bestContentColor(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = bestContentColor(primaryContainer),
            secondary = secondary,
            onSecondary = bestContentColor(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = bestContentColor(secondaryContainer),
            tertiary = tertiary,
            onTertiary = bestContentColor(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = bestContentColor(tertiaryContainer),
            background = background,
            onBackground = bestContentColor(background),
            surface = surface,
            onSurface = bestContentColor(surface),
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = bestContentColor(surfaceVariant),
            outline = outline,
            outlineVariant = outlineVariant,
            surfaceContainerLowest = surfaceContainerLowest,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            surfaceDim = surfaceDim,
            surfaceBright = surfaceBright,
            surfaceTint = primary,
            inversePrimary = primary
        )
    }

    private fun customContainer(
        color: Color,
        darkTheme: Boolean,
        darkBlackFraction: Float,
        lightWhiteFraction: Float
    ): Color {
        return if (darkTheme) blend(color, Color.Black, darkBlackFraction) else blend(color, Color.White, lightWhiteFraction)
    }

    private fun customSurface(color: Color, darkTheme: Boolean, level: Int): Color {
        return if (darkTheme) {
            val fraction = when (level.coerceIn(0, 5)) {
                0 -> 0.96f
                1 -> 0.94f
                2 -> 0.91f
                3 -> 0.88f
                4 -> 0.84f
                else -> 0.80f
            }
            blend(color, Color.Black, fraction)
        } else {
            val fraction = when (level.coerceIn(0, 5)) {
                0 -> 0.99f
                1 -> 0.98f
                2 -> 0.965f
                3 -> 0.95f
                4 -> 0.93f
                else -> 0.91f
            }
            blend(color, Color.White, fraction)
        }
    }

    private fun blend(color: Color, target: Color, targetFraction: Float): Color {
        val fraction = targetFraction.coerceIn(0f, 1f)
        return Color(
            red = color.red + (target.red - color.red) * fraction,
            green = color.green + (target.green - color.green) * fraction,
            blue = color.blue + (target.blue - color.blue) * fraction,
            alpha = 1f
        )
    }

    private fun bestContentColor(background: Color): Color {
        return if (relativeLuminance(background) > 0.5f) Color.Black else Color.White
    }

    private fun relativeLuminance(color: Color): Float {
        fun channel(value: Float): Float {
            return if (value <= 0.03928f) value / 12.92f else ((value + 0.055f) / 1.055f).pow(2.4f)
        }
        return 0.2126f * channel(color.red) + 0.7152f * channel(color.green) + 0.0722f * channel(color.blue)
    }
}
