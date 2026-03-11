package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    }

    if (showClearConfirmDialog) {
        val archiveCount = groupedEvents.values.sumOf { it.size }
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "确认清空",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "此操作将永久删除 $archiveCount 条归档事件。\n删除后将无法恢复。",
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirmDialog = false
                        viewModel.clearAllArchives()
                    },
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 0.dp),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                        .height(36.dp)
                ) {
                    Text("删除", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showClearConfirmDialog = false },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    modifier = Modifier
                        .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp)
                        .height(36.dp)
                ) {
                    Text(
                        text = "取消",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        )
    }
}
