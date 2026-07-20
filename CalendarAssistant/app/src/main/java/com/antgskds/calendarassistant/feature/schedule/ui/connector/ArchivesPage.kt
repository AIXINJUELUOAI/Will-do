package com.antgskds.calendarassistant.feature.schedule.ui.connector

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.schedule.domain.ScheduleDisplayHelper
import com.antgskds.calendarassistant.feature.schedule.ui.contract.ArchivesDateGroupUi
import com.antgskds.calendarassistant.feature.schedule.ui.contract.ArchivesUiAction
import com.antgskds.calendarassistant.feature.schedule.ui.contract.ArchivesUiState
import com.antgskds.calendarassistant.feature.schedule.ui.render.material.component.SwipeableEventItem
import com.antgskds.calendarassistant.app.ui.state.MainViewModel
import java.time.format.DateTimeFormatter

@Composable
fun ArchivesPage(viewModel: MainViewModel) {
    val archivedEvents by viewModel.archivedEvents.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchArchivedEvents()
    }

    val reverseOrderEnabled = uiState.settings.archivesListReverseOrder
    val groups = remember(archivedEvents, reverseOrderEnabled) {
        val items = archivedEvents
            .filter { it.archivedAt != null && it.id != null }
            .distinctBy { it.id }
            .map(ScheduleDisplayHelper::eventToSingleItem)
        val grouped = if (reverseOrderEnabled) {
            items.sortedByDescending { it.endDate }.groupBy { it.endDate }.toSortedMap(reverseOrder())
        } else {
            items.sortedBy { it.endDate }.groupBy { it.endDate }.toSortedMap()
        }
        grouped.map { (date, dateItems) -> ArchivesDateGroupUi(date, dateItems) }
    }

    MaterialArchivesScreen(
        state = ArchivesUiState(
            groups = groups,
            currentYear = uiState.today.year,
            timeRefreshToken = uiState.timeRefreshToken,
            hapticEnabled = uiState.settings.hapticFeedbackEnabled
        ),
        onAction = { action ->
            when (action) {
                is ArchivesUiAction.Delete -> viewModel.deleteArchivedEvent(action.eventId)
                is ArchivesUiAction.Restore -> viewModel.restoreEvent(action.eventId)
            }
        }
    )
}

@Composable
fun MaterialArchivesScreen(state: ArchivesUiState, onAction: (ArchivesUiAction) -> Unit) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(modifier = Modifier.fillMaxSize()) {
        if (state.groups.isEmpty()) {
            Box(modifier = Modifier.align(Alignment.Center)) {
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
                state.groups.forEach { group ->
                    item(key = "header_${group.date}") {
                        val headerText = if (group.date.year == state.currentYear) {
                            group.date.format(DateTimeFormatter.ofPattern("M月d日"))
                        } else {
                            group.date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
                        }
                        Text(
                            text = "—— $headerText",
                            modifier = Modifier.padding(vertical = 16.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    items(group.items, key = { it.stableKey }) { item ->
                        SwipeableEventItem(
                            item = item,
                            isRevealed = false,
                            timeRefreshToken = state.timeRefreshToken,
                            onExpand = {},
                            onCollapse = {},
                            onDelete = { item.eventId?.let { onAction(ArchivesUiAction.Delete(it)) } },
                            onEdit = {},
                            isArchivePage = true,
                            onRestore = { item.eventId?.let { onAction(ArchivesUiAction.Restore(it)) } },
                            hapticEnabled = state.hapticEnabled
                        )
                    }
                }
            }
        }
    }
}
