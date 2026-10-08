package com.antgskds.calendarassistant.feature.home.ui.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color
import com.antgskds.calendarassistant.shared.ui.material.component.AppMenuItem
import com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.feature.home.ui.contract.HomePageUiAction
import com.antgskds.calendarassistant.feature.home.ui.contract.HomePageUiState
import com.antgskds.calendarassistant.feature.home.ui.render.material.MaterialHomePage
import com.antgskds.calendarassistant.app.ui.navigation.SettingsDestination

@Composable
fun HomePageScreen(
    state: HomePageUiState,
    onAction: (HomePageUiAction) -> Unit,
    scheduleContent: @Composable () -> Unit,
    allEventsContent: @Composable (String, Dp) -> Unit,
    quickMemoContent: @Composable (String, Dp) -> Unit,
    currentPageKey: String,
    pageOrder: List<String>,
    uiSize: Int,
    pickupTimestamp: Long,
    openCourseRequestId: Long,
    courseFeatureEnabled: Boolean,
    isActionExpanded: Boolean,
    onActionExpandedChange: (Boolean) -> Unit,
    searchRequestId: Int,
    imageRequestId: Int,
    onSearchRequestHandled: (Int) -> Unit,
    onImageRequestHandled: (Int) -> Unit,
    onRememberCalendarViewMode: (String) -> Unit,
    isSidebarOpen: Boolean,
    isWideNavigation: Boolean,
    isTwoPane: Boolean,
    onPageChange: (String) -> Unit,
    onAddEventClick: () -> Unit,
    onAddCourseClick: () -> Unit = {},
    onEditItem: (ScheduleDisplayItem) -> Unit,
    onRequestDeleteItem: (ScheduleDisplayItem) -> Unit,
    onRequestQuickMemoFolders: () -> Unit,
    quickMemoFolderMenu: @Composable (Color, Color, Color, List<AppMenuItem>) -> Unit,
    quickMemoCount: Int,
    onScheduleExpandedChange: (Boolean) -> Unit,
    onScheduleProgressChange: (Float) -> Unit,
    onScheduleOffsetChange: (Float) -> Unit,
    onOpenWeatherDetail: () -> Unit,
    onOpenAccounting: (java.time.LocalDate, Boolean) -> Unit,
    onNavigateToSettings: (SettingsDestination) -> Unit,
) {
    MaterialHomePage(
        state = state,
        onAction = onAction,
        scheduleContent = scheduleContent,
        allEventsContent = allEventsContent,
        quickMemoContent = quickMemoContent,
        currentPageKey = currentPageKey,
        pageOrder = pageOrder,
        uiSize = uiSize,
        pickupTimestamp = pickupTimestamp,
        openCourseRequestId = openCourseRequestId,
        courseFeatureEnabled = courseFeatureEnabled,
        isActionExpanded = isActionExpanded,
        onActionExpandedChange = onActionExpandedChange,
        searchRequestId = searchRequestId,
        imageRequestId = imageRequestId,
        onSearchRequestHandled = onSearchRequestHandled,
        onImageRequestHandled = onImageRequestHandled,
        onRememberCalendarViewMode = onRememberCalendarViewMode,
        isSidebarOpen = isSidebarOpen,
        isWideNavigation = isWideNavigation,
        isTwoPane = isTwoPane,
        onPageChange = onPageChange,
        onAddEventClick = onAddEventClick,
        onAddCourseClick = onAddCourseClick,
        onEditItem = onEditItem,
        onRequestDeleteItem = onRequestDeleteItem,
        onRequestQuickMemoFolders = onRequestQuickMemoFolders,
        quickMemoFolderMenu = quickMemoFolderMenu,
        quickMemoCount = quickMemoCount,
        onScheduleExpandedChange = onScheduleExpandedChange,
        onScheduleProgressChange = onScheduleProgressChange,
        onScheduleOffsetChange = onScheduleOffsetChange,
        onOpenWeatherDetail = onOpenWeatherDetail,
        onOpenAccounting = onOpenAccounting,
    )
}
