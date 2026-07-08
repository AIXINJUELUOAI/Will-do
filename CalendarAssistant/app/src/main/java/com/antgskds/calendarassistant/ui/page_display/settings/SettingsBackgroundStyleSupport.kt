package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.data.model.MySettings
import kotlin.math.roundToInt

data class AppBackgroundStylePalette(
    val surface: Color,
    val surfaceStrong: Color,
    val content: Color,
    val secondaryContent: Color,
    val accent: Color,
    val accentContent: Color,
    val outline: Color,
    val blurEnabled: Boolean,
    val blurRadius: Dp
)

val LocalAppBackgroundWallpaperBitmap = staticCompositionLocalOf<ImageBitmap?> { null }
val LocalAppBackgroundRootSize = staticCompositionLocalOf { IntSize.Zero }
val LocalAppBackgroundStyleEnabled = staticCompositionLocalOf { false }
val LocalAppBackgroundMiuiBlurEnabled = staticCompositionLocalOf { false }
val LocalAppBackgroundCardAlphaPercent = staticCompositionLocalOf {
    MySettings.APP_BACKGROUND_CARD_ALPHA_DEFAULT_PERCENT
}

@Composable
fun AppBackgroundStyleTheme(
    enabled: Boolean,
    miuiBlurEnabled: Boolean = false,
    cardAlphaPercent: Int = MySettings.APP_BACKGROUND_CARD_ALPHA_DEFAULT_PERCENT,
    content: @Composable () -> Unit
) {
    val normalizedCardAlphaPercent = MySettings.normalizeAppBackgroundCardAlphaPercent(cardAlphaPercent)
    CompositionLocalProvider(
        LocalAppBackgroundStyleEnabled provides enabled,
        LocalAppBackgroundMiuiBlurEnabled provides (enabled && miuiBlurEnabled),
        LocalAppBackgroundCardAlphaPercent provides normalizedCardAlphaPercent
    ) {
        if (!enabled) {
            content()
        } else {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.appBackgroundColorScheme(
                    miuiBlurEnabled = miuiBlurEnabled,
                    cardAlphaPercent = normalizedCardAlphaPercent
                ),
                typography = MaterialTheme.typography,
                shapes = MaterialTheme.shapes,
                content = content
            )
        }
    }
}

@Composable
fun SettingsBackgroundStyleTheme(
    enabled: Boolean,
    miuiBlurEnabled: Boolean = false,
    cardAlphaPercent: Int = MySettings.APP_BACKGROUND_CARD_ALPHA_DEFAULT_PERCENT,
    content: @Composable () -> Unit
) {
    AppBackgroundStyleTheme(
        enabled = enabled,
        miuiBlurEnabled = miuiBlurEnabled,
        cardAlphaPercent = cardAlphaPercent,
        content = content
    )
}

@Composable
fun rememberAppBackgroundStylePalette(
    enabled: Boolean = true,
    miuiBlurEnabled: Boolean = false,
    cardAlphaPercent: Int? = null
): AppBackgroundStylePalette {
    if (!enabled) {
        val scheme = MaterialTheme.colorScheme
        return AppBackgroundStylePalette(
            surface = scheme.surfaceContainerLow,
            surfaceStrong = scheme.surfaceContainer,
            content = scheme.onSurface,
            secondaryContent = scheme.onSurfaceVariant,
            accent = scheme.primaryContainer,
            accentContent = scheme.onPrimaryContainer,
            outline = scheme.outlineVariant,
            blurEnabled = false,
            blurRadius = 0.dp
        )
    }

    val dark = MaterialTheme.colorScheme.isCurrentThemeDarkGlass()
    val effectiveCardAlphaPercent = cardAlphaPercent
        ?.let(MySettings::normalizeAppBackgroundCardAlphaPercent)
        ?: LocalAppBackgroundCardAlphaPercent.current
    return if (dark) {
        AppBackgroundStylePalette(
            surface = Color.Black.copy(
                alpha = appBackgroundSurfaceAlpha(effectiveCardAlphaPercent, dark = true, miuiBlurEnabled = miuiBlurEnabled)
            ),
            surfaceStrong = Color.Black.copy(
                alpha = appBackgroundSurfaceAlpha(
                    effectiveCardAlphaPercent,
                    dark = true,
                    miuiBlurEnabled = miuiBlurEnabled,
                    strong = true
                )
            ),
            content = Color.White,
            secondaryContent = Color.White.copy(alpha = 0.74f),
            accent = Color.White.copy(alpha = 0.18f),
            accentContent = Color.White,
            outline = Color.White.copy(alpha = if (miuiBlurEnabled) 0.26f else 0.18f),
            blurEnabled = miuiBlurEnabled,
            blurRadius = 28.dp
        )
    } else {
        AppBackgroundStylePalette(
            surface = Color.White.copy(
                alpha = appBackgroundSurfaceAlpha(effectiveCardAlphaPercent, dark = false, miuiBlurEnabled = miuiBlurEnabled)
            ),
            surfaceStrong = Color.White.copy(
                alpha = appBackgroundSurfaceAlpha(
                    effectiveCardAlphaPercent,
                    dark = false,
                    miuiBlurEnabled = miuiBlurEnabled,
                    strong = true
                )
            ),
            content = Color(0xFF15171C),
            secondaryContent = Color(0xFF15171C).copy(alpha = 0.68f),
            accent = Color.Black.copy(alpha = 0.08f),
            accentContent = Color(0xFF15171C),
            outline = Color.White.copy(alpha = if (miuiBlurEnabled) 0.42f else 0.30f),
            blurEnabled = miuiBlurEnabled,
            blurRadius = 28.dp
        )
    }
}

fun appBackgroundSurfaceAlpha(
    cardAlphaPercent: Int,
    dark: Boolean,
    miuiBlurEnabled: Boolean,
    strong: Boolean = false
): Float {
    val offset = when {
        dark && miuiBlurEnabled && strong -> -0.22f
        dark && miuiBlurEnabled -> -0.32f
        dark && strong -> -0.04f
        dark -> -0.12f
        miuiBlurEnabled && strong -> -0.08f
        miuiBlurEnabled -> -0.20f
        strong -> 0.10f
        else -> 0f
    }
    return appBackgroundAlphaWithOffset(cardAlphaPercent, offset)
}

fun Modifier.appBackgroundGlass(
    palette: AppBackgroundStylePalette,
    shape: Shape = RoundedCornerShape(16.dp),
    borderWidth: Dp = 1.dp
): Modifier {
    return this
        .clip(shape)
        .background(palette.surface, shape)
        .border(borderWidth, palette.outline, shape)
}

@Composable
fun AppBackgroundGlassSurface(
    enabled: Boolean? = null,
    miuiBlurEnabled: Boolean? = null,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    borderWidth: Dp = 1.dp,
    content: @Composable () -> Unit
) {
    val effectiveEnabled = enabled ?: LocalAppBackgroundStyleEnabled.current
    val effectiveMiuiBlurEnabled = miuiBlurEnabled ?: LocalAppBackgroundMiuiBlurEnabled.current
    val palette = rememberAppBackgroundStylePalette(
        enabled = effectiveEnabled,
        miuiBlurEnabled = effectiveMiuiBlurEnabled
    )
    if (!effectiveEnabled) {
        Card(
            modifier = modifier,
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = palette.surface,
                contentColor = palette.content
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            content()
        }
        return
    }

    Box(
        modifier = modifier
            .clip(shape)
            .border(borderWidth, palette.outline, shape)
    ) {
        if (effectiveEnabled && palette.blurEnabled) {
            AppBackgroundBlurredBackdrop(
                modifier = Modifier.matchParentSize(),
                shape = shape,
                blurRadius = palette.blurRadius
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(palette.surface, shape)
        )
        content()
    }
}

@Composable
private fun AppBackgroundBlurredBackdrop(
    modifier: Modifier,
    shape: Shape,
    blurRadius: Dp
) {
    val wallpaper = LocalAppBackgroundWallpaperBitmap.current ?: return
    val rootSize = LocalAppBackgroundRootSize.current
    if (rootSize.width <= 0 || rootSize.height <= 0) return

    val density = LocalDensity.current
    val blurRadiusPx = with(density) { blurRadius.toPx() }
    val rootWidth = with(density) { rootSize.width.toDp() }
    val rootHeight = with(density) { rootSize.height.toDp() }
    var positionInRoot by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = modifier
            .clip(shape)
            .onGloballyPositioned { coordinates ->
                positionInRoot = coordinates.positionInRoot()
            }
    ) {
        Image(
            bitmap = wallpaper,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = rootWidth, height = rootHeight)
                .offset {
                    IntOffset(
                        x = -positionInRoot.x.roundToInt(),
                        y = -positionInRoot.y.roundToInt()
                    )
                }
                .graphicsLayer {
                    renderEffect = BlurEffect(
                        radiusX = blurRadiusPx,
                        radiusY = blurRadiusPx,
                        edgeTreatment = TileMode.Clamp
                    )
                }
        )
    }
}

private fun ColorScheme.appBackgroundColorScheme(
    miuiBlurEnabled: Boolean,
    cardAlphaPercent: Int
): ColorScheme {
    return if (isCurrentThemeDarkGlass()) {
        darkAppBackgroundColorScheme(miuiBlurEnabled, cardAlphaPercent)
    } else {
        lightAppBackgroundColorScheme(miuiBlurEnabled, cardAlphaPercent)
    }
}

private fun ColorScheme.darkAppBackgroundColorScheme(
    miuiBlurEnabled: Boolean,
    cardAlphaPercent: Int
): ColorScheme {
    val primaryText = Color.White
    val secondaryText = Color.White.copy(alpha = 0.74f)
    val surfaceAlpha = appBackgroundAlphaWithOffset(cardAlphaPercent, if (miuiBlurEnabled) -0.28f else 0.16f)
    val variantAlpha = appBackgroundAlphaWithOffset(cardAlphaPercent, if (miuiBlurEnabled) -0.36f else -0.18f)
    val lowestAlpha = appBackgroundAlphaWithOffset(cardAlphaPercent, if (miuiBlurEnabled) -0.48f else -0.44f)
    val lowAlpha = appBackgroundSurfaceAlpha(cardAlphaPercent, dark = true, miuiBlurEnabled = miuiBlurEnabled)
    val normalAlpha = appBackgroundAlphaWithOffset(cardAlphaPercent, if (miuiBlurEnabled) -0.28f else -0.08f)
    val highAlpha = appBackgroundSurfaceAlpha(cardAlphaPercent, dark = true, miuiBlurEnabled = miuiBlurEnabled, strong = true)
    val highestAlpha = appBackgroundAlphaWithOffset(cardAlphaPercent, if (miuiBlurEnabled) -0.16f else 0f)

    return copy(
        background = Color.Transparent,
        onBackground = primaryText,
        surface = Color.Black.copy(alpha = surfaceAlpha),
        onSurface = primaryText,
        surfaceVariant = Color.Black.copy(alpha = variantAlpha),
        onSurfaceVariant = secondaryText,
        surfaceContainerLowest = Color.Black.copy(alpha = lowestAlpha),
        surfaceContainerLow = Color.Black.copy(alpha = lowAlpha),
        surfaceContainer = Color.Black.copy(alpha = normalAlpha),
        surfaceContainerHigh = Color.Black.copy(alpha = highAlpha),
        surfaceContainerHighest = Color.Black.copy(alpha = highestAlpha),
        outline = Color.White.copy(alpha = if (miuiBlurEnabled) 0.42f else 0.34f),
        outlineVariant = Color.White.copy(alpha = if (miuiBlurEnabled) 0.26f else 0.18f),
        inverseSurface = Color.White,
        inverseOnSurface = Color.Black,
        scrim = Color.Black
    )
}

private fun ColorScheme.lightAppBackgroundColorScheme(
    miuiBlurEnabled: Boolean,
    cardAlphaPercent: Int
): ColorScheme {
    val primaryText = Color(0xFF15171C)
    val secondaryText = primaryText.copy(alpha = 0.68f)
    val surfaceAlpha = appBackgroundAlphaWithOffset(cardAlphaPercent, if (miuiBlurEnabled) -0.08f else 0.16f)
    val variantAlpha = appBackgroundAlphaWithOffset(cardAlphaPercent, if (miuiBlurEnabled) -0.26f else -0.08f)
    val lowestAlpha = appBackgroundAlphaWithOffset(cardAlphaPercent, if (miuiBlurEnabled) -0.36f else -0.28f)
    val lowAlpha = appBackgroundSurfaceAlpha(cardAlphaPercent, dark = false, miuiBlurEnabled = miuiBlurEnabled)
    val normalAlpha = appBackgroundAlphaWithOffset(cardAlphaPercent, if (miuiBlurEnabled) -0.14f else 0.06f)
    val highAlpha = appBackgroundAlphaWithOffset(cardAlphaPercent, if (miuiBlurEnabled) -0.06f else 0.12f)
    val highestAlpha = appBackgroundAlphaWithOffset(cardAlphaPercent, if (miuiBlurEnabled) 0.02f else 0.18f)

    return copy(
        background = Color.Transparent,
        onBackground = primaryText,
        surface = Color.White.copy(alpha = surfaceAlpha),
        onSurface = primaryText,
        surfaceVariant = Color.White.copy(alpha = variantAlpha),
        onSurfaceVariant = secondaryText,
        surfaceContainerLowest = Color.White.copy(alpha = lowestAlpha),
        surfaceContainerLow = Color.White.copy(alpha = lowAlpha),
        surfaceContainer = Color.White.copy(alpha = normalAlpha),
        surfaceContainerHigh = Color.White.copy(alpha = highAlpha),
        surfaceContainerHighest = Color.White.copy(alpha = highestAlpha),
        outline = Color.White.copy(alpha = if (miuiBlurEnabled) 0.56f else 0.44f),
        outlineVariant = Color.White.copy(alpha = if (miuiBlurEnabled) 0.42f else 0.30f),
        inverseSurface = Color(0xFF15171C),
        inverseOnSurface = Color.White,
        scrim = Color.Black
    )
}

private fun appBackgroundAlphaWithOffset(cardAlphaPercent: Int, offset: Float): Float {
    val base = MySettings.normalizeAppBackgroundCardAlphaPercent(cardAlphaPercent) / 100f
    return (base + offset).coerceIn(0f, 1f)
}

private fun ColorScheme.isCurrentThemeDarkGlass(): Boolean {
    return surface.luminance() < 0.5f
}

fun shouldUseLightSystemBarsForAppBackground(defaultLight: Boolean): Boolean {
    return defaultLight
}
