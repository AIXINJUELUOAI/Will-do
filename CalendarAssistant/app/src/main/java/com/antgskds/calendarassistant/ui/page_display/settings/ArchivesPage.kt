package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.ui.event_display.SwipeableEventItem
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import com.antgskds.calendarassistant.calendar.models.endDate
import java.time.format.DateTimeFormatter

@Composable
fun ArchivesPage(
    viewModel: MainViewModel
) {
    val archivedEvents by viewModel.archivedEvents.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    LaunchedEffect(Unit) {
        viewModel.fetchArchivedEvents()
    }

    val reverseOrderEnabled = uiState.settings.archivesListReverseOrder
    val groupedEvents = remember(archivedEvents, reverseOrderEnabled) {
        val base = archivedEvents
            .filter { it.archivedAt != null }
            .distinctBy { it.id }
        if (reverseOrderEnabled) {
            // 倒序（默认）：结束日期从晚到早
            base.sortedByDescending { it.endDate }
                .groupBy { it.endDate }
                .toSortedMap(reverseOrder())
        } else {
            // 正序：结束日期从早到晚
            base.sortedBy { it.endDate }
                .groupBy { it.endDate }
                .toSortedMap()
        }
    }

    val currentYear = uiState.today.year

    Box(modifier = Modifier.fillMaxSize()) {
        if (groupedEvents.isEmpty()) {
            Box(
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text("暂无归档", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f))
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
                    item(key = "header_$date") {
                        val headerText = if (date.year == currentYear) {
                            date.format(DateTimeFormatter.ofPattern("M月d日"))
                        } else {
                            date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
                        }
                        Text(
                            text = "—— $headerText",
                            modifier = Modifier.padding(vertical = 16.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    items(events, key = { it.id ?: 0L }) { event ->
                        val displayItem = com.antgskds.calendarassistant.core.center.ScheduleDisplayHelper.eventToSingleItem(event)
                        SwipeableEventItem(
                            item = displayItem,
                            isRevealed = false,
                            timeRefreshToken = uiState.timeRefreshToken,
                            onExpand = {},
                            onCollapse = {},
                            onDelete = { event.id?.let { id -> viewModel.deleteArchivedEvent(id) } },
                            onEdit = {},
                            isArchivePage = true,
                            onRestore = { event.id?.let { id -> viewModel.restoreEvent(id) } },
                            hapticEnabled = uiState.settings.hapticFeedbackEnabled
                        )
                    }
                }
            }
        }
    }
}
