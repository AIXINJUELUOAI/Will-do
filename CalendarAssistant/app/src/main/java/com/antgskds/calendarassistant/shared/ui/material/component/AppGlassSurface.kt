package com.antgskds.calendarassistant.shared.ui.material.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur

data class AppGlassSettings(
    val enabled: Boolean = false,
    val blurRadiusDp: Int = 25,
    val backdrop: LayerBackdrop? = null,
    val overlayBackdrop: LayerBackdrop? = null,
    val darkTheme: Boolean = false
) {
    val active: Boolean
        get() = enabled && backdrop != null
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
    content: @Composable () -> Unit
) {
    val settings = LocalAppGlassSettings.current
    if (!settings.active) {
        Box(modifier = modifier.background(fallbackColor, shape)) {
            content()
        }
        return
    }

    Box(modifier = modifier.appMiuiBlurMaterial(shape)) {
        content()
    }
}

/**
 * Glass surface for content drawn above the current page. It samples the scene backdrop and
 * publishes its rendered result as the backdrop for any child dialog opened from this surface.
 */
@Composable
fun AppOverlayGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape,
    fallbackColor: Color,
    content: @Composable () -> Unit
) {
    val settings = LocalAppGlassSettings.current
    val parentBackdrop = settings.overlayBackdrop
    if (!settings.active || parentBackdrop == null) {
        Box(modifier = modifier.background(fallbackColor, shape)) {
            content()
        }
        return
    }

    val childBackdrop = rememberLayerBackdrop()
    val glassModifier = modifier
        .layerBackdrop(childBackdrop)
        .appMiuiBlurMaterial(shape = shape, backdrop = parentBackdrop)

    AppGlassSettingsProvider(settings.copy(overlayBackdrop = childBackdrop)) {
        Box(modifier = glassModifier) {
            content()
        }
    }
}

@Composable
fun Modifier.appMiuiBlurMaterial(shape: Shape): Modifier {
    val settings = LocalAppGlassSettings.current
    return appMiuiBlurMaterial(shape = shape, backdrop = settings.backdrop)
}

@Composable
fun Modifier.appMiuiOverlayBlurMaterial(shape: Shape): Modifier {
    val settings = LocalAppGlassSettings.current
    return appMiuiBlurMaterial(shape = shape, backdrop = settings.overlayBackdrop)
}

@Composable
private fun Modifier.appMiuiBlurMaterial(
    shape: Shape,
    backdrop: LayerBackdrop?
): Modifier {
    val settings = LocalAppGlassSettings.current
    if (!settings.active || backdrop == null) return this

    val density = LocalDensity.current
    val blurRadiusPx = with(density) { settings.blurRadiusDp.dp.toPx() }
    val materialTint = if (settings.darkTheme) {
        Color.Black.copy(alpha = 0.58f)
    } else {
        Color.White.copy(alpha = 0.62f)
    }
    val blurColors = BlurDefaults.blurColors(
        blendColors = listOf(
            BlendColorEntry(
                color = materialTint,
                mode = BlurBlendMode.SrcOver
            )
        ),
        brightness = if (settings.darkTheme) -0.03f else 0.03f,
        contrast = 1.04f,
        saturation = 1.12f
    )

    return textureBlur(
        backdrop = backdrop,
        shape = shape,
        blurRadius = blurRadiusPx,
        noiseCoefficient = BlurDefaults.NoiseCoefficient,
        colors = blurColors
    )
}
