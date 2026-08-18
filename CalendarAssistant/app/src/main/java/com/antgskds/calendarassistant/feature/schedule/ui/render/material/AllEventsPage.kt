package com.antgskds.calendarassistant.feature.schedule.ui.render.material

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.home.ui.render.material.component.IntegratedFloatingBarBottomSpacing
import com.antgskds.calendarassistant.feature.home.ui.render.material.component.IntegratedFloatingBarHeight
import com.antgskds.calendarassistant.feature.schedule.ui.contract.AllEventsUiAction
import com.antgskds.calendarassistant.feature.schedule.ui.contract.AllEventsUiState
import com.antgskds.calendarassistant.feature.schedule.ui.render.material.component.SwipeableEventItem
import com.antgskds.calendarassistant.shared.ui.adaptive.AdaptiveTwoPaneLayout
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialAllEventsScreen(
    state: AllEventsUiState,
    uiSize: Int = 2,
    extraBottomPadding: Dp = 0.dp,
    twoPane: Boolean = false,
    hapticEnabled: Boolean = true,
    onAction: (AllEventsUiAction) -> Unit
) {
    var selectedItemKey by rememberSaveable { mutableStateOf<String?>(null) }
    val allItems = remember(state.groups) { state.groups.flatMap { it.items } }
    val allItemKeys = remember(allItems) { allItems.map { it.stableKey } }

    LaunchedEffect(twoPane, allItemKeys) {
        if (!twoPane) {
            selectedItemKey = null
        } else if (selectedItemKey !in allItemKeys) {
            selectedItemKey = allItemKeys.firstOrNull()
        }
    }

    if (!twoPane) {
        AllEventsListPane(
            state = state,
            uiSize = uiSize,
            extraBottomPadding = extraBottomPadding,
            hapticEnabled = hapticEnabled,
            reserveFloatingBarSpace = true,
            selectedItemKey = null,
            onSelectItem = null,
            onAction = onAction,
        )
        return
    }

    AdaptiveTwoPaneLayout(
        primaryFraction = 0.46f,
        primary = {
            AllEventsListPane(
                state = state,
                uiSize = uiSize,
                extraBottomPadding = extraBottomPadding,
                hapticEnabled = hapticEnabled,
                reserveFloatingBarSpace = false,
                selectedItemKey = selectedItemKey,
                onSelectItem = { selectedItemKey = it.stableKey },
                onAction = onAction,
            )
        },
        secondary = {
            val selectedItem = allItems.firstOrNull { it.stableKey == selectedItemKey }
            if (selectedItem == null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "选择一条日程查看详情",
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                AllEventDetailPane(
                    item = selectedItem,
                    onEdit = { onAction(AllEventsUiAction.EditItem(selectedItem.stableKey)) },
                    onArchive = { onAction(AllEventsUiAction.ArchiveItem(selectedItem.stableKey)) },
                )
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllEventsListPane(
    state: AllEventsUiState,
    uiSize: Int,
    extraBottomPadding: Dp,
    hapticEnabled: Boolean,
    reserveFloatingBarSpace: Boolean,
    selectedItemKey: String?,
    onSelectItem: ((com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem) -> Unit)?,
    onAction: (AllEventsUiAction) -> Unit,
) {
    var isLoadingMoreFuture by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val futureLimitFormatter = remember { DateTimeFormatter.ofPattern("M月d日", java.util.Locale.CHINA) }
    val futureLimitText = remember(state.futureLimit) { state.futureLimit.format(futureLimitFormatter) }
    val nextFutureLimitText = remember(state.futureLimit) {
        state.futureLimit.plusDays(15).format(futureLimitFormatter)
    }

    LaunchedEffect(state.futureDays) { isLoadingMoreFuture = false }

    PullToRefreshBox(
        isRefreshing = isLoadingMoreFuture,
        onRefresh = {
            isLoadingMoreFuture = true
            onAction(AllEventsUiAction.LoadMoreFuture)
        },
        state = pullToRefreshState,
        indicator = {
            AllEventsFutureLoadIndicator(
                isRefreshing = isLoadingMoreFuture,
                state = pullToRefreshState,
                currentLimitText = futureLimitText,
                nextLimitText = nextFutureLimitText,
                modifier = Modifier.align(Alignment.TopCenter)
            )
        },
        modifier = Modifier.fillMaxSize()
    ) {
        // 🔥 直接是一个 Column，没有 Scaffold 了
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 过滤后的本地数据用于显示
            val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val floatingBarOffset = if (reserveFloatingBarSpace) {
                IntegratedFloatingBarHeight + IntegratedFloatingBarBottomSpacing + bottomInset
            } else {
                bottomInset + 24.dp
            }

            // 列表内容
            if (state.groups.isEmpty()) {
                // 空状态居中显示
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.align(Alignment.Center)) {
                        val emptyText = if (state.searchQuery.isBlank()) {
                            "暂无日程记录"
                        } else {
                            "未找到相关日程"
                        }
                        Text(emptyText, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f))
                    }
                }
            } else {
                val currentYear = state.today.year

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = floatingBarOffset + extraBottomPadding,
                        top = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 按日期分组显示
                    state.groups.forEach { group ->
                        val date = group.date
                        // 日期分割线头部
                        item(key = "header_${date}") {
                            val headerText = if (date.year == currentYear) {
                                date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", java.util.Locale.CHINA))
                            } else {
                                date.format(DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", java.util.Locale.CHINA))
                            }
                            Text(
                                text = "—— $headerText",
                                modifier = Modifier.padding(vertical = 16.dp, horizontal = 20.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }

                        // 该日期下的所有事件
                        items(group.items, key = { it.stableKey }) { item ->
                            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                                SwipeableEventItem(
                                    item = item,
                                    isRevealed = state.revealedItemKey == item.stableKey,
                                    selected = selectedItemKey == item.stableKey,
                                    timeRefreshToken = state.timeRefreshToken,
                                    onExpand = { onAction(AllEventsUiAction.RevealItem(item.stableKey)) },
                                    onCollapse = { onAction(AllEventsUiAction.CollapseItem) },
                                    onDelete = { onAction(AllEventsUiAction.DeleteItem(item.stableKey)) },
                                    onEdit = { onAction(AllEventsUiAction.EditItem(item.stableKey)) },
                                    onClick = onSelectItem?.let { select -> { select(item) } },
                                    onLongPress = { onAction(AllEventsUiAction.RequestDeleteItem(item.stableKey)) },
                                    uiSize = uiSize,
                                    isArchivePage = false,
                                    onArchive = { onAction(AllEventsUiAction.ArchiveItem(item.stableKey)) },
                                    hapticEnabled = hapticEnabled
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AllEventDetailPane(
    item: com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", java.util.Locale.CHINA) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(48.dp)
                    .background(item.composeColor, RoundedCornerShape(3.dp)),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        EventDetailRow(
            icon = Icons.Default.CalendarMonth,
            text = if (item.startDate == item.endDate) {
                item.startDate.format(dateFormatter)
            } else {
                "${item.startDate.format(dateFormatter)} - ${item.endDate.format(dateFormatter)}"
            },
        )
        EventDetailRow(
            icon = Icons.Default.Schedule,
            text = if (item.isAllDay) "全天" else "${item.startTime} - ${item.endTime}",
        )
        if (item.location.isNotBlank()) {
            EventDetailRow(icon = Icons.Default.LocationOn, text = item.location)
        }
        if (item.description.isNotBlank()) {
            EventDetailRow(icon = Icons.Default.Notes, text = item.description)
        }
        if (item.isRecurringInstance) {
            Text(
                text = "重复日程实例",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onArchive) { Text("归档") }
            Spacer(modifier = Modifier.width(12.dp))
            Button(onClick = onEdit) { Text("编辑") }
        }
    }
}

@Composable
private fun EventDetailRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AllEventsFutureLoadIndicator(
    isRefreshing: Boolean,
    state: PullToRefreshState,
    currentLimitText: String,
    nextLimitText: String,
    modifier: Modifier = Modifier
) {
    val thresholdPx = with(LocalDensity.current) { PullToRefreshDefaults.PositionalThreshold.toPx() }
    val isVisible = isRefreshing || state.distanceFraction > 0.01f
    val progress = state.distanceFraction.coerceIn(0f, 1f)
    val text = if (isRefreshing) {
        "正在加载到 $nextLimitText"
    } else {
        "已显示到 $currentLimitText，下拉加载到 $nextLimitText"
    }

    Surface(
        modifier = modifier
            .graphicsLayer {
                translationY = state.distanceFraction * thresholdPx - size.height
                alpha = if (isVisible) 1f else 0f
            }
            .padding(top = 8.dp),
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
