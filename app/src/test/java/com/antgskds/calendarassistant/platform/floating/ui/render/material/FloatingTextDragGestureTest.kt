package com.antgskds.calendarassistant.platform.floating.ui.render.material

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingTextDragGestureTest {
    private fun gesture(fromLeft: Boolean = false) = FloatingTextDragGesture(1080f, 50, fromLeft, 1.2f)

    @Test
    fun rightEntranceDragUsesWindowPositionEvenWhenLocalStartIsOutsideHotZone() {
        val drag = gesture()
        // 右侧滑入偏移 350px；局部 x=450 实际为窗口 x=800，向左拖到窗口 x=500。
        assertTrue(drag.shouldStartExternalDrag(Offset(450f, 100f), Offset(150f, 100f)) {
            it + Offset(350f, 0f)
        })
    }

    @Test
    fun leftEntranceDragUsesWindowPositionEvenWhenLocalStartIsOutsideHotZone() {
        val drag = gesture(fromLeft = true)
        assertTrue(drag.shouldStartExternalDrag(Offset(630f, 100f), Offset(930f, 100f)) {
            it - Offset(350f, 0f)
        })
    }

    @Test
    fun windowOriginSurvivesTranslationAndScaleChangesAcrossDragFrames() {
        val drag = gesture()
        assertFalse(drag.shouldStartExternalDrag(Offset(450f, 100f), Offset(350f, 450f)) {
            it + Offset(350f, 0f)
        })
        // 已离开热区，但仍以纵向移动为主。
        assertFalse(drag.shouldStartExternalDrag(Offset(700f, 450f), Offset(500f, 450f)) { it })
        // 布局再次变换，手指回到原纵坐标；窗口起点仍为 (800, 100)。
        assertTrue(drag.shouldStartExternalDrag(Offset(300f, 650f), Offset(250f, 300f)) {
            Offset(it.x * 2f - 100f, it.y - 200f)
        })
    }

    @Test
    fun layoutAnimationAloneDoesNotStartAnExternalDrag() {
        val drag = gesture()
        assertFalse(drag.shouldStartExternalDrag(Offset(450f, 100f), Offset(450f, 100f)) {
            it + Offset(350f, 0f)
        })
        assertFalse(drag.shouldStartExternalDrag(Offset(800f, 100f), Offset(800f, 100f)) { it })
    }

    @Test
    fun verticalSortingAndStartsOutsideHotZoneKeepTheirExistingBehavior() {
        val vertical = gesture()
        assertFalse(vertical.shouldStartExternalDrag(Offset(800f, 100f), Offset(790f, 450f)) { it })
        val outside = gesture()
        assertFalse(outside.shouldStartExternalDrag(Offset(450f, 100f), Offset(100f, 100f)) { it })
    }

    @Test
    fun nextGestureDoesNotReuseThePreviousWindowOrigin() {
        val drag = gesture()
        assertTrue(drag.shouldStartExternalDrag(Offset(800f, 100f), Offset(500f, 100f)) { it })
        drag.reset()
        assertFalse(drag.shouldStartExternalDrag(Offset(450f, 100f), Offset(100f, 100f)) { it })
    }
}
