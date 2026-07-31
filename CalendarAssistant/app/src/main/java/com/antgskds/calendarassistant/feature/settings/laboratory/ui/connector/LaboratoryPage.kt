package com.antgskds.calendarassistant.feature.settings.laboratory.ui.connector

import com.antgskds.calendarassistant.shared.ui.material.settings.*
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import com.antgskds.calendarassistant.shared.util.PrivilegeManager
import com.antgskds.calendarassistant.platform.clipboard.ClipboardCodeMonitorService
import com.antgskds.calendarassistant.shared.ui.material.component.AppCard
import com.antgskds.calendarassistant.shared.ui.edition.EditionSwitch
import com.antgskds.calendarassistant.shared.ui.interaction.LocalAppHapticsEnabled
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.app.ui.state.MainViewModel
import com.antgskds.calendarassistant.app.ui.state.SettingsViewModel
import com.antgskds.calendarassistant.feature.settings.laboratory.ui.contract.LaboratoryUiAction
import com.antgskds.calendarassistant.feature.settings.laboratory.ui.contract.LaboratoryUiState
import kotlinx.coroutines.delay

@Composable
fun LaboratoryPage(
    uiSize: Int = 2,
    settingsViewModel: SettingsViewModel? = null,
    mainViewModel: MainViewModel? = null,
    onNavigateToDeveloper: () -> Unit = {}
) {
    val settings by settingsViewModel?.settings?.collectAsState() ?: remember { mutableStateOf(null) }
    val context = LocalContext.current

    LaunchedEffect(settings?.developerOptionsUnlocked, settings?.developerOptionsEnabled, settings?.developerOptionsDisabledAtMillis) {
        val current = settings ?: return@LaunchedEffect
        if (
            current.developerOptionsUnlocked &&
            !current.developerOptionsEnabled &&
            current.developerOptionsDisabledAtMillis > 0L
        ) {
            val remaining = DEVELOPER_OPTION_HIDE_DELAY_MS - (System.currentTimeMillis() - current.developerOptionsDisabledAtMillis)
            if (remaining > 0L) {
                delay(remaining)
            }
            val latest = settingsViewModel?.settings?.value ?: return@LaunchedEffect
            if (
                latest.developerOptionsUnlocked &&
                !latest.developerOptionsEnabled &&
                latest.developerOptionsDisabledAtMillis > 0L &&
                System.currentTimeMillis() - latest.developerOptionsDisabledAtMillis >= DEVELOPER_OPTION_HIDE_DELAY_MS
            ) {
                settingsViewModel.expireDeveloperOptionsUnlock()
            }
        }
    }

    MaterialLaboratoryScreen(
        state = LaboratoryUiState(settings = settings),
        uiSize = uiSize,
        onAction = { action ->
            when (action) {
                is LaboratoryUiAction.SetBraceletMode -> settingsViewModel?.updatePreference(braceletModeEnabled = action.enabled)
                is LaboratoryUiAction.SetForceInstantCodeTime -> settingsViewModel?.updatePreference(forceInstantCodeTimeToNow = action.enabled)
                is LaboratoryUiAction.SetPredictiveBack -> settingsViewModel?.updatePreference(predictiveBackEnabled = action.enabled)
                is LaboratoryUiAction.SetClipboardRecognition -> {
                    settingsViewModel?.updatePreference(clipboardCodeRecognitionEnabled = action.enabled)
                    if (action.enabled) {
                        PrivilegeManager.refreshPrivilege()
                        val message = if (PrivilegeManager.hasPrivilege) {
                            "已启用完整后台识别，识别到取件类内容将自动创建日程"
                        } else {
                            "未获取 Shizuku/Root 权限，仅打开软件时识别，并向你确认是否入库"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        ClipboardCodeMonitorService.startIfNeeded(context)
                    } else {
                        ClipboardCodeMonitorService.stop(context)
                    }
                }
                LaboratoryUiAction.OpenDeveloper -> onNavigateToDeveloper()
            }
        }
    )
}

@Composable
fun MaterialLaboratoryScreen(
    state: LaboratoryUiState,
    uiSize: Int = 2,
    onAction: (LaboratoryUiAction) -> Unit
) {
    val settings = state.settings
    val scrollState = rememberScrollState()

    androidx.compose.runtime.CompositionLocalProvider(LocalAppHapticsEnabled provides (settings?.hapticFeedbackEnabled ?: true)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (settings != null) {
            Text(
                text = "实验功能",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            LaboratorySwitchCard(
                title = "取件类事件使用当前时间",
                subtitle = "开启后取件码、取餐码、取票码、寄件码会忽略 AI 返回时间，入库时改为当前时间",
                checked = settings.forceInstantCodeTimeToNow,
                onCheckedChange = { enabled ->
                    onAction(LaboratoryUiAction.SetForceInstantCodeTime(enabled))
                }
            )

            LaboratorySwitchCard(
                title = "剪贴板取件类识别（Beta）",
                subtitle = "识别剪贴板中的取件码、取餐码、取票码和寄件码",
                checked = settings.clipboardCodeRecognitionEnabled,
                onCheckedChange = { enabled ->
                    onAction(LaboratoryUiAction.SetClipboardRecognition(enabled))
                }
            )

            LaboratorySwitchCard(
                title = "预测性返回手势",
                subtitle = "侧滑返回时页面支持跟手动画效果",
                checked = settings.predictiveBackEnabled,
                onCheckedChange = { enabled ->
                    onAction(LaboratoryUiAction.SetPredictiveBack(enabled))
                }
            )

            LaboratorySwitchCard(
                title = "手环模式",
                subtitle = "开启后，将同步发送一条普通通知以同步到手环",
                checked = settings.braceletModeEnabled,
                onCheckedChange = { enabled ->
                    onAction(LaboratoryUiAction.SetBraceletMode(enabled))
                }
            )

            if (settings.developerOptionsUnlocked) {
                Text(
                    text = "开发者",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                AppCard(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        ActionSettingItem(
                            title = "开发者页",
                            subtitle = "调试动作、注册台账、通知模板与日志导出",
                            value = "",
                            icon = Icons.Default.ChevronRight,
                            enabled = true,
                            onClick = { onAction(LaboratoryUiAction.OpenDeveloper) },
                            cardTitleStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            cardSubtitleStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                            cardValueStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
    }
}

@Composable
private fun LaboratorySwitchCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        LaboratorySwitchRow(
            title = title,
            subtitle = subtitle,
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun LaboratorySwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val haptics = rememberAppHaptics()
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        EditionSwitch(
            checked = checked,
            onCheckedChange = { haptics.selection(); onCheckedChange(it) }
        )
    }
}

private const val DEVELOPER_OPTION_HIDE_DELAY_MS = 5 * 60 * 1000L
