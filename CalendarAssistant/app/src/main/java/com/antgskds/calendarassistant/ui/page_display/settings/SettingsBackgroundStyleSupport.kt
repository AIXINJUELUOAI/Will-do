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

@Composable
fun AppBackgroundStyleTheme(
    enabled: Boolean,
    miuiBlurEnabled: Boolean = false,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(
        LocalAppBackgroundStyleEnabled provides enabled,
        LocalAppBackgroundMiuiBlurEnabled provides (enabled && miuiBlurEnabled)
    ) {
        if (!enabled) {
            content()
        } else {
            MaterialTheme(
                colorScheme = MaterialTheme.colorScheme.appBackgroundColorScheme(miuiBlurEnabled),
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
    content: @Composable () -> Unit
) {
    AppBackgroundStyleTheme(enabled = enabled, miuiBlurEnabled = miuiBlurEnabled, content = content)
}

@Composable
fun rememberAppBackgroundStylePalette(
    enabled: Boolean = true,
    miuiBlurEnabled: Boolean = false
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
    return if (dark) {
        AppBackgroundStylePalette(
            surface = Color.Black.copy(alpha = if (miuiBlurEnabled) 0.34f else 0.54f),
            surfaceStrong = Color.Black.copy(alpha = if (miuiBlurEnabled) 0.44f else 0.62f),
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
            surface = Color.White.copy(alpha = if (miuiBlurEnabled) 0.46f else 0.66f),
            surfaceStrong = Color.White.copy(alpha = if (miuiBlurEnabled) 0.58f else 0.76f),
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

private fun ColorScheme.appBackgroundColorScheme(miuiBlurEnabled: Boolean): ColorScheme {
    return if (isCurrentThemeDarkGlass()) {
        darkAppBackgroundColorScheme(miuiBlurEnabled)
    } else {
        lightAppBackgroundColorScheme(miuiBlurEnabled)
    }
}

private fun ColorScheme.darkAppBackgroundColorScheme(miuiBlurEnabled: Boolean): ColorScheme {
    val primaryText = Color.White
    val secondaryText = Color.White.copy(alpha = 0.74f)
    val lowAlpha = if (miuiBlurEnabled) 0.34f else 0.54f
    val normalAlpha = if (miuiBlurEnabled) 0.38f else 0.58f
    val highAlpha = if (miuiBlurEnabled) 0.44f else 0.62f
    val highestAlpha = if (miuiBlurEnabled) 0.50f else 0.66f

    return copy(
        primary = primaryText,
        onPrimary = Color.Black,
        primaryContainer = Color.White.copy(alpha = 0.18f),
        onPrimaryContainer = primaryText,
        secondary = Color.White.copy(alpha = 0.82f),
        onSecondary = Color.Black,
        secondaryContainer = Color.White.copy(alpha = 0.14f),
        onSecondaryContainer = primaryText,
        tertiary = Color.White.copy(alpha = 0.82f),
        onTertiary = Color.Black,
        tertiaryContainer = Color.White.copy(alpha = 0.14f),
        onTertiaryContainer = primaryText,
        background = Color.Transparent,
        onBackground = primaryText,
        surface = Color.Black.copy(alpha = if (miuiBlurEnabled) 0.38f else 0.82f),
        onSurface = primaryText,
        surfaceVariant = Color.Black.copy(alpha = if (miuiBlurEnabled) 0.30f else 0.48f),
        onSurfaceVariant = secondaryText,
        surfaceContainerLowest = Color.Black.copy(alpha = if (miuiBlurEnabled) 0.18f else 0.22f),
        surfaceContainerLow = Color.Black.copy(alpha = lowAlpha),
        surfaceContainer = Color.Black.copy(alpha = normalAlpha),
        surfaceContainerHigh = Color.Black.copy(alpha = highAlpha),
        surfaceContainerHighest = Color.Black.copy(alpha = highestAlpha),
        outline = Color.White.copy(alpha = if (miuiBlurEnabled) 0.42f else 0.34f),
        outlineVariant = Color.White.copy(alpha = if (miuiBlurEnabled) 0.26f else 0.18f),
        inverseSurface = Color.White,
        inverseOnSurface = Color.Black,
        inversePrimary = Color.Black,
        scrim = Color.Black,
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6)
    )
}

private fun ColorScheme.lightAppBackgroundColorScheme(miuiBlurEnabled: Boolean): ColorScheme {
    val primaryText = Color(0xFF15171C)
    val secondaryText = primaryText.copy(alpha = 0.68f)
    val lowAlpha = if (miuiBlurEnabled) 0.46f else 0.66f
    val normalAlpha = if (miuiBlurEnabled) 0.52f else 0.72f
    val highAlpha = if (miuiBlurEnabled) 0.60f else 0.78f
    val highestAlpha = if (miuiBlurEnabled) 0.68f else 0.84f

    return copy(
        primary = primaryText,
        onPrimary = Color.White,
        primaryContainer = Color.White.copy(alpha = 0.70f),
        onPrimaryContainer = primaryText,
        secondary = primaryText.copy(alpha = 0.82f),
        onSecondary = Color.White,
        secondaryContainer = Color.White.copy(alpha = 0.58f),
        onSecondaryContainer = primaryText,
        tertiary = primaryText.copy(alpha = 0.82f),
        onTertiary = Color.White,
        tertiaryContainer = Color.White.copy(alpha = 0.58f),
        onTertiaryContainer = primaryText,
        background = Color.Transparent,
        onBackground = primaryText,
        surface = Color.White.copy(alpha = if (miuiBlurEnabled) 0.58f else 0.82f),
        onSurface = primaryText,
        surfaceVariant = Color.White.copy(alpha = if (miuiBlurEnabled) 0.40f else 0.58f),
        onSurfaceVariant = secondaryText,
        surfaceContainerLowest = Color.White.copy(alpha = if (miuiBlurEnabled) 0.30f else 0.38f),
        surfaceContainerLow = Color.White.copy(alpha = lowAlpha),
        surfaceContainer = Color.White.copy(alpha = normalAlpha),
        surfaceContainerHigh = Color.White.copy(alpha = highAlpha),
        surfaceContainerHighest = Color.White.copy(alpha = highestAlpha),
        outline = Color.White.copy(alpha = if (miuiBlurEnabled) 0.56f else 0.44f),
        outlineVariant = Color.White.copy(alpha = if (miuiBlurEnabled) 0.42f else 0.30f),
        inverseSurface = Color(0xFF15171C),
        inverseOnSurface = Color.White,
        inversePrimary = Color.White,
        scrim = Color.Black,
        error = Color(0xFFBA1A1A),
        onError = Color.White,
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002)
    )
}

private fun ColorScheme.isCurrentThemeDarkGlass(): Boolean {
    return background.luminance() < 0.5f
}

fun shouldUseLightSystemBarsForAppBackground(defaultLight: Boolean): Boolean {
    return defaultLight
}
