package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
                        IconButton(onClick = { viewModel.clearAllArchives() }) {
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
}
