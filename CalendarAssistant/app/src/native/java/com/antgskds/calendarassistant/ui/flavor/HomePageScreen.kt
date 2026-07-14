package com.antgskds.calendarassistant.ui.flavor

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.ui.contract.HomePageUiAction
import com.antgskds.calendarassistant.ui.contract.HomePageUiState
import com.antgskds.calendarassistant.ui.page_display.MaterialHomePage

@Composable
fun HomePageScreen(
    state: HomePageUiState,
    onAction: (HomePageUiAction) -> Unit,
    scheduleContent: @Composable () -> Unit,
    allEventsContent: @Composable (String, Dp) -> Unit,
    noteListContent: @Composable (String, Dp) -> Unit,
    quickMemoContent: @Composable (String, Dp) -> Unit,
    currentPageKey: String,
    uiSize: Int,
    pickupTimestamp: Long,
    openCourseRequestId: Long,
    courseFeatureEnabled: Boolean,
    isActionExpanded: Boolean,
    onActionExpandedChange: (Boolean) -> Unit,
    searchRequestId: Int,
    imageRequestId: Int,
    isSidebarOpen: Boolean,
    onPageChange: (String) -> Unit,
    onAddEventClick: () -> Unit,
    onEditItem: (ScheduleDisplayItem) -> Unit,
    onRequestDeleteItem: (ScheduleDisplayItem) -> Unit,
    onCreateNote: () -> Unit,
    onRequestClearQuickMemos: () -> Unit,
    quickMemoCount: Int,
    onScheduleExpandedChange: (Boolean) -> Unit,
    onScheduleProgressChange: (Float) -> Unit,
    onScheduleOffsetChange: (Float) -> Unit,
    onOpenWeatherDetail: () -> Unit,
) {
    MaterialHomePage(
        state = state,
        onAction = onAction,
        scheduleContent = scheduleContent,
        allEventsContent = allEventsContent,
        noteListContent = noteListContent,
        quickMemoContent = quickMemoContent,
        currentPageKey = currentPageKey,
        uiSize = uiSize,
        pickupTimestamp = pickupTimestamp,
        openCourseRequestId = openCourseRequestId,
        courseFeatureEnabled = courseFeatureEnabled,
        isActionExpanded = isActionExpanded,
        onActionExpandedChange = onActionExpandedChange,
        searchRequestId = searchRequestId,
        imageRequestId = imageRequestId,
        isSidebarOpen = isSidebarOpen,
        onPageChange = onPageChange,
        onAddEventClick = onAddEventClick,
        onEditItem = onEditItem,
        onRequestDeleteItem = onRequestDeleteItem,
        onCreateNote = onCreateNote,
        onRequestClearQuickMemos = onRequestClearQuickMemos,
        quickMemoCount = quickMemoCount,
        onScheduleExpandedChange = onScheduleExpandedChange,
        onScheduleProgressChange = onScheduleProgressChange,
        onScheduleOffsetChange = onScheduleOffsetChange,
        onOpenWeatherDetail = onOpenWeatherDetail,
    )
}
