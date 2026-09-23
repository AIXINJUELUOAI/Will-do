package com.antgskds.calendarassistant.feature.home.ui.render.material.component

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.app.ui.theme.material.background.AppBackgroundGlassSurface
import com.antgskds.calendarassistant.feature.home.domain.HomeEntryKey

@Composable
fun AdaptiveHomeNavigationRail(
    navItems: List<String>,
    selectedPageKey: String,
    accountingSelected: Boolean = false,
    weatherSelected: Boolean = false,
    settingsSelected: Boolean = false,
    expanded: Boolean,
    canExpand: Boolean,
    backgroundMode: Boolean,
    miuiBlurEnabled: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPageClick: (String) -> Unit,
    onAccountingClick: () -> Unit,
    onWeatherClick: () -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val width by animateDpAsState(
        targetValue = if (expanded) 256.dp else 88.dp,
        label = "primary_navigation_width",
    )

    AppBackgroundGlassSurface(
        enabled = backgroundMode,
        miuiBlurEnabled = miuiBlurEnabled,
        modifier = modifier.width(width).fillMaxHeight(),
        shape = RectangleShape,
        borderWidth = 0.dp,
        surfaceColor = if (backgroundMode) {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (canExpand) {
                IconButton(
                    onClick = { onExpandedChange(!expanded) },
                    modifier = Modifier.align(if (expanded) Alignment.Start else Alignment.CenterHorizontally),
                ) {
                    Icon(
                        imageVector = if (expanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Default.Menu,
                        contentDescription = if (expanded) "收起导航栏" else "展开导航栏",
                    )
                }
            } else {
                Spacer(Modifier.height(48.dp))
            }

            Spacer(Modifier.height(4.dp))
            navItems.forEach { pageKey ->
                val item = adaptiveRailItem(pageKey) ?: return@forEach
                AdaptivePrimaryNavigationItem(
                    label = item.label,
                    selected = !settingsSelected && !accountingSelected && !weatherSelected && selectedPageKey == pageKey,
                    expanded = expanded,
                    onClick = { onPageClick(pageKey) },
                    icon = {
                        Icon(
                            painter = painterResource(item.iconRes),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )
                    },
                )
            }

            AdaptivePrimaryNavigationItem(
                label = "记账",
                selected = accountingSelected,
                expanded = expanded,
                onClick = onAccountingClick,
                icon = { Icon(Icons.Outlined.AccountBalanceWallet, null, Modifier.size(26.dp)) },
            )
            AdaptivePrimaryNavigationItem(
                label = "天气",
                selected = weatherSelected,
                expanded = expanded,
                onClick = onWeatherClick,
                icon = { Icon(Icons.Outlined.WbSunny, null, Modifier.size(26.dp)) },
            )

            Spacer(modifier = Modifier.weight(1f))
            HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp))
            AdaptivePrimaryNavigationItem(
                label = "设置与管理",
                selected = settingsSelected,
                expanded = expanded,
                onClick = onSettingsClick,
                icon = { Icon(Icons.Outlined.Settings, null, Modifier.size(26.dp)) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdaptivePrimaryNavigationItem(
    label: String,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    val color = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    val modifier = Modifier
        .fillMaxWidth()
        .height(if (expanded) 56.dp else 64.dp)
        .padding(vertical = 3.dp)
        .background(
            color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
            shape = RoundedCornerShape(28.dp),
        )
        .semantics { contentDescription = label }
        .selectable(selected = selected, role = Role.Tab, onClick = onClick)

    if (expanded) {
        Row(
            modifier = modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.material3.LocalContentColor provides color,
            ) { icon() }
            Spacer(Modifier.width(16.dp))
            Text(label, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(label) } },
            state = rememberTooltipState(),
        ) {
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides color,
                ) { icon() }
            }
        }
    }
}

private data class AdaptiveRailItem(val label: String, val iconRes: Int)

private fun adaptiveRailItem(pageKey: String): AdaptiveRailItem? = when (pageKey) {
    HomeEntryKey.TODAY -> AdaptiveRailItem("今日", R.drawable.floatingbar_today)
    HomeEntryKey.ALL -> AdaptiveRailItem("全部", R.drawable.floatingbar_all)
    HomeEntryKey.NOTE -> AdaptiveRailItem("随口记", R.drawable.ic_stat_quickmemo)
    else -> null
}
