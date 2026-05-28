package com.antgskds.calendarassistant.ui.motion

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException

private const val BottomDialogAnimationMillis = 240
private val PredictiveBackTravel = 160.dp

@Composable
fun PredictiveBottomDialogHost(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissEnabled: Boolean = true,
    backHandlerEnabled: Boolean = true,
    dismissOnClickOutside: Boolean = true,
    predictiveBackEnabled: Boolean = true,
    scrimColor: Color = Color.Black.copy(alpha = 0.4f),
    contentAlignment: Alignment = Alignment.BottomCenter,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable BoxScope.() -> Unit
) {
    var backProgress by remember { mutableFloatStateOf(0f) }
    val clampedProgress = backProgress.coerceIn(0f, 1f)
    val transitionState = remember { MutableTransitionState(false) }

    LaunchedEffect(visible) {
        if (visible) {
            backProgress = 0f
        }
        transitionState.targetState = visible
    }

    val transition = updateTransition(transitionState, label = "predictive_bottom_dialog")
    val appearProgress by transition.animateFloat(
        transitionSpec = { tween(BottomDialogAnimationMillis, easing = FastOutSlowInEasing) },
        label = "dialog_appear_progress"
    ) { shown -> if (shown) 1f else 0f }

    if (visible && dismissEnabled && backHandlerEnabled) {
        if (predictiveBackEnabled) {
            PredictiveBackHandler { progress ->
                try {
                    progress.collect { backEvent ->
                        backProgress = backEvent.progress
                    }
                    backProgress = 1f
                    onDismiss()
                } catch (_: CancellationException) {
                    backProgress = 0f
                }
            }
        } else {
            BackHandler(onBack = onDismiss)
        }
    }

    if (appearProgress > 0f || transitionState.currentState || transitionState.targetState) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(appearProgress * (1f - clampedProgress))
                    .background(scrimColor)
                    .clickable(
                        enabled = dismissEnabled && dismissOnClickOutside,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )

            Box(
                modifier = Modifier
                    .align(contentAlignment)
                    .padding(contentPadding)
                    .graphicsLayer {
                        translationY = PredictiveBackTravel.toPx() * maxOf(1f - appearProgress, clampedProgress)
                        alpha = appearProgress * (1f - clampedProgress * 0.25f)
                    }
            ) {
                Box(content = content)
            }
        }
    }
}
