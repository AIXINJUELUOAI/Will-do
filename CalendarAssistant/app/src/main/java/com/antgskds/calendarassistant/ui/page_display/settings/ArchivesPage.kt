package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.ui.event_display.SwipeableEventItem
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import java.time.YearMonth
import androidx.compose.runtime.LaunchedEffect

/**
 * 归档页面
 * 按月分组显示已归档的事件
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ArchivesPage(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val archivedEvents by viewModel.archivedEvents.collectAsState()

    // 🔥 修复：进入归档页面时懒加载归档数据
    LaunchedEffect(Unit) {
        viewModel.fetchArchivedEvents()
    }

    // 按月分组并排序（最新月份在前）
    val groupedEvents = remember(archivedEvents) {
        archivedEvents
            .sortedByDescending { it.endDate }
            .groupBy {
                YearMonth.from(it.endDate)
            }
            .toSortedMap(reverseOrder())
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("归档") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Outlined.ArrowBack, "返回")
                    }
                },
                actions = {
                    // 清空归档按钮
                    if (groupedEvents.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.clearAllArchives()
                        }) {
                            Icon(Icons.Default.DeleteSweep, "清空归档")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (groupedEvents.isEmpty()) {
            // 空状态
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无归档", color = MaterialTheme.colorScheme.secondary)
            }
        } else {
            // 归档列表
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = padding
            ) {
                groupedEvents.forEach { (yearMonth, events) ->
                    // 粘性月份标题
                    stickyHeader {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${yearMonth.year}年 ${yearMonth.monthValue}月",
                                modifier = Modifier.padding(16.dp, 8.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 事件列表
                    items(events, key = { it.id }) { event ->
                        SwipeableEventItem(
                            event = event,
                            isRevealed = false,
                            onExpand = {},
                            onCollapse = {},
                            onDelete = { viewModel.deleteArchivedEvent(it.id) },
                            onImportant = {},
                            onEdit = {},
                            isArchivePage = true,
                            onRestore = { viewModel.restoreEvent(it.id) }
                        )
                    }
                }
            }
        }
    }
}
