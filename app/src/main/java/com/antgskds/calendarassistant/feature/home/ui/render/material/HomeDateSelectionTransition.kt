package com.antgskds.calendarassistant.feature.home.ui.render.material

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog

/** 只共享选中背景，日历/列表正文继续使用各自布局；不受父卡片裁剪和离场透明度影响。 */
internal data class HomeDateSelectionTransition(
    val scope: SharedTransitionScope,
    val visibility: AnimatedVisibilityScope,
    val shape: Shape,
    val color: Color,
)

internal val LocalHomeDateSelectionTransition = compositionLocalOf<HomeDateSelectionTransition?> { null }

@Composable
internal fun Modifier.homeDateSelectionBackground(radius: Dp, color: Color): Modifier {
    val transition = LocalHomeDateSelectionTransition.current
    if (transition == null) return background(color, RoundedCornerShape(radius))
    return with(transition.scope) {
        this@homeDateSelectionBackground.sharedElement(
            sharedContentState = rememberSharedContentState("home-selected-date"),
            animatedVisibilityScope = transition.visibility,
            boundsTransform = { _, _ -> tween(ConfigCatalog.HOME_AGENDA_MOTION_MS) },
            // No parent clip: the background must travel out of the calendar card to the date rail.
            clipInOverlayDuringTransition = OverlayClip(transition.shape),
        ).background(transition.color, transition.shape)
    }
}

/** 文字随原场景淡入淡出，但在共享背景上方绘制，避免移动的背景遮住日期。 */
@Composable
internal fun Modifier.homeDateSelectionForeground(): Modifier {
    val transition = LocalHomeDateSelectionTransition.current ?: return this
    val opacity by transition.visibility.transition.animateFloat(
        transitionSpec = { tween(ConfigCatalog.HOME_AGENDA_MOTION_MS) },
        label = "home_date_foreground_alpha",
    ) { if (it == EnterExitState.Visible) 1f else 0f }
    return with(transition.scope) {
        this@homeDateSelectionForeground.renderInSharedTransitionScopeOverlay(zIndexInOverlay = 1f)
            .graphicsLayer { alpha = if (isTransitionActive) opacity else 1f }
    }
}
