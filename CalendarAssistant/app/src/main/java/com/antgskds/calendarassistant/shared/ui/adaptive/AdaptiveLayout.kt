package com.antgskds.calendarassistant.shared.ui.adaptive

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.IntRect
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator

enum class AppWindowWidthClass {
    COMPACT,
    MEDIUM,
    EXPANDED,
}

@Immutable
data class AdaptiveLayoutInfo(
    val widthClass: AppWindowWidthClass = AppWindowWidthClass.COMPACT,
    val hasVerticalSeparatingHinge: Boolean = false,
    val isTabletop: Boolean = false,
    val verticalHingeBounds: IntRect? = null,
    val horizontalHingeBounds: IntRect? = null,
) {
    val useNavigationRail: Boolean
        get() = widthClass != AppWindowWidthClass.COMPACT || hasVerticalSeparatingHinge

    val useTwoPaneContent: Boolean
        get() = widthClass == AppWindowWidthClass.EXPANDED || hasVerticalSeparatingHinge || isTabletop
}

val LocalAdaptiveLayoutInfo = compositionLocalOf { AdaptiveLayoutInfo() }

internal fun appWindowWidthClass(widthDp: Float): AppWindowWidthClass = when {
    widthDp >= 840f -> AppWindowWidthClass.EXPANDED
    widthDp >= 600f -> AppWindowWidthClass.MEDIUM
    else -> AppWindowWidthClass.COMPACT
}

@Composable
fun rememberAdaptiveLayoutInfo(activity: Activity): AdaptiveLayoutInfo {
    val configuration = LocalConfiguration.current
    val physicalWindowWidthDp = remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
    ) {
        val bounds = WindowMetricsCalculator.getOrCreate()
            .computeCurrentWindowMetrics(activity)
            .bounds
        bounds.width() / (configuration.densityDpi / 160f)
    }
    val widthClass = remember(physicalWindowWidthDp) {
        appWindowWidthClass(physicalWindowWidthDp)
    }
    val tracker = remember(activity) { WindowInfoTracker.getOrCreate(activity) }
    val layoutInfo by tracker.windowLayoutInfo(activity).collectAsState(initial = null)
    val foldingFeature = layoutInfo
        ?.displayFeatures
        ?.filterIsInstance<FoldingFeature>()
        ?.firstOrNull()
    val hasVerticalSeparatingHinge = foldingFeature?.let { feature ->
        feature.orientation == FoldingFeature.Orientation.VERTICAL && feature.isSeparating
    } == true
    val isTabletop = foldingFeature?.let { feature ->
        feature.orientation == FoldingFeature.Orientation.HORIZONTAL &&
            feature.state == FoldingFeature.State.HALF_OPENED
    } == true
    val verticalHingeBounds = foldingFeature
        ?.takeIf {
            it.orientation == FoldingFeature.Orientation.VERTICAL && it.isSeparating
        }
        ?.bounds
        ?.let { IntRect(it.left, it.top, it.right, it.bottom) }
    val horizontalHingeBounds = foldingFeature
        ?.takeIf {
            it.orientation == FoldingFeature.Orientation.HORIZONTAL &&
                (it.isSeparating || it.state == FoldingFeature.State.HALF_OPENED)
        }
        ?.bounds
        ?.let { IntRect(it.left, it.top, it.right, it.bottom) }

    return remember(
        widthClass,
        hasVerticalSeparatingHinge,
        isTabletop,
        verticalHingeBounds,
        horizontalHingeBounds,
    ) {
        AdaptiveLayoutInfo(
            widthClass = widthClass,
            hasVerticalSeparatingHinge = hasVerticalSeparatingHinge,
            isTabletop = isTabletop,
            verticalHingeBounds = verticalHingeBounds,
            horizontalHingeBounds = horizontalHingeBounds,
        )
    }
}
