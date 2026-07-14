package com.antgskds.calendarassistant.feature.backup.ui.render

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.feature.backup.ui.contract.BackupUiController
import com.antgskds.calendarassistant.ui.page_display.settings.MaterialBackupSettingsScreen

@Composable
fun BackupSettingsScreen(controller: BackupUiController, uiSize: Int = 2) {
    MaterialBackupSettingsScreen(controller, uiSize)
}
