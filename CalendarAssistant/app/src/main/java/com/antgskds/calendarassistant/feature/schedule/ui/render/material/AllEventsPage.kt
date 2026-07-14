package com.antgskds.calendarassistant.feature.schedule.ui.render.material

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
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
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialAllEventsScreen(
    state: AllEventsUiState,
    uiSize: Int = 2,
    extraBottomPadding: Dp = 0.dp,
    hapticEnabled: Boolean = true,
    onAction: (AllEventsUiAction) -> Unit
) {
    var isLoadingMoreFuture by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val futureLimitFormatter = remember { DateTimeFormatter.ofPattern("M月d日", java.util.Locale.CHINA) }
    val futureLimitText = remember(state.futureLimit) {
        state.futureLimit.format(futureLimitFormatter)
    }
    val nextFutureLimitText = remember(state.futureLimit) {
        state.futureLimit.plusDays(15).format(futureLimitFormatter)
    }

    LaunchedEffect(state.futureDays) {
        isLoadingMoreFuture = false
    }

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
            val floatingBarOffset = IntegratedFloatingBarHeight + IntegratedFloatingBarBottomSpacing + bottomInset

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
                                    timeRefreshToken = state.timeRefreshToken,
                                    onExpand = { onAction(AllEventsUiAction.RevealItem(item.stableKey)) },
                                    onCollapse = { onAction(AllEventsUiAction.CollapseItem) },
                                    onDelete = { onAction(AllEventsUiAction.DeleteItem(item.stableKey)) },
                                    onEdit = { onAction(AllEventsUiAction.EditItem(item.stableKey)) },
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
