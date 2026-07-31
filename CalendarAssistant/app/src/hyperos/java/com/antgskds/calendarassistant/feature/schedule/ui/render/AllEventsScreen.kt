package com.antgskds.calendarassistant.feature.schedule.ui.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.feature.schedule.ui.contract.AllEventsUiAction
import com.antgskds.calendarassistant.feature.schedule.ui.contract.AllEventsUiState
import com.antgskds.calendarassistant.feature.home.ui.render.LocalHyperHomeContentInsets
import java.time.format.DateTimeFormatter
import java.util.Locale
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AllEventsScreen(
    state: AllEventsUiState,
    uiSize: Int,
    extraBottomPadding: Dp,
    hapticEnabled: Boolean,
    onAction: (AllEventsUiAction) -> Unit,
) {
    val contentInsets = LocalHyperHomeContentInsets.current
    var loadingMore by remember { mutableStateOf(false) }
    val refreshState = rememberPullToRefreshState()
    val formatter = remember { DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA) }

    LaunchedEffect(state.futureDays) {
        loadingMore = false
    }

    PullToRefresh(
        isRefreshing = loadingMore,
        onRefresh = {
            loadingMore = true
            onAction(AllEventsUiAction.LoadMoreFuture)
        },
        pullToRefreshState = refreshState,
        color = MiuixTheme.colorScheme.primary,
        refreshTexts = listOf("下拉加载更多", "松开加载", "正在加载", "加载完成"),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (state.searchQuery.isBlank()) "暂无日程记录" else "未找到相关日程",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            return@PullToRefresh
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = contentInsets.top + 8.dp,
                bottom = contentInsets.bottom + 24.dp + extraBottomPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.groups.forEach { group ->
                item(key = "header_${group.date}") {
                    Text(
                        text = group.date.format(formatter),
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                items(group.items, key = { it.stableKey }) { item ->
                    Box(modifier = Modifier.padding(horizontal = 16.dp)) {
                        HyperEventItem(
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
                            hapticEnabled = hapticEnabled,
                        )
                    }
                }
            }
        }
    }
}
