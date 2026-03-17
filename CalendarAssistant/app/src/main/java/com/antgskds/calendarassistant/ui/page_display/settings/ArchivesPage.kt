package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.antgskds.calendarassistant.ui.components.FloatingActionCard
import com.antgskds.calendarassistant.ui.event_display.SwipeableEventItem
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchivesPage(
    viewModel: MainViewModel,
    onBack: () -> Unit
) {
    val archivedEvents by viewModel.archivedEvents.collectAsState()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    var showClearConfirmDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.fetchArchivedEvents()
    }

    val groupedEvents = remember(archivedEvents) {
        archivedEvents
            .distinctBy { it.id }
            .sortedByDescending { it.endDate }
            .groupBy { it.endDate }
            .toSortedMap(reverseOrder())
    }

    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy年M月d日") }

    // 最外层容器
    Box(modifier = Modifier.fillMaxSize()) {
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
                        if (groupedEvents.isNotEmpty()) {
                            IconButton(onClick = { showClearConfirmDialog = true }) {
                                Icon(
                                    Icons.Default.DeleteSweep,
                                    "清空归档",
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                )
            }
        ) { padding ->
            // --- Scaffold Content 开始 ---
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                if (groupedEvents.isEmpty()) {
                    Box(
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Text("暂无归档", color = MaterialTheme.colorScheme.secondary)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp + bottomInset
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        groupedEvents.forEach { (date, events) ->
                            item(key = "header_${date}") {
                                Column {
                                    HorizontalDivider(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                                        thickness = 1.dp
                                    )
                                    Text(
                                        text = "—— ${date.format(dateFormatter)}",
                                        modifier = Modifier.padding(vertical = 16.dp),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

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
            // --- Scaffold Content 结束 ---
        } // ✅ 关键修复：这里补上了之前遗漏的 Scaffold 的闭合括号

        // 半透明遮罩层 (放在 Scaffold 外部，遮住全局)
        AnimatedVisibility(
            visible = showClearConfirmDialog,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null, // 去除点击波纹
                        onClick = { showClearConfirmDialog = false }
                    )
            )
        }

        // 悬浮操作卡片
        FloatingActionCard(
            visible = showClearConfirmDialog,
            title = "确认清空",
            content = "此操作将永久删除 ${groupedEvents.values.sumOf { it.size }} 条归档事件。\n删除后将无法恢复。",
            confirmText = "删除",
            dismissText = "取消",
            isDestructive = true,
            isLoading = false,
            onConfirm = {
                showClearConfirmDialog = false
                viewModel.clearAllArchives()
            },
            onDismiss = { showClearConfirmDialog = false },
            modifier = Modifier.align(Alignment.BottomCenter) // ✅ 直接把 Modifier 传给组件，省略外层 Box
        )
    }
}