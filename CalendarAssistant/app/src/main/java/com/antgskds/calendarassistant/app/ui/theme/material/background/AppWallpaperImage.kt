package com.antgskds.calendarassistant.app.ui.theme.material.background

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

data class AppWallpaperLayout(
    val destinationOffset: IntOffset,
    val destinationSize: IntSize,
    val maxPanX: Float,
    val maxPanY: Float
)

fun calculateAppWallpaperLayout(
    imageSize: IntSize,
    containerSize: IntSize,
    scale: Float,
    offsetX: Float,
    offsetY: Float
): AppWallpaperLayout {
    if (imageSize.width <= 0 || imageSize.height <= 0 || containerSize.width <= 0 || containerSize.height <= 0) {
        return AppWallpaperLayout(IntOffset.Zero, IntSize.Zero, 0f, 0f)
    }
    val normalizedScale = MySettings.normalizeAppBackgroundImageScale(scale)
    val baseScale = max(
        containerSize.width.toFloat() / imageSize.width.toFloat(),
        containerSize.height.toFloat() / imageSize.height.toFloat()
    )
    val renderedWidth = ceil(imageSize.width * baseScale * normalizedScale).toInt().coerceAtLeast(1)
    val renderedHeight = ceil(imageSize.height * baseScale * normalizedScale).toInt().coerceAtLeast(1)
    val maxPanX = ((renderedWidth - containerSize.width) / 2f).coerceAtLeast(0f)
    val maxPanY = ((renderedHeight - containerSize.height) / 2f).coerceAtLeast(0f)
    val panX = MySettings.normalizeAppBackgroundImageOffset(offsetX) * maxPanX
    val panY = MySettings.normalizeAppBackgroundImageOffset(offsetY) * maxPanY
    return AppWallpaperLayout(
        destinationOffset = IntOffset(
            ((containerSize.width - renderedWidth) / 2f + panX).roundToInt(),
            ((containerSize.height - renderedHeight) / 2f + panY).roundToInt()
        ),
        destinationSize = IntSize(renderedWidth, renderedHeight),
        maxPanX = maxPanX,
        maxPanY = maxPanY
    )
}

@Composable
fun AppWallpaperImage(
    imageBitmap: ImageBitmap,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    blurRadiusPx: Float = 0f,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier.then(
            if (blurRadiusPx > 0f) {
                Modifier.graphicsLayer {
                    renderEffect = BlurEffect(
                        radiusX = blurRadiusPx,
                        radiusY = blurRadiusPx,
                        edgeTreatment = TileMode.Clamp
                    )
                }
            } else {
                Modifier
            }
        )
    ) {
        val layout = calculateAppWallpaperLayout(
            imageSize = IntSize(imageBitmap.width, imageBitmap.height),
            containerSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY
        )
        if (layout.destinationSize == IntSize.Zero) return@Canvas
        drawImage(
            image = imageBitmap,
            dstOffset = layout.destinationOffset,
            dstSize = layout.destinationSize,
            filterQuality = FilterQuality.High
        )
    }
}
