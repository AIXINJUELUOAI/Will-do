package com.antgskds.calendarassistant.miui.page_display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.data.model.UiStyle
import com.antgskds.calendarassistant.miui.components.SettingsDestination
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel

@Composable
fun HomeScreen(
    mainViewModel: MainViewModel,
    settingsViewModel: SettingsViewModel,
    pickupTimestamp: Long = 0L,
    onNavigateToSettings: (SettingsDestination) -> Unit = {}
) {
    MiuiPlaceholderScreen(
        title = "MIUI 界面预留中",
        message = "当前只保留 MIUI 入口和主题切换接口，具体页面会从空白状态重新实现。",
        actionText = "前往主题设置",
        onAction = { onNavigateToSettings(SettingsDestination.Theme) },
        secondaryActionText = "切回原版界面",
        onSecondaryAction = { settingsViewModel.updateUiStyle(UiStyle.MATERIAL3.name) }
    )
}

@Composable
internal fun MiuiPlaceholderScreen(
    title: String,
    message: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionText: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            if (actionText != null && onAction != null) {
                Spacer(Modifier.height(24.dp))
                Button(onClick = onAction) {
                    Text(actionText)
                }
            }
            if (secondaryActionText != null && onSecondaryAction != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onSecondaryAction) {
                    Text(secondaryActionText)
                }
            }
        }
    }
}
