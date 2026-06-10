package com.antgskds.calendarassistant.ui.page_display

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
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
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarBottomSpacing
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarHeight
import com.antgskds.calendarassistant.core.util.DateCalculator
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import java.time.LocalDate
import com.antgskds.calendarassistant.ui.event_display.SwipeableEventItem
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllEventsPage(
    viewModel: MainViewModel,
    onEditItem: (ScheduleDisplayItem) -> Unit,
    onRequestDeleteItem: (ScheduleDisplayItem) -> Unit = {},
    uiSize: Int = 2,
    pickupTimestamp: Long = 0L,
    searchQuery: String = "",
    extraBottomPadding: Dp = 0.dp,
    hapticEnabled: Boolean = true
) {
    val uiState by viewModel.uiState.collectAsState()
    val today = uiState.today
    var isLoadingMoreFuture by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val futureLimitFormatter = remember { DateTimeFormatter.ofPattern("M月d日", java.util.Locale.CHINA) }
    val futureLimitText = remember(uiState.allEventsFutureLimit) {
        uiState.allEventsFutureLimit.format(futureLimitFormatter)
    }
    val nextFutureLimitText = remember(uiState.allEventsFutureLimit) {
        uiState.allEventsFutureLimit.plusDays(15).format(futureLimitFormatter)
    }

    LaunchedEffect(uiState.allEventsFutureDays) {
        isLoadingMoreFuture = false
    }

    // 核心过滤逻辑
    val filteredItems by remember(uiState.allScheduleItems, searchQuery, today, uiState.timeRefreshToken) {
        derivedStateOf {
            uiState.allScheduleItems
                .distinctBy { it.stableKey }
                .filter { item ->
                val searchMatch = if (searchQuery.isBlank()) true else {
                    item.title.contains(searchQuery, ignoreCase = true) ||
                            item.description.contains(searchQuery, ignoreCase = true) ||
                            item.location.contains(searchQuery, ignoreCase = true)
                }
                searchMatch
            }.sortedWith { a, b ->
                val now = java.time.LocalDateTime.now()
                val aExpired = try { java.time.LocalDateTime.of(a.endDate, a.endLocalTime).isBefore(now) } catch (_: Exception) { false }
                val bExpired = try { java.time.LocalDateTime.of(b.endDate, b.endLocalTime).isBefore(now) } catch (_: Exception) { false }
                when {
                    aExpired != bExpired -> if (aExpired) 1 else -1
                    else -> {
                        fun dateKey(e: ScheduleDisplayItem, expired: Boolean): Long {
                            val started = e.startDate.isBefore(today) || e.startDate == today
                            return when {
                                expired -> -e.endDate.toEpochDay()
                                started -> e.endDate.toEpochDay()
                                else -> -e.startDate.toEpochDay()
                            }
                        }
                        val dateCmp = dateKey(a, aExpired).compareTo(dateKey(b, bExpired))
                        if (dateCmp != 0) dateCmp
                        else a.startTime.compareTo(b.startTime)
                    }
                }
            }
        }
    }

    // 按日期分组（用于显示日期分割线）
    val groupedItems = remember(filteredItems) {
        filteredItems.groupBy { it.startDate }
    }

    PullToRefreshBox(
        isRefreshing = isLoadingMoreFuture,
        onRefresh = {
            isLoadingMoreFuture = true
            viewModel.loadMoreFutureAllEvents()
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
            if (filteredItems.isEmpty()) {
                // 空状态居中显示
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.align(Alignment.Center)) {
                        val emptyText = if (searchQuery.isBlank()) {
                            "暂无日程记录"
                        } else {
                            "未找到相关日程"
                        }
                        Text(emptyText, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            } else {
                val currentYear = today.year

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = floatingBarOffset + extraBottomPadding,
                        top = 8.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 按日期分组显示
                    groupedItems.forEach { (date, events) ->
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
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // 该日期下的所有事件
                        items(events, key = { it.stableKey }) { item ->
                            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                                SwipeableEventItem(
                                    item = item,
                                    isRevealed = uiState.revealedItemKey == item.stableKey,
                                    timeRefreshToken = uiState.timeRefreshToken,
                                    onExpand = { viewModel.onRevealItem(item.stableKey) },
                                    onCollapse = { viewModel.onRevealItem(null) },
                                    onDelete = { item.eventId?.let { id -> viewModel.deleteEvent(id) } },
                                    onEdit = { onEditItem(item) },
                                    onLongPress = { onRequestDeleteItem(item) },
                                    uiSize = uiSize,
                                    isArchivePage = false,
                                    onArchive = { viewModel.archiveItem(item.action) },
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
