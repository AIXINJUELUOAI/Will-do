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
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.ui.event_display.SwipeableEventItem
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel

/**
 * 修复说明：
 * 修正了 SwipeableEventItem 的参数调用错误：
 * 1. onToggleImportant -> onImportant
 * 2. 移除了不存在的 onClick 参数
 */
@Composable
fun AllEventsPage(
    viewModel: MainViewModel,
    onEditEvent: (MyEvent) -> Unit,
    uiSize: Int = 2 // 1=小, 2=中, 3=大
) {
    val uiState by viewModel.uiState.collectAsState()

    // 1. 本地 UI 状态
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableIntStateOf(0) } // 0=日程, 1=临时

    // 2. 核心过滤逻辑
    val filteredEvents by remember(uiState.allEvents, searchQuery, selectedCategory) {
        derivedStateOf {
            uiState.allEvents.filter { event ->
                // 分类匹配
                val categoryMatch = if (selectedCategory == 0) {
                    event.eventType != "temp" // 日程事件
                } else {
                    event.eventType == "temp" // 临时事件
                }

                // 搜索匹配
                val searchMatch = if (searchQuery.isBlank()) true else {
                    event.title.contains(searchQuery, ignoreCase = true) ||
                            event.description.contains(searchQuery, ignoreCase = true) ||
                            event.location.contains(searchQuery, ignoreCase = true)
                }
                categoryMatch && searchMatch
            }.sortedByDescending { it.startDate }
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

        // B. 顶部 Tab (日程 vs 临时)
        TabRow(selectedTabIndex = selectedCategory) {
            Tab(
                selected = selectedCategory == 0,
                onClick = { selectedCategory = 0 },
                text = { Text("日程事件") }
            )
            Tab(
                selected = selectedCategory == 1,
                onClick = { selectedCategory = 1 },
                text = { Text("临时事件") }
            )
        }

        // C. 列表内容
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            // 底部留白给 FAB，顶部留一点呼吸感
            contentPadding = PaddingValues(bottom = 80.dp, top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 空状态
            if (filteredEvents.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val emptyText = if (searchQuery.isBlank()) {
                            if (selectedCategory == 0) "暂无日程记录" else "暂无临时取件码"
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
                    if (selectedCategory == 0) {
                        Text(
                            text = "${event.startDate} ~ ${event.endDate}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)
                        )
                    } else {
                        Text(
                            text = "创建于: ${event.startDate}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.padding(bottom = 4.dp, top = 8.dp)
                        )
                    }

                    // 滑动组件
                    SwipeableEventItem(
                        event = event,
                        isRevealed = uiState.revealedEventId == event.id,
                        onExpand = { viewModel.onRevealEvent(event.id) },
                        onCollapse = { viewModel.onRevealEvent(null) },
                        onDelete = { viewModel.deleteEvent(event) },
                        onImportant = { viewModel.toggleImportant(event) }, // 修正参数名
                        onEdit = { onEditEvent(event) }, // 移除 onClick，仅保留 onEdit
                        uiSize = uiSize
                    )
                }
            }
        }
    }
}