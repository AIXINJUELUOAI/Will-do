package com.antgskds.calendarassistant.platform.floating.ui.render.material

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs

/** 仅保留本次手势的窗口起点，不使用长按回调中可能来自入场动画的旧局部坐标。 */
internal class FloatingTextDragGesture(
    private val windowWidthPx: Float,
    private val hotZonePercent: Int,
    private val expandFromLeft: Boolean,
    private val directionBias: Float
) {
    private var startInWindow: Offset? = null

    fun reset() {
        startInWindow = null
    }

    fun shouldStartExternalDrag(
        previousPosition: Offset,
        currentPosition: Offset,
        localToWindow: (Offset) -> Offset
    ): Boolean {
        if (windowWidthPx <= 0f) return false
        // 同一拖动事件的前后位置都使用当前布局转换；动画结束后仍沿用真实的窗口起点。
        val start = startInWindow ?: localToWindow(previousPosition).also { startInWindow = it }
        val current = localToWindow(currentPosition)
        val movement = current - start
        val hotZoneWidthPx = windowWidthPx * (hotZonePercent / 100f)
        fun inHotZone(x: Float): Boolean = if (expandFromLeft) {
            x <= hotZoneWidthPx
        } else {
            x >= windowWidthPx - hotZoneWidthPx
        }
        val movedOutFromFloatingSide = if (expandFromLeft) movement.x > 0f else movement.x < 0f
        return movedOutFromFloatingSide &&
            inHotZone(start.x.coerceIn(0f, windowWidthPx)) &&
            !inHotZone(current.x.coerceIn(0f, windowWidthPx)) &&
            abs(movement.x) > abs(movement.y) * directionBias
    }
}
