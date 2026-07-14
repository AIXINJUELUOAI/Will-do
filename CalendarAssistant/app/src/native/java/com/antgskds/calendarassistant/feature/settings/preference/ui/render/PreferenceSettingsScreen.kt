package com.antgskds.calendarassistant.feature.settings.preference.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.feature.settings.preference.ui.contract.PreferenceUiController
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialPreferenceSettingsScreen

@Composable
fun PreferenceSettingsScreen(controller: PreferenceUiController, uiSize: Int = 2, onNavigateToBottomBarEditor: () -> Unit, onNavigateToWidgetSettings: () -> Unit, onNavigateToScheduleColors: () -> Unit, onNavigateToSemesterConfig: () -> Unit, onNavigateToCourseManage: () -> Unit, onNavigateToTimeTableManage: () -> Unit) {
    MaterialPreferenceSettingsScreen(
        controller = controller,
        uiSize = uiSize,
        onNavigateToBottomBarEditor = onNavigateToBottomBarEditor,
        onNavigateToWidgetSettings = onNavigateToWidgetSettings,
        onNavigateToScheduleColors = onNavigateToScheduleColors,
        onNavigateToSemesterConfig = onNavigateToSemesterConfig,
        onNavigateToCourseManage = onNavigateToCourseManage,
        onNavigateToTimeTableManage = onNavigateToTimeTableManage
    )
}
