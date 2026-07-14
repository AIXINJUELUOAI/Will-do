package com.antgskds.calendarassistant.ui.connector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.ui.contract.AllEventsDateGroupUiModel
import com.antgskds.calendarassistant.ui.contract.AllEventsUiAction
import com.antgskds.calendarassistant.ui.contract.AllEventsUiState
import com.antgskds.calendarassistant.feature.schedule.ui.render.AllEventsScreen
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import java.time.LocalDate
import java.time.LocalDateTime

internal data class AllEventsConnection(
    val state: AllEventsUiState,
    val itemsByKey: Map<String, ScheduleDisplayItem>
)

@Composable
fun AllEventsRoute(
    viewModel: MainViewModel,
    onEditItem: (ScheduleDisplayItem) -> Unit,
    onRequestDeleteItem: (ScheduleDisplayItem) -> Unit = {},
    uiSize: Int = 2,
    searchQuery: String = "",
    extraBottomPadding: Dp = 0.dp,
    hapticEnabled: Boolean = true
) {
    val mainState by viewModel.uiState.collectAsState()
    val reverseOrderEnabled = mainState.settings.allEventsListReverseOrder
    val connection = remember(
        mainState.allScheduleItems,
        searchQuery,
        mainState.today,
        mainState.timeRefreshToken,
        reverseOrderEnabled,
        mainState.revealedItemKey,
        mainState.allEventsFutureDays,
        mainState.allEventsFutureLimit
    ) {
        buildAllEventsConnection(
            items = mainState.allScheduleItems,
            searchQuery = searchQuery,
            today = mainState.today,
            now = LocalDateTime.now(),
            reverseOrderEnabled = reverseOrderEnabled,
            revealedItemKey = mainState.revealedItemKey,
            timeRefreshToken = mainState.timeRefreshToken,
            futureDays = mainState.allEventsFutureDays,
            futureLimit = mainState.allEventsFutureLimit
        )
    }

    AllEventsScreen(
        state = connection.state,
        uiSize = uiSize,
        extraBottomPadding = extraBottomPadding,
        hapticEnabled = hapticEnabled,
        onAction = { action ->
            when (action) {
                AllEventsUiAction.LoadMoreFuture -> viewModel.loadMoreFutureAllEvents()
                is AllEventsUiAction.RevealItem -> viewModel.onRevealItem(action.itemKey)
                AllEventsUiAction.CollapseItem -> viewModel.onRevealItem(null)
                is AllEventsUiAction.DeleteItem -> {
                    connection.itemsByKey[action.itemKey]?.eventId?.let { eventId ->
                        viewModel.deleteEvent(eventId)
                    }
                }

                is AllEventsUiAction.EditItem -> {
                    connection.itemsByKey[action.itemKey]?.let(onEditItem)
                }

                is AllEventsUiAction.RequestDeleteItem -> {
                    connection.itemsByKey[action.itemKey]?.let(onRequestDeleteItem)
                }

                is AllEventsUiAction.ArchiveItem -> {
                    connection.itemsByKey[action.itemKey]?.let { item ->
                        viewModel.archiveItem(item.action)
                    }
                }
            }
        }
    )
}

internal fun buildAllEventsConnection(
    items: List<ScheduleDisplayItem>,
    searchQuery: String,
    today: LocalDate,
    now: LocalDateTime,
    reverseOrderEnabled: Boolean,
    revealedItemKey: String?,
    timeRefreshToken: Long,
    futureDays: Int,
    futureLimit: LocalDate
): AllEventsConnection {
    val sortedItems = items
        .distinctBy { it.stableKey }
        .filter { item ->
            searchQuery.isBlank() ||
                item.title.contains(searchQuery, ignoreCase = true) ||
                item.description.contains(searchQuery, ignoreCase = true) ||
                item.location.contains(searchQuery, ignoreCase = true)
        }
        .sortedWith { first, second ->
            compareAllEventsItems(first, second, today, now, reverseOrderEnabled)
        }
    val groups = sortedItems
        .groupBy { it.startDate }
        .map { (date, dateItems) -> AllEventsDateGroupUiModel(date, dateItems) }

    return AllEventsConnection(
        state = AllEventsUiState(
            groups = groups,
            searchQuery = searchQuery,
            today = today,
            revealedItemKey = revealedItemKey,
            timeRefreshToken = timeRefreshToken,
            futureDays = futureDays,
            futureLimit = futureLimit
        ),
        itemsByKey = sortedItems.associateBy { it.stableKey }
    )
}

private fun compareAllEventsItems(
    first: ScheduleDisplayItem,
    second: ScheduleDisplayItem,
    today: LocalDate,
    now: LocalDateTime,
    reverseOrderEnabled: Boolean
): Int {
    val firstExpired = isExpired(first, now)
    val secondExpired = isExpired(second, now)
    if (firstExpired != secondExpired) return if (firstExpired) 1 else -1

    val dateComparison = allEventsDateKey(first, firstExpired, today)
        .compareTo(allEventsDateKey(second, secondExpired, today))
    if (dateComparison != 0) return dateComparison

    return if (reverseOrderEnabled) {
        second.startTime.compareTo(first.startTime)
    } else {
        first.startTime.compareTo(second.startTime)
    }
}

private fun isExpired(item: ScheduleDisplayItem, now: LocalDateTime): Boolean = try {
    LocalDateTime.of(item.endDate, item.endLocalTime).isBefore(now)
} catch (_: Exception) {
    false
}

private fun allEventsDateKey(
    item: ScheduleDisplayItem,
    expired: Boolean,
    today: LocalDate
): Long {
    val started = !item.startDate.isAfter(today)
    return when {
        expired -> -item.endDate.toEpochDay()
        started -> item.endDate.toEpochDay()
        else -> -item.startDate.toEpochDay()
    }
}
