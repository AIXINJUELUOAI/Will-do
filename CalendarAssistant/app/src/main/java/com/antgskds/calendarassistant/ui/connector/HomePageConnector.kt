package com.antgskds.calendarassistant.ui.connector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.antgskds.calendarassistant.core.course.TimeTableLayoutUtils
import com.antgskds.calendarassistant.core.note.NoteEntity
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoEntity
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.ui.contract.HomePageUiAction
import com.antgskds.calendarassistant.ui.contract.HomePageUiState
import com.antgskds.calendarassistant.ui.flavor.HomePageScreen
import com.antgskds.calendarassistant.ui.flavor.ScheduleView
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel

@Composable
fun HomePageRoute(
    viewModel: MainViewModel,
    currentPageKey: String,
    uiSize: Int = 2,
    pickupTimestamp: Long = 0L,
    openCourseRequestId: Long = 0L,
    courseFeatureEnabled: Boolean = true,
    isActionExpanded: Boolean = false,
    onActionExpandedChange: (Boolean) -> Unit = {},
    searchRequestId: Int = 0,
    imageRequestId: Int = 0,
    isSidebarOpen: Boolean = false,
    onPageChange: (String) -> Unit = {},
    onAddEventClick: () -> Unit = {},
    onEditItem: (ScheduleDisplayItem) -> Unit = {},
    onRequestDeleteItem: (ScheduleDisplayItem) -> Unit = {},
    onEditNote: (NoteEntity) -> Unit = {},
    onCreateNote: () -> Unit = {},
    onRequestDeleteNote: (NoteEntity) -> Unit = {},
    onRequestDeleteQuickMemo: (QuickMemoEntity) -> Unit = {},
    onRequestClearQuickMemos: () -> Unit = {},
    quickMemoCount: Int = 0,
    onOpenQuickMemoDetail: (Long) -> Unit = {},
    onScheduleExpandedChange: (Boolean) -> Unit = {},
    onScheduleProgressChange: (Float) -> Unit = {},
    onScheduleOffsetChange: (Float) -> Unit = {},
    onOpenWeatherDetail: () -> Unit = {},
) {
    val mainState by viewModel.uiState.collectAsState()
    val state = remember(mainState) {
        HomePageUiState(
            selectedDate = mainState.selectedDate,
            today = mainState.today,
            timeRefreshToken = mainState.timeRefreshToken,
            revealedItemKey = mainState.revealedItemKey,
            courseScheduleItems = mainState.courseScheduleItems,
            currentDateEvents = mainState.currentDateEvents,
            tomorrowEvents = mainState.tomorrowEvents,
            settings = mainState.settings,
            weatherData = mainState.weatherData,
        )
    }

    HomePageScreen(
        state = state,
        onAction = { action ->
            when (action) {
                is HomePageUiAction.SelectDate -> viewModel.updateSelectedDate(action.date)
                is HomePageUiAction.RevealItem -> viewModel.onRevealItem(action.itemKey)
                is HomePageUiAction.DeleteItem -> {
                    action.item.eventId?.let { eventId -> viewModel.deleteEvent(eventId) }
                }
                is HomePageUiAction.ArchiveItem -> viewModel.archiveItem(action.item.action)
            }
        },
        scheduleContent = {
            val maxNodes = remember(state.settings.timeTableJson) {
                TimeTableLayoutUtils.nodeCountFromJson(state.settings.timeTableJson)
            }
            ScheduleView(
                items = state.courseScheduleItems,
                semesterStartDateStr = state.settings.semesterStartDate,
                totalWeeks = state.settings.totalWeeks,
                maxNodes = maxNodes,
                selectedDate = state.selectedDate,
                onCourseClick = onEditItem,
            )
        },
        allEventsContent = { searchQuery, extraBottomPadding ->
            AllEventsRoute(
                viewModel = viewModel,
                onEditItem = onEditItem,
                uiSize = uiSize,
                searchQuery = searchQuery,
                extraBottomPadding = extraBottomPadding,
                onRequestDeleteItem = onRequestDeleteItem,
                hapticEnabled = state.settings.hapticFeedbackEnabled,
            )
        },
        noteListContent = { searchQuery, extraBottomPadding ->
            NoteListRoute(
                viewModel = viewModel,
                searchQuery = searchQuery,
                extraBottomPadding = extraBottomPadding,
                onEditNote = onEditNote,
                onRequestDeleteNote = onRequestDeleteNote,
                hapticEnabled = state.settings.hapticFeedbackEnabled,
            )
        },
        quickMemoContent = { searchQuery, extraBottomPadding ->
            QuickMemoPage(
                viewModel = viewModel,
                searchQuery = searchQuery,
                uiSize = uiSize,
                extraBottomPadding = extraBottomPadding,
                onOpenDetail = onOpenQuickMemoDetail,
                onPendingDeleteChange = { memo -> memo?.let(onRequestDeleteQuickMemo) },
                hapticEnabled = state.settings.hapticFeedbackEnabled,
            )
        },
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
