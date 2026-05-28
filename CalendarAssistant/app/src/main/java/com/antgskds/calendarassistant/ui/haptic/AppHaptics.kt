package com.antgskds.calendarassistant.ui.haptic

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import kotlin.math.roundToInt

@Stable
class AppHaptics internal constructor(
    private val view: View,
    private val enabled: Boolean
) {
    fun selection() = perform(HapticFeedbackConstants.KEYBOARD_TAP)

    fun click() = perform(HapticFeedbackConstants.CONTEXT_CLICK)

    fun longPress() = perform(HapticFeedbackConstants.LONG_PRESS)

    fun threshold() = perform(HapticFeedbackConstants.GESTURE_START)

    fun confirm() = selection()

    fun warning() = click()

    fun error() = click()

    
    private fun perform(feedbackConstant: Int) {
        if (enabled) {
            view.performHapticFeedback(feedbackConstant)
        }
    }
}

val LocalAppHapticsEnabled = staticCompositionLocalOf { true }

@Composable
fun rememberAppHaptics(enabled: Boolean): AppHaptics {
    val view = LocalView.current
    return remember(view, enabled) { AppHaptics(view, enabled) }
}

@Composable
fun rememberAppHaptics(): AppHaptics = rememberAppHaptics(LocalAppHapticsEnabled.current)

@Composable
fun HapticValueChangeEffect(
    valueKey: Any?,
    enabled: Boolean? = null,
    skipInitial: Boolean = true,
    feedback: AppHaptics.() -> Unit = { selection() }
) {
    val haptics = rememberAppHaptics(enabled ?: LocalAppHapticsEnabled.current)
    val hasSeenValue = remember { androidx.compose.runtime.mutableStateOf(false) }
    LaunchedEffect(valueKey) {
        if (skipInitial && !hasSeenValue.value) {
            hasSeenValue.value = true
            return@LaunchedEffect
        }
        hasSeenValue.value = true
        haptics.feedback()
    }
}

fun sliderHapticBucket(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    continuousBucketCount: Int = 12
): Int {
    if (steps > 0) {
        return value.roundToInt()
    }
    val span = valueRange.endInclusive - valueRange.start
    if (span <= 0f) return 0
    val normalized = ((value - valueRange.start) / span).coerceIn(0f, 1f)
    return (normalized * continuousBucketCount).roundToInt()
}
