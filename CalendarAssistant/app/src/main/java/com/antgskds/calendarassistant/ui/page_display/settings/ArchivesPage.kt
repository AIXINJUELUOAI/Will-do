package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import androidx.compose.runtime.LaunchedEffect

/**
 * 归档页面
 * 按日期分组显示已归档的事件
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

    // 按具体日期分组并排序（最新日期在前）
    val groupedEvents = remember(archivedEvents) {
        // ✅ 去重：防止重复 ID 导致崩溃
        archivedEvents
            .distinctBy { it.id }
            .sortedByDescending { it.endDate }
            .groupBy { it.endDate }
            .toSortedMap(reverseOrder())
    }

    // 日期格式化器
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy年M月d日") }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("归档") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            "返回",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                },
                actions = {
                    // 清空归档按钮
                    if (groupedEvents.isNotEmpty()) {
                        IconButton(onClick = {
                            viewModel.clearAllArchives()
                        }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                "清空归档",
                                modifier = Modifier.size(24.dp)
                            )
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
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = padding.calculateBottomPadding()
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                groupedEvents.forEach { (date, events) ->
                    // 粘性日期标题
                    stickyHeader {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface)
                        ) {
                            // 连续横线
                            HorizontalDivider(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                thickness = 1.dp
                            )
                            // 日期文本
                            Text(
                                text = "—— ${date.format(dateFormatter)}",
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
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
