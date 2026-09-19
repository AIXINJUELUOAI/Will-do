package com.antgskds.calendarassistant

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.shared.ui.material.component.AppSwipeReveal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppSwipeRevealTest {
    @get:Rule val compose = createComposeRule()

    private val expanded = mutableStateOf(false)
    private val threshold = mutableStateOf(0.5f)
    private val callbackGeneration = mutableStateOf(0)
    private var receivedGeneration = -1
    private var progress = 0f

    private fun showRow() {
        compose.setContent {
            MaterialTheme {
                val generation = callbackGeneration.value
                Box(Modifier.width(300.dp).testTag("row")) {
                    AppSwipeReveal(
                        isRevealed = expanded.value,
                        actionWidth = 160.dp,
                        revealThreshold = threshold.value,
                        onRevealedChange = {
                            receivedGeneration = generation
                            expanded.value = it
                        },
                        hapticEnabled = false,
                        actions = { Text("操作") },
                    ) { modifier, value, _ ->
                        SideEffect { progress = value }
                        Box(modifier.height(64.dp)) { Text("条目") }
                    }
                }
            }
        }
    }

    private fun drag(fraction: Float) {
        compose.onNodeWithTag("row").performTouchInput {
            swipe(
                start = Offset(width * 0.9f, height / 2f),
                end = Offset(width * (0.9f - fraction), height / 2f),
                durationMillis = 300,
            )
        }
        compose.waitForIdle()
    }

    @Test fun businessThresholdsRemainDistinct() {
        showRow()
        // 同样拖动约 65 dp：不足日程阈值，但足以展开随口记。
        drag(65f / 300f)
        compose.runOnIdle { assertFalse(expanded.value); threshold.value = 0.28f }
        drag(65f / 300f)
        compose.runOnIdle { assertTrue(expanded.value); assertEquals(1f, progress, 0.001f) }
    }

    @Test fun externalCollapseAndUpdatedCallbackAreRespected() {
        showRow()
        compose.runOnIdle { callbackGeneration.value = 1 }
        drag(0.6f)
        compose.runOnIdle {
            assertTrue(expanded.value)
            assertEquals(1, receivedGeneration)
            expanded.value = false
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(0f, progress, 0.001f) }
    }

    @Test fun cancelledDragReturnsToRest() {
        showRow()
        compose.onNodeWithTag("row").performTouchInput {
            down(Offset(width * 0.9f, height / 2f))
            moveTo(Offset(width * 0.3f, height / 2f))
            cancel()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(expanded.value); assertEquals(0f, progress, 0.001f) }
    }
}
