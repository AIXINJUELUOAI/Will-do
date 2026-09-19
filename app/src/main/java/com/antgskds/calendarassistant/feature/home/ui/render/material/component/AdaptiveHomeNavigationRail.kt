package com.antgskds.calendarassistant.feature.home.ui.render.material.component

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.app.ui.theme.material.background.AppBackgroundGlassSurface
import com.antgskds.calendarassistant.feature.home.domain.HomeEntryKey

@Composable
fun AdaptiveHomeNavigationRail(
    navItems: List<String>,
    selectedPageKey: String,
    settingsSelected: Boolean = false,
    backgroundMode: Boolean,
    miuiBlurEnabled: Boolean,
    onPageClick: (String) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppBackgroundGlassSurface(
        enabled = backgroundMode,
        miuiBlurEnabled = miuiBlurEnabled,
        modifier = modifier
            .width(88.dp)
            .fillMaxHeight(),
        shape = RectangleShape,
        borderWidth = 0.dp,
        surfaceColor = if (backgroundMode) {
            MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        NavigationRail(
            modifier = Modifier.fillMaxHeight(),
            containerColor = Color.Transparent,
            header = { Spacer(modifier = Modifier.size(8.dp)) },
        ) {
            navItems.forEach { pageKey ->
                val item = adaptiveRailItem(pageKey) ?: return@forEach
                NavigationRailItem(
                    selected = !settingsSelected && selectedPageKey == pageKey,
                    onClick = { onPageClick(pageKey) },
                    icon = {
                        Icon(
                            painter = painterResource(item.iconRes),
                            contentDescription = item.label,
                            modifier = Modifier.size(26.dp),
                        )
                    },
                    label = { Text(item.label) },
                    alwaysShowLabel = true,
                    colors = adaptiveRailItemColors(),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            NavigationRailItem(
                selected = settingsSelected,
                onClick = onSettingsClick,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "设置",
                        modifier = Modifier.size(26.dp),
                    )
                },
                label = { Text("设置") },
                alwaysShowLabel = true,
                colors = adaptiveRailItemColors(),
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}

private data class AdaptiveRailItem(
    val label: String,
    val iconRes: Int,
)

private fun adaptiveRailItem(pageKey: String): AdaptiveRailItem? = when (pageKey) {
    HomeEntryKey.TODAY -> AdaptiveRailItem("今日", R.drawable.floatingbar_today)
    HomeEntryKey.ALL -> AdaptiveRailItem("全部", R.drawable.floatingbar_all)
    HomeEntryKey.NOTE -> AdaptiveRailItem("随口记", R.drawable.ic_stat_quickmemo)
    else -> null
}

@Composable
private fun adaptiveRailItemColors() = NavigationRailItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedTextColor = MaterialTheme.colorScheme.onSurface,
    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
