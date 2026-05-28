package com.antgskds.calendarassistant.ui.page_display.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.core.developer.DeveloperTestDataFactory
import com.antgskds.calendarassistant.core.developer.DeveloperTestDataFactory.TestEventType
import com.antgskds.calendarassistant.core.util.PrivilegeManager
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.service.clipboard.ClipboardCodeMonitorService
import com.antgskds.calendarassistant.ui.haptic.LocalAppHapticsEnabled
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LaboratoryPage(
    uiSize: Int = 2,
    settingsViewModel: SettingsViewModel? = null,
    mainViewModel: MainViewModel? = null
) {
    val settings by settingsViewModel?.settings?.collectAsState() ?: remember { mutableStateOf(null) }
    val scrollState = rememberScrollState()
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

    androidx.compose.runtime.CompositionLocalProvider(LocalAppHapticsEnabled provides (settings?.hapticFeedbackEnabled ?: true)) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "实验功能",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )

        if (settings != null) {
            LaboratorySwitchCard(
                title = "便签功能",
                subtitle = "启用后增加便签并在悬浮窗中显示便签",
                checked = settings!!.noteEnabled,
                onCheckedChange = { enabled ->
                    settingsViewModel?.updatePreference(noteEnabled = enabled)
                }
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
                LaboratorySwitchCard(
                    title = "开发者选项",
                    subtitle = "打开后显示开发者测试工具。关闭超过 5 分钟后会自动隐藏，需要重新在关于页解锁。",
                    checked = settings!!.developerOptionsEnabled,
                    onCheckedChange = { enabled ->
                        settingsViewModel?.setDeveloperOptionsEnabled(enabled)
                    }
                )

                if (settings!!.developerOptionsEnabled) {
                    DeveloperOptionsCard(
                        onCreateType = { type ->
                            val count = createDeveloperTestEvents(
                                type = type,
                                settings = settings!!,
                                mainViewModel = mainViewModel
                            )
                            Toast.makeText(context, "已创建 $count 条 ${type.label} 测试数据", Toast.LENGTH_SHORT).show()
                        },
                        onCreateAll = {
                            val count = createAllDeveloperTestEvents(
                                settings = settings!!,
                                mainViewModel = mainViewModel
                            )
                            Toast.makeText(context, "已创建 $count 条开发者测试数据", Toast.LENGTH_SHORT).show()
                        },
                        onClearAll = {
                            val count = mainViewModel?.clearDeveloperTestEvents() ?: 0
                            val message = if (count > 0) {
                                "已清空 $count 条测试日程"
                            } else {
                                "没有可清空的测试日程"
                            }
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                        },
                        onRemoveDonationThanks = {
                            settingsViewModel?.updateHasDonated(false)
                            Toast.makeText(context, "已移除捐赠感谢状态", Toast.LENGTH_SHORT).show()
                        },
                        onMigrateLegacyLogs = {
                            settingsViewModel?.migrateLegacyLogs { results, path ->
                                val message = if (results.any { it.startsWith("已迁移") }) {
                                    "日志已整理到 $path"
                                } else {
                                    results.firstOrNull() ?: "没有发现旧日志"
                                }
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        },
                        onExportLogs = { minutes ->
                            settingsViewModel?.exportDiagnosticLogs(minutes) { result ->
                                val message = result.fold(
                                    onSuccess = { path -> "日志包已导出到 $path" },
                                    onFailure = { error -> "日志导出失败: ${error.message ?: "未知错误"}" }
                                )
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            }
                        }
                    )
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
    val haptics = rememberAppHaptics()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeveloperOptionsCard(
    onCreateType: (TestEventType) -> Unit,
    onCreateAll: () -> Unit,
    onClearAll: () -> Unit,
    onRemoveDonationThanks: () -> Unit,
    onMigrateLegacyLogs: () -> Unit,
    onExportLogs: (Int?) -> Unit
) {
    val haptics = rememberAppHaptics()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "测试事件创建",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "每次点击都会创建一条新的同类型测试数据，可重复点击生成多条。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { haptics.confirm(); onCreateAll() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("创建全部类型")
            }
            OutlinedButton(
                onClick = { haptics.warning(); onClearAll() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("清空所有测试日程")
            }
            OutlinedButton(
                onClick = { haptics.warning(); onRemoveDonationThanks() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("移除捐赠感谢")
            }
            OutlinedButton(
                onClick = { haptics.confirm(); onMigrateLegacyLogs() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("整理旧日志到 WillDo")
            }
            Text(
                text = "日志导出",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "日志可能包含识别文本、Prompt 和模型原始响应，请确认后再分享。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(5, 15, 30, 60).forEach { minutes ->
                    AssistChip(
                        onClick = { haptics.confirm(); onExportLogs(minutes) },
                        label = { Text("导出最近 ${minutes} 分钟日志") }
                    )
                }
                AssistChip(
                    onClick = { haptics.confirm(); onExportLogs(null) },
                    label = { Text("导出完整日志") }
                )
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DeveloperTestDataFactory.allTypes.forEach { type ->
                    AssistChip(
                        onClick = { haptics.confirm(); onCreateType(type) },
                        label = { Text(type.label) }
                    )
                }
            }
        }
    }
}

private fun createDeveloperTestEvents(
    type: TestEventType,
    settings: MySettings,
    mainViewModel: MainViewModel?
): Int {
    val bundle = DeveloperTestDataFactory.build(
        type = type,
        sequence = nextDeveloperEventSequence(),
        settings = settings
    )
    createDeveloperTestBundle(bundle, mainViewModel)
    return bundle.patches.size + bundle.events.size
}

private fun createAllDeveloperTestEvents(
    settings: MySettings,
    mainViewModel: MainViewModel?
): Int {
    val bundle = DeveloperTestDataFactory.buildAll(
        sequence = nextDeveloperEventSequence(),
        settings = settings
    )
    createDeveloperTestBundle(bundle, mainViewModel)
    return bundle.patches.size + bundle.events.size
}

private fun createDeveloperTestBundle(
    bundle: DeveloperTestDataFactory.TestEventBundle,
    mainViewModel: MainViewModel?
) {
    bundle.patches.forEach { patch -> mainViewModel?.addEventFromPatch(patch) }
    bundle.events.forEach { event -> mainViewModel?.addEvent(event) }
}

private fun nextDeveloperEventSequence(): Int {
    return (System.currentTimeMillis() % 100000).toInt()
}

private const val DEVELOPER_OPTION_HIDE_DELAY_MS = 5 * 60 * 1000L
