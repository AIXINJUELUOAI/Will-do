package com.antgskds.calendarassistant.ui.page_display

import androidx.compose.foundation.layout.*
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarBottomSpacing
import com.antgskds.calendarassistant.ui.components.IntegratedFloatingBarHeight
import com.antgskds.calendarassistant.core.util.DateCalculator
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import java.time.LocalDate
import com.antgskds.calendarassistant.ui.event_display.SwipeableEventItem
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import java.time.format.DateTimeFormatter

@Composable
fun AllEventsPage(
    viewModel: MainViewModel,
    onEditItem: (ScheduleDisplayItem) -> Unit,
    onRequestDeleteItem: (ScheduleDisplayItem) -> Unit = {},
    uiSize: Int = 2,
    pickupTimestamp: Long = 0L,
    searchQuery: String = "",
    extraBottomPadding: Dp = 0.dp
) {
    val uiState by viewModel.uiState.collectAsState()
    val today = LocalDate.now()

    // 核心过滤逻辑
    val filteredItems by remember(uiState.allScheduleItems, searchQuery, today) {
        derivedStateOf {
            uiState.allScheduleItems
                .filter { it.tag != EventTags.NOTE }
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
            val currentYear = remember { LocalDate.now().year }

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
                                onExpand = { viewModel.onRevealItem(item.stableKey) },
                                onCollapse = { viewModel.onRevealItem(null) },
                                onDelete = { item.eventId?.let { id -> viewModel.deleteEvent(id) } },
                                onEdit = { onEditItem(item) },
                                onLongPress = { onRequestDeleteItem(item) },
                                uiSize = uiSize,
                                isArchivePage = false,
                                onArchive = { viewModel.archiveItem(item.action) }
                            )
                        }
                    }
                }
            }
        }
    }
}
