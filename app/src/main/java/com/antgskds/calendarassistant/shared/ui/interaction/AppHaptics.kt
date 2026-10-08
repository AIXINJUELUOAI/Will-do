package com.antgskds.calendarassistant.shared.ui.interaction

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlin.math.roundToInt

@Stable
class AppHaptics internal constructor(
    private val view: View,
    private val enabled: () -> Boolean
) {
    fun selection() = perform(HapticFeedbackConstants.KEYBOARD_TAP)

    fun click() = perform(HapticFeedbackConstants.CONTEXT_CLICK)

    fun longPress() = perform(HapticFeedbackConstants.LONG_PRESS)

    fun threshold() = perform(HapticFeedbackConstants.GESTURE_START)

    fun confirm() = selection()

    fun warning() = click()

    fun error() = click()

    
    private fun perform(feedbackConstant: Int) {
        if (enabled()) {
            view.performHapticFeedback(feedbackConstant)
        }
    }
}

val LocalAppHapticsEnabled = staticCompositionLocalOf { true }

@Composable
fun rememberAppHaptics(enabled: Boolean): AppHaptics {
    val view = LocalView.current
    val latestEnabled = rememberUpdatedState(enabled && LocalAppHapticsEnabled.current)
    // 手势协程可能保留旧引用；稳定实例在触发时读取最新开关。
    return remember(view) { AppHaptics(view) { latestEnabled.value } }
}

/** 给 combinedClickable 等内置反馈使用；保留系统默认触感类型。 */
@Composable
fun rememberAppSystemHapticFeedback(enabled: Boolean): HapticFeedback {
    val system = rememberUpdatedState(LocalHapticFeedback.current)
    val latestEnabled = rememberUpdatedState(enabled)
    return remember {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (latestEnabled.value) system.value.performHapticFeedback(hapticFeedbackType)
            }
        }
    }
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
