package com.antgskds.calendarassistant.miui.page_display

import androidx.compose.runtime.Composable
import com.antgskds.calendarassistant.data.model.UiStyle
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsDetailScreen(
    destinationStr: String,
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    onExitSettings: () -> Unit,
    onLogout: () -> Unit,
    uiSize: Int
) {
    MiuiPlaceholderScreen(
        title = "MIUI 设置页预留中",
        message = "已保留设置路由入口：$destinationStr。后续会在 miui 目录内单独实现。",
        actionText = "返回",
        onAction = onExitSettings,
        secondaryActionText = "切回原版界面",
        onSecondaryAction = { settingsViewModel.updateUiStyle(UiStyle.MATERIAL3.name) }
    )
}
