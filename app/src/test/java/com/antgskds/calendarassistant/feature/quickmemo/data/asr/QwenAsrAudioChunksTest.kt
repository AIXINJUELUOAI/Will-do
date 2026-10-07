package com.antgskds.calendarassistant.feature.quickmemo.data.asr

import org.junit.Assert.*
import org.junit.Test

class QwenAsrAudioChunksTest {
    @Test fun shortAudioIsNotSplitAndLongAudioHasNoGapsOrOverlaps() {
        assertTrue(QwenAsrAudioChunks.ranges(floatArrayOf(), 10).isEmpty())
        assertEquals(listOf(0 until 100), QwenAsrAudioChunks.ranges(FloatArray(100), 10))
        val samples = FloatArray(657) { 1f }
        val ranges = QwenAsrAudioChunks.ranges(samples, 10)
        assertEquals(0, ranges.first().first)
        assertEquals(samples.lastIndex, ranges.last().last)
        assertTrue(ranges.all { it.count() <= 200 && !it.isEmpty() })
        ranges.zipWithNext().forEach { (first, next) -> assertEquals(first.last + 1, next.first) }
    }

    @Test fun prefersQuietBoundaryAndKeepsTheWholeRecording() {
        val samples = FloatArray(450) { 1f }
        for (index in 170 until 180) samples[index] = 0f
        val ranges = QwenAsrAudioChunks.ranges(samples, 10)
        assertEquals(179, ranges.first().last)
        assertEquals(samples.size, ranges.sumOf { it.count() })
    }
}
