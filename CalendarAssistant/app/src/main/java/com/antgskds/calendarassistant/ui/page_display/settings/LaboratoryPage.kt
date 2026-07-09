package com.antgskds.calendarassistant.ui.page_display.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import com.antgskds.calendarassistant.core.quickmemo.asr.QuickMemoAsrModelStatus
import com.antgskds.calendarassistant.core.quickmemo.asr.QuickMemoAsrModelStore
import com.antgskds.calendarassistant.core.util.PrivilegeManager
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.QuickMemoRecordingDisplayMode
import com.antgskds.calendarassistant.platform.clipboard.ClipboardCodeMonitorService
import com.antgskds.calendarassistant.ui.components.AppCard
import com.antgskds.calendarassistant.ui.haptic.LocalAppHapticsEnabled
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LaboratoryPage(
    uiSize: Int = 2,
    settingsViewModel: SettingsViewModel? = null,
    mainViewModel: MainViewModel? = null,
    onNavigateToDeveloper: () -> Unit = {}
) {
    val settings by settingsViewModel?.settings?.collectAsState() ?: remember { mutableStateOf(null) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var asrModelStatus by remember { mutableStateOf(QuickMemoAsrModelStore.status(context)) }

    fun refreshAsrModelStatus() {
        asrModelStatus = QuickMemoAsrModelStore.status(context)
    }

    val asrModelImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                QuickMemoAsrModelStore.importModelFile(context, uri)
            }
            refreshAsrModelStatus()
            val message = result.fold(
                onSuccess = { fileName -> "已导入 $fileName" },
                onFailure = { error -> error.message ?: "模型导入失败" }
            )
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

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
                text = "随口记",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            LaboratoryQuickMemoCard(
                settings = settings!!,
                asrModelStatus = asrModelStatus,
                onVoiceInputEnabledChange = { enabled ->
                    settingsViewModel?.updatePreference(voiceInputEnabled = enabled)
                },
                onFloatingLongPressChange = { enabled ->
                    settingsViewModel?.updatePreference(floatingVoiceLongPressEnabled = enabled)
                },
                onRecordingDisplayModeChange = { mode ->
                    settingsViewModel?.updatePreference(quickMemoRecordingDisplayMode = mode)
                },
                onTextAutoPinChange = { enabled ->
                    settingsViewModel?.updatePreference(floatingTextQuickMemoAutoPinEnabled = enabled)
                },
                onVoiceAutoPinChange = { enabled ->
                    settingsViewModel?.updatePreference(voiceQuickMemoAutoPinEnabled = enabled)
                },
                onImportAsrModel = { asrModelImportLauncher.launch(arrayOf("*/*")) }
            )

            Text(
                text = "实验功能",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            LaboratorySwitchCard(
                title = "码类事件使用当前时间",
                subtitle = "开启后取件码、取餐码、取票码、寄件码会忽略 AI 返回时间，入库时改为当前时间",
                checked = settings!!.forceInstantCodeTimeToNow,
                onCheckedChange = { enabled ->
                    settingsViewModel?.updatePreference(forceInstantCodeTimeToNow = enabled)
                }
            )

            LaboratorySwitchCard(
                title = "剪贴板码类识别（实验）",
                subtitle = "识别剪贴板中的取件码、取餐码、取票码、寄件码；有 Shizuku/Root 时后台自动入库，否则打开软件时确认入库",
                checked = settings!!.clipboardCodeRecognitionEnabled,
                onCheckedChange = { enabled ->
                    settingsViewModel?.updatePreference(clipboardCodeRecognitionEnabled = enabled)
                    if (enabled) {
                        PrivilegeManager.refreshPrivilege()
                        val message = if (PrivilegeManager.hasPrivilege) {
                            "已启用完整后台识别，识别到码类内容将自动创建日程"
                        } else {
                            "未获取 Shizuku/Root 权限，仅打开软件时识别，并向你确认是否入库"
                        }
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                        ClipboardCodeMonitorService.startIfNeeded(context)
                    } else {
                        ClipboardCodeMonitorService.stop(context)
                    }
                }
            )

            LaboratorySwitchCard(
                title = "预测性返回手势",
                subtitle = "侧滑返回时页面支持跟手动画效果",
                checked = settings!!.predictiveBackEnabled,
                onCheckedChange = { enabled ->
                    settingsViewModel?.updatePreference(predictiveBackEnabled = enabled)
                }
            )

            if (settings!!.developerOptionsUnlocked) {
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
                            onClick = onNavigateToDeveloper,
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
private fun LaboratoryQuickMemoCard(
    settings: MySettings,
    asrModelStatus: QuickMemoAsrModelStatus,
    onVoiceInputEnabledChange: (Boolean) -> Unit,
    onFloatingLongPressChange: (Boolean) -> Unit,
    onRecordingDisplayModeChange: (Int) -> Unit,
    onTextAutoPinChange: (Boolean) -> Unit,
    onVoiceAutoPinChange: (Boolean) -> Unit,
    onImportAsrModel: () -> Unit
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            LaboratorySwitchRow(
                title = "随口记",
                subtitle = "总开关。关闭后长按音量+和悬浮窗入口都不能启动随口记录音",
                checked = settings.voiceInputEnabled,
                onCheckedChange = onVoiceInputEnabledChange
            )

            AnimatedVisibility(
                visible = settings.voiceInputEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LaboratoryDivider()
                    LaboratorySwitchRow(
                        title = "悬浮窗长按随口记",
                        subtitle = "控制悬浮窗已呼出后，再次长按音量+是否进入随口记录音",
                        checked = settings.floatingVoiceLongPressEnabled,
                        onCheckedChange = onFloatingLongPressChange
                    )
                    LaboratoryDivider()
                    LaboratoryTwoOptionRow(
                        title = "录音展示",
                        subtitle = if (settings.quickMemoRecordingDisplayMode == QuickMemoRecordingDisplayMode.FLOATING_WINDOW) {
                            "所有入口录音时使用悬浮窗"
                        } else {
                            "所有入口录音时使用实况通知"
                        },
                        selectedValue = QuickMemoRecordingDisplayMode.normalize(settings.quickMemoRecordingDisplayMode),
                        firstValue = QuickMemoRecordingDisplayMode.LIVE_CAPSULE,
                        firstLabel = "实况通知",
                        secondValue = QuickMemoRecordingDisplayMode.FLOATING_WINDOW,
                        secondLabel = "悬浮窗",
                        onValueSelected = onRecordingDisplayModeChange
                    )
                    LaboratoryDivider()
                    LaboratorySwitchRow(
                        title = "悬浮窗文本随口记同步挂起",
                        subtitle = "悬浮窗随口记模式保存文本后，同步挂到实况通知；需开启实况通知",
                        checked = settings.floatingTextQuickMemoAutoPinEnabled,
                        onCheckedChange = onTextAutoPinChange
                    )
                    LaboratoryDivider()
                    LaboratorySwitchRow(
                        title = "语音随口记同步挂起",
                        subtitle = "语音随口记转写完成后，自动挂到实况通知；需开启实况通知",
                        checked = settings.voiceQuickMemoAutoPinEnabled,
                        onCheckedChange = onVoiceAutoPinChange
                    )
                    LaboratoryDivider()
                    LaboratoryActionRow(
                        title = "语音转写模型",
                        subtitle = formatQuickMemoAsrModelStatus(asrModelStatus),
                        buttonText = if (asrModelStatus.ready) "更换模型" else "导入模型",
                        onClick = onImportAsrModel
                    )
                }
            }
        }
    }
}

@Composable
private fun LaboratoryDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
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
        Switch(
            checked = checked,
            onCheckedChange = { haptics.selection(); onCheckedChange(it) }
        )
    }
}

@Composable
private fun LaboratoryTwoOptionRow(
    title: String,
    subtitle: String,
    selectedValue: Int,
    firstValue: Int,
    firstLabel: String,
    secondValue: Int,
    secondLabel: String,
    onValueSelected: (Int) -> Unit
) {
    val haptics = rememberAppHaptics()
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (selectedValue == firstValue) {
                Button(onClick = { haptics.selection(); onValueSelected(firstValue) }) {
                    Text(firstLabel)
                }
            } else {
                OutlinedButton(onClick = { haptics.selection(); onValueSelected(firstValue) }) {
                    Text(firstLabel)
                }
            }
            if (selectedValue == secondValue) {
                Button(onClick = { haptics.selection(); onValueSelected(secondValue) }) {
                    Text(secondLabel)
                }
            } else {
                OutlinedButton(onClick = { haptics.selection(); onValueSelected(secondValue) }) {
                    Text(secondLabel)
                }
            }
        }
    }
}

private fun formatQuickMemoAsrModelStatus(status: QuickMemoAsrModelStatus): String {
    return when {
        status.ready -> "已导入本地模型，可离线转写"
        !status.modelReady && !status.tokensReady -> "未导入模型，请依次导入 model.int8.onnx 和 tokens.txt"
        !status.modelReady -> "缺少 model.int8.onnx 或 model.onnx"
        else -> "缺少 tokens.txt"
    }
}

@Composable
private fun LaboratoryActionRow(
    title: String,
    subtitle: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
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
        Button(onClick = onClick) {
            Text(buttonText)
        }
    }
}

@Composable
private fun LaboratoryActionCard(
    title: String,
    subtitle: String,
    buttonText: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    AppCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        LaboratoryActionRow(
            title = title,
            subtitle = subtitle,
            buttonText = buttonText,
            onClick = onClick
        )
    }
}

private const val DEVELOPER_OPTION_HIDE_DELAY_MS = 5 * 60 * 1000L
