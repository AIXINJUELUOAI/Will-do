package com.antgskds.calendarassistant.shared.ui.material.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop

/**
 * textureBlur 的跨窗口采样源。库内 LayerBackdrop 在跨窗口时退回 positionInWindow，
 * 而居中 Dialog 与 Activity 的窗口原点不同；这里统一经屏幕坐标转换。
 * 子弹层先绘制父场景再叠加自身，局部弹层之外仍有完整背景可采样。
 */
class AppWindowBackdrop internal constructor(
    internal val layer: LayerBackdrop,
    private val parent: Backdrop?,
) : Backdrop {
    internal var coordinates: LayoutCoordinates? by mutableStateOf(null)
    override val isCoordinatesDependent: Boolean = true

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int,
    ) {
        parent?.let { with(it) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) } }
        val source = this@AppWindowBackdrop.coordinates ?: return
        val target = coordinates ?: return
        if (!source.isAttached || !target.isAttached || source.size.width == 0 || source.size.height == 0) return

        // 使用实际布局坐标的变换，包含 Sheet 位移和 Dialog 缩放；textureBlur 不传自定义 layerBlock。
        val origin = target.screenToLocal(source.localToScreen(Offset.Zero))
        val x = target.screenToLocal(source.localToScreen(Offset(source.size.width.toFloat(), 0f)))
        val y = target.screenToLocal(source.localToScreen(Offset(0f, source.size.height.toFloat())))
        if (!origin.x.isFinite() || !origin.y.isFinite() || !x.x.isFinite() || !x.y.isFinite() ||
            !y.x.isFinite() || !y.y.isFinite()) return

        val scale = 1f / downscaleFactor.coerceAtLeast(1)
        val matrix = Matrix().apply {
            this[0, 0] = (x.x - origin.x) / source.size.width * scale
            this[0, 1] = (x.y - origin.y) / source.size.width * scale
            this[1, 0] = (y.x - origin.x) / source.size.height * scale
            this[1, 1] = (y.y - origin.y) / source.size.height * scale
            this[3, 0] = origin.x * scale
            this[3, 1] = origin.y * scale
        }
        withTransform({ transform(matrix) }) { drawLayer(layer.graphicsLayer) }
    }
}

@Composable
fun rememberAppWindowBackdrop(parent: Backdrop? = null): AppWindowBackdrop {
    val layer = rememberLayerBackdrop()
    val backdrop = remember(layer, parent) { AppWindowBackdrop(layer, parent) }
    DisposableEffect(backdrop) { onDispose { backdrop.coordinates = null } }
    return backdrop
}

fun Modifier.appWindowBackdrop(backdrop: AppWindowBackdrop): Modifier =
    layerBackdrop(backdrop.layer).onGloballyPositioned { backdrop.coordinates = it }
