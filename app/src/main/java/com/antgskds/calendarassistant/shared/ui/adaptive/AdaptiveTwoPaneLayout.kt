package com.antgskds.calendarassistant.shared.ui.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.shared.ui.material.component.AppTopBar

@Composable
fun AdaptiveTwoPaneTopBar(
    primaryTitle: String,
    secondaryTitle: String,
    primaryWidth: Dp,
    primaryNavigationIcon: @Composable () -> Unit = {},
    primaryActions: @Composable RowScope.() -> Unit = {},
    secondaryActions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(modifier = Modifier.width(primaryWidth)) {
            AppTopBar(
                title = primaryTitle,
                navigationIcon = primaryNavigationIcon,
                actions = primaryActions,
            )
        }
        VerticalDivider(
            modifier = Modifier.fillMaxHeight(),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Box(modifier = Modifier.weight(1f)) {
            AppTopBar(title = secondaryTitle, actions = secondaryActions)
        }
    }
}

@Composable
fun AdaptiveTwoPaneLayout(
    modifier: Modifier = Modifier,
    primaryFraction: Float = 0.45f,
    primaryMaxWidth: Dp? = null,
    primaryWidth: Dp? = null,
    primary: @Composable () -> Unit,
    secondary: @Composable () -> Unit,
) {
    val layoutInfo = LocalAdaptiveLayoutInfo.current
    val density = LocalDensity.current
    var originInWindow by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { originInWindow = it.positionInWindow() },
    ) {
        val availableWidth = maxWidth
        val verticalHinge = layoutInfo.verticalHingeBounds
        val horizontalHinge = layoutInfo.horizontalHingeBounds
        val localVerticalStart = verticalHinge?.let {
            with(density) { it.left.toDp() - originInWindow.x.toDp() }
        }
        val localVerticalEnd = verticalHinge?.let {
            with(density) { it.right.toDp() - originInWindow.x.toDp() }
        }
        val localHorizontalStart = horizontalHinge?.let {
            with(density) { it.top.toDp() - originInWindow.y.toDp() }
        }
        val localHorizontalEnd = horizontalHinge?.let {
            with(density) { it.bottom.toDp() - originInWindow.y.toDp() }
        }

        when {
            localVerticalStart != null && localVerticalEnd != null &&
                localVerticalStart > 0.dp && localVerticalEnd < maxWidth -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .width(localVerticalStart)
                            .fillMaxHeight(),
                    ) { primary() }
                    Spacer(
                        modifier = Modifier
                            .width((localVerticalEnd - localVerticalStart).coerceAtLeast(1.dp))
                            .fillMaxHeight(),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) { secondary() }
                }
            }

            layoutInfo.isTabletop && localHorizontalStart != null && localHorizontalEnd != null &&
                localHorizontalStart > 0.dp && localHorizontalEnd < maxHeight -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(localHorizontalStart),
                    ) { primary() }
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((localHorizontalEnd - localHorizontalStart).coerceAtLeast(1.dp)),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) { secondary() }
                }
            }

            else -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (primaryWidth != null) {
                        Box(
                            modifier = Modifier
                                .width(primaryWidth.coerceAtMost(availableWidth * 0.75f))
                                .fillMaxHeight(),
                        ) { primary() }
                    } else if (primaryMaxWidth != null) {
                        Box(
                            modifier = Modifier
                                .width(
                                    (availableWidth * primaryFraction.coerceIn(0.25f, 0.75f))
                                        .coerceAtMost(primaryMaxWidth)
                                )
                                .fillMaxHeight(),
                        ) { primary() }
                    } else {
                        Box(
                            modifier = Modifier
                                .weight(primaryFraction.coerceIn(0.25f, 0.75f))
                                .fillMaxHeight(),
                        ) { primary() }
                    }
                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Box(
                        modifier = Modifier
                            .weight(
                                if (primaryWidth == null && primaryMaxWidth == null) {
                                    (1f - primaryFraction).coerceIn(0.25f, 0.75f)
                                } else {
                                    1f
                                }
                            )
                            .fillMaxHeight(),
                    ) { secondary() }
                }
            }
        }
    }
}
