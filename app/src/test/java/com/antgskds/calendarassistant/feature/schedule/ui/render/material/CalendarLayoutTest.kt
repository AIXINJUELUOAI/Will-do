package com.antgskds.calendarassistant.feature.schedule.ui.render.material

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class CalendarLayoutTest {
    @Test fun courseSpansEndAtTheSamePixelAsTheirLastNode() {
        for (density in listOf(1f, 1.25f, 1.5f, 2.625f, 3f)) {
            for (nodes in listOf(8, 12, 13, 16)) {
                val height = (713f * density).roundToInt()
                val edges = (0..nodes).map { calendarGridBoundary(height, it, nodes) }
                val rowHeights = edges.zipWithNext { top, bottom ->
                    (((bottom - top) / density) * density).roundToInt()
                }
                assertEquals(height, rowHeights.sum())
                for (start in 0 until nodes) for (end in start + 1..nodes) {
                    val cardHeight = (((edges[end] - edges[start]) / density) * density).roundToInt()
                    assertEquals(rowHeights.take(start).sum(), edges[start])
                    assertEquals(rowHeights.take(end).sum(), edges[start] + cardHeight)
                }
            }
        }
    }

    @Test fun columnsAndConflictLanesShareExactEdgesAtScaledWidths() {
        for (density in listOf(1f, 1.25f, 1.5f, 2.625f, 3f)) {
            for (widthDp in listOf(321f, 601f, 839f, 1103f)) {
                val width = (widthDp * density).roundToInt()
                val edges = (0..7).map { calendarGridBoundary(width, it, 7) }
                assertEquals(0, edges.first())
                assertEquals(width, edges.last())
                assertEquals(width, edges.zipWithNext { a, b -> b - a }.sum())
                edges.zipWithNext().forEach { (left, right) ->
                    val dayWidth = right - left
                    val lanes = (0..3).map { left + calendarGridBoundary(dayWidth, it, 3) }
                    assertEquals(left, lanes.first())
                    assertEquals(right, lanes.last())
                    assertTrue(lanes.zipWithNext().all { (a, b) -> b >= a })
                    // Widths converted through Dp must still round back to the same physical pixels.
                    assertEquals(dayWidth, ((dayWidth / density) * density).roundToInt())
                }
            }
        }
    }

    @Test fun timeEdgesUseTheSameRoundingAsGridLines() {
        for (hourPx in listOf(64, 80, 96, 168, 192)) {
            for (hour in 0..24) assertEquals(hourPx * hour, calendarMinuteOffset(hourPx, hour * 60))
            assertEquals(hourPx, calendarMinuteOffset(hourPx, 600) - calendarMinuteOffset(hourPx, 540))
            assertEquals((hourPx * 5 / 60.0).roundToInt(),
                calendarMinuteOffset(hourPx, 545) - calendarMinuteOffset(hourPx, 540))
            assertTrue((0..1440).map { calendarMinuteOffset(hourPx, it) }
                .zipWithNext().all { (a, b) -> b >= a })
        }
    }
}
