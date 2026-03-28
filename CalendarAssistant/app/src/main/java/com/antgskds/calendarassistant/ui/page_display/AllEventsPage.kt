package com.antgskds.calendarassistant.ui.page_display

import androidx.compose.foundation.layout.*
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
import com.antgskds.calendarassistant.core.calendar.RecurringEventUtils
import com.antgskds.calendarassistant.core.util.DateCalculator
import com.antgskds.calendarassistant.data.model.MyEvent
import java.time.LocalDate
import com.antgskds.calendarassistant.ui.event_display.SwipeableEventItem
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import java.time.format.DateTimeFormatter

@Composable
fun AllEventsPage(
    viewModel: MainViewModel,
    onEditEvent: (MyEvent) -> Unit,
    uiSize: Int = 2,
    pickupTimestamp: Long = 0L,
    searchQuery: String = "",
    extraBottomPadding: Dp = 0.dp
) {
    val uiState by viewModel.uiState.collectAsState()

    // 核心过滤逻辑
    val filteredEvents by remember(uiState.allEvents, searchQuery) {
        derivedStateOf {
            uiState.allEvents
                .filter { event -> !event.isRecurring || event.isRecurringParent }
                .distinctBy { it.id }
                .filter { event ->
                // 搜索匹配
                val searchMatch = if (searchQuery.isBlank()) true else {
                    event.title.contains(searchQuery, ignoreCase = true) ||
                            event.description.contains(searchQuery, ignoreCase = true) ||
                            event.location.contains(searchQuery, ignoreCase = true)
                }
                searchMatch
            }.sortedWith { a, b ->
                val aExpired = DateCalculator.isEventExpired(a)
                val bExpired = DateCalculator.isEventExpired(b)
                when {
                    aExpired != bExpired -> if (aExpired) 1 else -1
                    a.isImportant != b.isImportant -> if (a.isImportant) -1 else 1
                    else -> {
                        val today = LocalDate.now()
                        fun dateKey(e: MyEvent, expired: Boolean): Long {
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
    val groupedEvents = remember(filteredEvents) {
        filteredEvents.groupBy { it.startDate }
    }

    // 🔥 直接是一个 Column，没有 Scaffold 了
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 过滤后的本地数据用于显示
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val floatingBarOffset = IntegratedFloatingBarHeight + IntegratedFloatingBarBottomSpacing + bottomInset

        // 列表内容
        if (filteredEvents.isEmpty()) {
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
            val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy年M月d日 EEEE", java.util.Locale.CHINA) }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = floatingBarOffset + extraBottomPadding,
                    top = 8.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 按日期分组显示
                groupedEvents.forEach { (date, events) ->
                    // 日期分割线头部
                    item(key = "header_${date}") {
                        Column {
                            HorizontalDivider(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                thickness = 1.dp
                            )
                            Text(
                                text = "—— ${date.format(dateFormatter)}",
                                modifier = Modifier.padding(vertical = 16.dp, horizontal = 20.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // 该日期下的所有事件
                    items(events, key = { it.id }) { event ->
                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                            // 头部日期信息
                            Text(
                                text = if (event.isRecurringParent) {
                                    "下次：${RecurringEventUtils.formatMillis(event.nextOccurrenceStartMillis) ?: "暂无未来实例"}"
                                } else {
                                    "${event.startDate} ~ ${event.endDate}"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )

                            // 滑动组件
                            SwipeableEventItem(
                                event = event,
                                isRevealed = uiState.revealedEventId == event.id,
                                onExpand = { viewModel.onRevealEvent(event.id) },
                                onCollapse = { viewModel.onRevealEvent(null) },
                                onDelete = { viewModel.deleteEvent(event) },
                                onImportant = { viewModel.toggleImportant(event) },
                                onEdit = { onEditEvent(event) },
                                uiSize = uiSize,
                                isArchivePage = false,
                                onArchive = { viewModel.archiveEvent(it.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}
