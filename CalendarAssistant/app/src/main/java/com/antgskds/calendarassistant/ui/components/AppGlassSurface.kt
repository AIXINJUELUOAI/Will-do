package com.antgskds.calendarassistant.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

data class AppGlassSettings(
    val enabled: Boolean = false,
    val miuixEnabled: Boolean = false,
    val blurRadiusDp: Int = 28,
    val overlayAlphaPercent: Int = 78,
    val wallpaperBitmap: ImageBitmap? = null,
    val rootSize: IntSize = IntSize.Zero,
    val darkTheme: Boolean = false
) {
    val active: Boolean
        get() = false
}

val LocalAppGlassSettings = staticCompositionLocalOf { AppGlassSettings() }

@Composable
fun AppGlassSettingsProvider(
    settings: AppGlassSettings,
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalAppGlassSettings provides settings, content = content)
}

@Composable
fun AppGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    fallbackColor: Color,
    overlayColor: Color = glassOverlayColor(LocalAppGlassSettings.current, fallbackColor),
    borderColor: Color = glassBorderColor(LocalAppGlassSettings.current),
    borderWidth: Dp = 1.dp,
    forceStrongerOverlay: Boolean = false,
    content: @Composable () -> Unit
) {
    val glassSettings = LocalAppGlassSettings.current
    if (!glassSettings.active) {
        Box(modifier = modifier.background(fallbackColor, shape)) {
            content()
        }
        return
    }

    val resolvedOverlay = if (forceStrongerOverlay) {
        overlayColor.copy(alpha = (overlayColor.alpha + 0.08f).coerceAtMost(0.96f))
    } else {
        overlayColor
    }

    Box(
        modifier = modifier
            .clip(shape)
            .border(borderWidth, borderColor, shape)
    ) {
        AppGlassBlurredBackdrop(
            modifier = Modifier.matchParentSize(),
            shape = shape,
            blurRadius = if (glassSettings.miuixEnabled) {
                (glassSettings.blurRadiusDp * 1.25f).dp
            } else {
                glassSettings.blurRadiusDp.dp
            }
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(resolvedOverlay, shape)
        )
        content()
    }
}

@Composable
private fun AppGlassBlurredBackdrop(
    modifier: Modifier,
    shape: Shape,
    blurRadius: Dp
) {
    val glassSettings = LocalAppGlassSettings.current
    val wallpaper = glassSettings.wallpaperBitmap ?: return
    val rootSize = glassSettings.rootSize
    if (rootSize.width <= 0 || rootSize.height <= 0) return

    val density = LocalDensity.current
    val rootWidth = with(density) { rootSize.width.toDp() }
    val rootHeight = with(density) { rootSize.height.toDp() }
    val blurRadiusPx = with(density) { blurRadius.toPx() }
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
                .size(rootWidth, rootHeight)
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

private fun glassOverlayColor(settings: AppGlassSettings, fallbackColor: Color): Color {
    val alpha = (settings.overlayAlphaPercent.coerceIn(45, 92) / 100f).let { base ->
        if (settings.miuixEnabled) (base - 0.08f).coerceAtLeast(0.42f) else base
    }
    val base = if (settings.darkTheme) Color.Black else fallbackColor
    return base.copy(alpha = alpha)
}

private fun glassBorderColor(settings: AppGlassSettings): Color {
    return if (settings.darkTheme) {
        Color.White.copy(alpha = if (settings.miuixEnabled) 0.24f else 0.16f)
    } else {
        Color.White.copy(alpha = if (settings.miuixEnabled) 0.50f else 0.34f)
    }
}
