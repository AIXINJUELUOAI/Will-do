package com.antgskds.calendarassistant.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally

const val AppNavTransitionDurationMillis = 300

fun navForwardEnterTransition(): EnterTransition = slideInHorizontally(
    initialOffsetX = { width -> width },
    animationSpec = tween(
        durationMillis = AppNavTransitionDurationMillis,
        easing = FastOutSlowInEasing
    )
)

fun navForwardExitTransition(): ExitTransition = slideOutHorizontally(
    targetOffsetX = { width -> -width },
    animationSpec = tween(
        durationMillis = AppNavTransitionDurationMillis,
        easing = FastOutSlowInEasing
    )
)

fun navBackwardEnterTransition(): EnterTransition = slideInHorizontally(
    initialOffsetX = { width -> -width },
    animationSpec = tween(
        durationMillis = AppNavTransitionDurationMillis,
        easing = FastOutSlowInEasing
    )
)

fun navBackwardExitTransition(): ExitTransition = slideOutHorizontally(
    targetOffsetX = { width -> width },
    animationSpec = tween(
        durationMillis = AppNavTransitionDurationMillis,
        easing = FastOutSlowInEasing
    )
)
