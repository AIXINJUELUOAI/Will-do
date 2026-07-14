package com.antgskds.calendarassistant.feature.update.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.feature.update.ui.contract.AppUpdateScreenState
import com.antgskds.calendarassistant.feature.update.ui.contract.AppUpdateUiAction
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialAppUpdateScreen

@Composable
fun AppUpdateScreen(state: AppUpdateScreenState, uiSize: Int = 2, onAction: (AppUpdateUiAction) -> Unit) {
    MaterialAppUpdateScreen(state = state, uiSize = uiSize, onAction = onAction)
}
