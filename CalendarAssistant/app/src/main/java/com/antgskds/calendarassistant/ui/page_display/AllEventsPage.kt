package com.antgskds.calendarassistant.ui.page_display

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import com.antgskds.calendarassistant.core.calendar.RecurringEventUtils
import com.antgskds.calendarassistant.core.util.DateCalculator
import com.antgskds.calendarassistant.data.model.MyEvent
import java.time.LocalDate
import com.antgskds.calendarassistant.ui.event_display.SwipeableEventItem
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel

@Composable
fun AllEventsPage(
    viewModel: MainViewModel,
    onEditEvent: (MyEvent) -> Unit,
    uiSize: Int = 2,
    pickupTimestamp: Long = 0L
) {
    val uiState by viewModel.uiState.collectAsState()

    // 1. 本地 UI 状态
    var searchQuery by remember { mutableStateOf("") }

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
            }.sortedWith(
                compareBy(
                    // 1. 过期状态（未过期在前）
                    { event -> DateCalculator.isEventExpired(event) },
                    // 2. 重要性（重要在前）
                    { event -> !event.isImportant },
                    // 3. 折中方案：事件未开始按开始日，已开始按结束日
                    { event ->
                        val isExpired = DateCalculator.isEventExpired(event)
                        val today = LocalDate.now()
                        val isStarted = event.startDate.isBefore(today) || event.startDate == today
                        if (isExpired) {
                            -event.endDate.toEpochDay() // 已过期，按结束日倒序（最近过期的在前）
                        } else if (isStarted) {
                            event.endDate.toEpochDay() // 未过期且已开始，按结束日正序（快结束的在前）
                        } else {
                            -event.startDate.toEpochDay() // 未开始，按开始日倒序（最近开始的在前）
                        }
                    }
                )
            )
        }
    }

    // 🔥 直接是一个 Column，没有 Scaffold 了
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // A. 搜索栏
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            placeholder = { Text("搜索标题、备注或地点...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "搜索") },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "清除")
                    }
                }
            } else null,
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )

        // 过滤后的本地数据用于显示
        val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        // 列表内容
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 80.dp + bottomInset, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 空状态
            if (filteredEvents.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val emptyText = if (searchQuery.isBlank()) {
                            "暂无日程记录"
                        } else {
                            "未找到相关日程"
                        }
                        Text(emptyText, color = Color.Gray)
                    }
                }
            }

            // 列表项
            items(filteredEvents, key = { it.id }) { event ->
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
                        modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)
                    )

                    // 滑动组件
                    SwipeableEventItem(
                        event = event,
                        isRevealed = uiState.revealedEventId == event.id,
                        onExpand = { viewModel.onRevealEvent(event.id) },
                        onCollapse = { viewModel.onRevealEvent(null) },
                        onDelete = { viewModel.deleteEvent(event) },
                        onImportant = { viewModel.toggleImportant(event) }, // 修正参数名
                        onEdit = { onEditEvent(event) }, // 移除 onClick，仅保留 onEdit
                        uiSize = uiSize,
                        isArchivePage = false,
                        onArchive = { viewModel.archiveEvent(it.id) } // 归档回调
                    )
                }
            }
        }
    }
}
