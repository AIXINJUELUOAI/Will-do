package com.antgskds.calendarassistant.feature.quickmemo.ui.render.material

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickMemoToolbarLayoutTest {
    @Test fun linksKeepMemoActionWidthsAcrossPhoneAndSplitPaneWidths() {
        for (width in listOf(240, 280, 320, 360, 411, 412, 480, 760)) {
            val plain = quickMemoToolbarLayout(width.dp, hasSource = false)
            val linked = quickMemoToolbarLayout(width.dp, hasSource = true)
            assertEquals("Link must not compress memo actions at $width dp", plain.itemWidth, linked.itemWidth)
            assertTrue("Capsule must fit the page", linked.barWidth + 32.dp <= width.dp)
            assertTrue("Each slot keeps a minimum touch width", linked.itemWidth >= 48.dp)
            assertFalse(plain.sourceAbove)
            if (linked.sourceAbove) {
                assertEquals(80.dp, linked.extraHeight)
            } else {
                assertEquals(0.dp, linked.extraHeight)
                assertTrue("Arrow and capsule must both fit", linked.barWidth + 8.dp + 72.dp + 32.dp <= width.dp)
            }
        }
        assertTrue(quickMemoToolbarLayout(411.dp, hasSource = true).sourceAbove)
        assertFalse(quickMemoToolbarLayout(412.dp, hasSource = true).sourceAbove)
        assertEquals(72.dp, quickMemoToolbarLayout(360.dp, hasSource = true).itemWidth)
        val selection = quickMemoToolbarLayout(360.dp, hasSource = false, itemCount = 3)
        assertEquals(228.dp, selection.barWidth)
        assertEquals(0.dp, selection.extraHeight)
    }
}
