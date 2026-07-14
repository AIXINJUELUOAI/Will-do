package com.antgskds.calendarassistant.ui.contract

import com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem
import java.time.LocalDate

data class AllEventsUiState(
    val groups: List<AllEventsDateGroupUiModel> = emptyList(),
    val searchQuery: String = "",
    val today: LocalDate = LocalDate.now(),
    val revealedItemKey: String? = null,
    val timeRefreshToken: Long = 0L,
    val futureDays: Int = 0,
    val futureLimit: LocalDate = LocalDate.now()
)

data class AllEventsDateGroupUiModel(
    val date: LocalDate,
    val items: List<ScheduleDisplayItem>
)

sealed interface AllEventsUiAction {
    data object LoadMoreFuture : AllEventsUiAction
    data class RevealItem(val itemKey: String) : AllEventsUiAction
    data object CollapseItem : AllEventsUiAction
    data class DeleteItem(val itemKey: String) : AllEventsUiAction
    data class EditItem(val itemKey: String) : AllEventsUiAction
    data class RequestDeleteItem(val itemKey: String) : AllEventsUiAction
    data class ArchiveItem(val itemKey: String) : AllEventsUiAction
}
