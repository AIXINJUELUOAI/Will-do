package com.antgskds.calendarassistant.ui.page_display.settings

import android.content.Context
import android.content.Intent
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.core.ai.RecognitionFailureDisplay
import com.antgskds.calendarassistant.core.developer.DeveloperTestDataFactory
import com.antgskds.calendarassistant.core.developer.DeveloperTestDataFactory.TestEventType
import com.antgskds.calendarassistant.core.query.DailySummaryPayload
import com.antgskds.calendarassistant.core.service.voice.LocalRecorderTestActivity
import com.antgskds.calendarassistant.core.util.PrivilegeManager
import com.antgskds.calendarassistant.core.weather.WeatherAlertIconMapper
import com.antgskds.calendarassistant.data.model.LiveNotificationTemplateMode
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.WeatherAlertData
import com.antgskds.calendarassistant.data.model.WeatherRiskAlert
import com.antgskds.calendarassistant.service.clipboard.ClipboardCodeMonitorService
import com.antgskds.calendarassistant.service.capsule.NetworkSpeedMonitor
import com.antgskds.calendarassistant.ui.haptic.LocalAppHapticsEnabled
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

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
    val app = context.applicationContext as? App
    var showNotificationTestSheet by remember { mutableStateOf(false) }

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

        LaboratoryActionCard(
            title = "本地录音测试",
            subtitle = "直接调用本地 QuickMemoAudioRecorder，验证前台页面里的 MediaRecorder/AudioRecord 是否能正常录音。",
            buttonText = "开始测试",
            onClick = {
                context.startActivity(Intent(context, LocalRecorderTestActivity::class.java))
            }
        )

        if (settings != null) {
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
                        weatherLocationStabilityRequiredHits = settings!!.weatherLocationStabilityRequiredHits,
                        liveNotificationTemplateMode = settings!!.liveNotificationTemplateMode,
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
                        onMigrateLegacyNotes = {
                            settingsViewModel?.migrateLegacyNotes { result ->
                                Toast.makeText(
                                    context,
                                    "旧便签扫描 ${result.scanned} 条，迁移 ${result.migrated} 条，跳过 ${result.skipped} 条",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        },
                        onCleanLegacyNotes = {
                            settingsViewModel?.cleanLegacyNotes { result ->
                                Toast.makeText(context, "已清理 ${result.cleaned} 条旧版便签 Event 数据", Toast.LENGTH_LONG).show()
                            }
                        },
                        onCleanDuplicateEvents = {
                            settingsViewModel?.cleanDuplicateEvents { result ->
                                val message = if (result.deleted > 0 || result.mergedBindings > 0) {
                                    "已扫描 ${result.scanned} 条，清理 ${result.deleted} 条重复日程，合并 ${result.mergedBindings} 个系统日历绑定"
                                } else {
                                    "已扫描 ${result.scanned} 条，没有发现完全重复日程"
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
                        },
                        onOpenNotificationTests = {
                            showNotificationTestSheet = true
                        },
                        onWeatherLocationStabilityRequiredHitsChange = { hits ->
                            settingsViewModel?.updatePreference(weatherLocationStabilityRequiredHits = hits)
                            Toast.makeText(context, "天气通知位置稳定阈值已设为 $hits 次", Toast.LENGTH_SHORT).show()
                        },
                        onLiveNotificationTemplateModeChange = { mode ->
                            settingsViewModel?.updatePreference(liveNotificationTemplateMode = mode)
                            app?.capsuleCenter?.forceRefresh()
                            Toast.makeText(context, "原生实况通知模板已设为 ${liveTemplateModeLabel(mode)}", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
        if (showNotificationTestSheet) {
            DeveloperNotificationTestSheet(
                app = app,
                settings = settings,
                onDismiss = { showNotificationTestSheet = false }
            )
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
    weatherLocationStabilityRequiredHits: Int,
    liveNotificationTemplateMode: String,
    onCreateType: (TestEventType) -> Unit,
    onCreateAll: () -> Unit,
    onClearAll: () -> Unit,
    onRemoveDonationThanks: () -> Unit,
    onMigrateLegacyLogs: () -> Unit,
    onMigrateLegacyNotes: () -> Unit,
    onCleanLegacyNotes: () -> Unit,
    onCleanDuplicateEvents: () -> Unit,
    onExportLogs: (Int?) -> Unit,
    onOpenNotificationTests: () -> Unit,
    onWeatherLocationStabilityRequiredHitsChange: (Int) -> Unit,
    onLiveNotificationTemplateModeChange: (String) -> Unit
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
            OutlinedButton(
                onClick = { haptics.confirm(); onMigrateLegacyNotes() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("手动迁移旧版便签")
            }
            OutlinedButton(
                onClick = { haptics.warning(); onCleanLegacyNotes() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("清理旧版便签 Event 数据")
            }
            OutlinedButton(
                onClick = { haptics.warning(); onCleanDuplicateEvents() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("清理重复日程")
            }
            Button(
                onClick = { haptics.confirm(); onOpenNotificationTests() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("通知/胶囊测试")
            }
            Text(
                text = "原生实况通知模板",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "控制原生实况通知使用完整多行内容还是两行精简内容；仅影响原生通知通道。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LiveNotificationTemplateMode.ALL.forEach { mode ->
                    val selected = LiveNotificationTemplateMode.normalize(liveNotificationTemplateMode) == mode
                    AssistChip(
                        onClick = { haptics.confirm(); onLiveNotificationTemplateModeChange(mode) },
                        label = { Text(if (selected) "${liveTemplateModeLabel(mode)} ✓" else liveTemplateModeLabel(mode)) }
                    )
                }
            }
            Text(
                text = "天气通知位置稳定阈值",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "自动定位连续命中同一位置达到阈值后才发送天气预警/风险通知；1 次表示关闭兜底，当前 ${weatherLocationStabilityRequiredHits.coerceIn(1, 3)} 次。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(1, 2, 3).forEach { hits ->
                    val label = when (hits) {
                        1 -> "1 次（关闭兜底）"
                        2 -> "2 次（默认）"
                        else -> "3 次（严格）"
                    }
                    AssistChip(
                        onClick = { haptics.confirm(); onWeatherLocationStabilityRequiredHitsChange(hits) },
                        label = { Text(if (weatherLocationStabilityRequiredHits.coerceIn(1, 3) == hits) "$label ✓" else label) }
                    )
                }
            }
            Text(
                text = "日志导出",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "导出本地日志、崩溃日志、识别日志和应用 logcat。日志可能包含识别文本、Prompt、模型响应和接口返回；有 Shizuku/Root 时 logcat 更完整。",
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
                        label = { Text("导出最近 ${minutes} 分钟诊断日志") }
                    )
                }
                AssistChip(
                    onClick = { haptics.confirm(); onExportLogs(null) },
                    label = { Text("导出完整诊断日志") }
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

private fun liveTemplateModeLabel(mode: String): String {
    return when (LiveNotificationTemplateMode.normalize(mode)) {
        LiveNotificationTemplateMode.FULL -> "完整"
        LiveNotificationTemplateMode.COMPACT -> "精简"
        else -> "自动"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DeveloperNotificationTestSheet(
    app: App?,
    settings: MySettings?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    val liveCapsuleEnabled = settings?.isLiveCapsuleEnabled == true

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "通知/胶囊测试",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "点击后会立即发送测试通知，便于检查状态栏、通知栏、实况胶囊和天气图标映射。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!notificationsEnabled) {
                Text(
                    text = "系统通知权限未开启，普通通知可能不会显示。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
            if (!liveCapsuleEnabled) {
                Text(
                    text = "实况胶囊通知开关未开启，实况测试不会显示；请先到偏好设置开启。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            DeveloperSheetSection(title = "普通通知") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeveloperTestChip("日程") {
                        haptics.confirm()
                        toastResult(context, sendDeveloperPlainNotification(context, app, developerScheduleNotificationSample()))
                    }
                    DeveloperTestChip("取件") {
                        haptics.confirm()
                        toastResult(context, sendDeveloperPlainNotification(context, app, developerPickupNotificationSample()))
                    }
                    DeveloperTestChip("课程") {
                        haptics.confirm()
                        toastResult(context, sendDeveloperPlainNotification(context, app, developerCourseNotificationSample()))
                    }
                }
            }

            DeveloperSheetSection(title = "天气普通通知") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    developerWeatherAlertSamples().forEach { sample ->
                        DeveloperTestChip(sample.label) {
                            haptics.confirm()
                            toastResult(context, sendDeveloperWeatherNotification(context, app, sample))
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            DeveloperSheetSection(title = "实况胶囊") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeveloperTestChip("OCR 进度") {
                        haptics.confirm()
                        toastResult(context, showDeveloperLiveOcrProgress(app, settings))
                    }
                    DeveloperTestChip("识别成功") {
                        haptics.confirm()
                        toastResult(context, showDeveloperRecognitionSuccess(app, settings, count = 1))
                    }
                    DeveloperTestChip("识别成功 x2") {
                        haptics.confirm()
                        toastResult(context, showDeveloperRecognitionSuccess(app, settings, count = 2))
                    }
                    DeveloperTestChip("识别失败") {
                        haptics.confirm()
                        toastResult(context, showDeveloperRecognitionFailure(app, settings))
                    }
                    DeveloperTestChip("模型加载") {
                        haptics.confirm()
                        toastResult(context, showDeveloperLiveModelLoading(app, settings))
                    }
                    DeveloperTestChip("网速") {
                        haptics.confirm()
                        toastResult(context, showDeveloperLiveNetworkSpeed(app, settings))
                    }
                }
            }

            DeveloperSheetSection(title = "每日提醒") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeveloperTestChip("今日提醒") {
                        haptics.confirm()
                        toastResult(context, showDeveloperDailySummaryNotification(context, app, settings, isMorning = true))
                    }
                    DeveloperTestChip("明日预告") {
                        haptics.confirm()
                        toastResult(context, showDeveloperDailySummaryNotification(context, app, settings, isMorning = false))
                    }
                }
            }

            DeveloperSheetSection(title = "天气实况胶囊") {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    developerWeatherAlertSamples().forEach { sample ->
                        DeveloperTestChip(sample.label) {
                            haptics.confirm()
                            toastResult(context, showDeveloperWeatherAlertCapsule(app, settings, sample))
                        }
                    }
                    developerWeatherRiskSamples().forEach { sample ->
                        DeveloperTestChip(sample.label) {
                            haptics.confirm()
                            toastResult(context, showDeveloperWeatherRiskCapsule(app, settings, sample))
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    haptics.warning()
                    toastResult(context, clearDeveloperLiveCapsules(app))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("清除测试胶囊")
            }

            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun DeveloperSheetSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
        content()
    }
}

@Composable
private fun DeveloperTestChip(
    label: String,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) }
    )
}

private data class DeveloperPlainNotificationSample(
    val key: String,
    val title: String,
    val content: String,
    val channelId: String,
    val smallIcon: Int
)

private data class DeveloperWeatherAlertSample(
    val key: String,
    val label: String,
    val eventName: String,
    val colorCode: String,
    val description: String,
    val instruction: String,
    val messageTypeCode: String = "alert"
)

private data class DeveloperWeatherRiskSample(
    val key: String,
    val label: String,
    val title: String,
    val level: String,
    val weatherText: String,
    val message: String
)

private fun developerScheduleNotificationSample(): DeveloperPlainNotificationSample {
    return DeveloperPlainNotificationSample(
        key = "schedule",
        title = "开发者测试日程",
        content = "15 分钟后开始：检查普通日程通知样式。",
        channelId = App.CHANNEL_ID_POPUP,
        smallIcon = R.drawable.ic_stat_event
    )
}

private fun developerPickupNotificationSample(): DeveloperPlainNotificationSample {
    return DeveloperPlainNotificationSample(
        key = "pickup",
        title = "开发者测试取件",
        content = "菜鸟驿站 3-2-101，取件码 8-2333。",
        channelId = App.CHANNEL_ID_POPUP,
        smallIcon = R.drawable.ic_stat_package
    )
}

private fun developerCourseNotificationSample(): DeveloperPlainNotificationSample {
    return DeveloperPlainNotificationSample(
        key = "course",
        title = "开发者测试课程",
        content = "高等数学即将开始，地点：教学楼 A203。",
        channelId = App.CHANNEL_ID_POPUP,
        smallIcon = R.drawable.ic_stat_course
    )
}

@Composable
private fun LaboratoryActionCard(
    title: String,
    subtitle: String,
    buttonText: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onClick) {
                Text(buttonText)
            }
        }
    }
}

private fun developerRecognitionEvents(count: Int): List<Event> {
    val now = LocalDateTime.now().withSecond(0).withNano(0)
    return (0 until count).map { index ->
        val start = now.plusMinutes(30L + index * 90L)
        val end = start.plusMinutes(45L)
        val startSeconds = start.atZone(ZoneId.systemDefault()).toEpochSecond()
        val endSeconds = end.atZone(ZoneId.systemDefault()).toEpochSecond()
        Event(
            id = -(System.currentTimeMillis() % 100_000L) - index,
            startTS = startSeconds,
            endTS = endSeconds,
            title = if (index == 0) "开发者测试会议" else "开发者测试复盘",
            location = if (index == 0) "会议室 A203" else "线上会议",
            description = if (index == 0) "确认识别结果胶囊的两行内容" else "验证多个日程各发一条结果通知"
        )
    }
}

private fun developerWeatherAlertSamples(): List<DeveloperWeatherAlertSample> {
    return listOf(
        DeveloperWeatherAlertSample(
            key = "heat",
            label = "高温",
            eventName = "高温",
            colorCode = "orange",
            description = "预计未来 24 小时最高气温将达到 38℃ 以上，请减少户外活动。",
            instruction = "注意防暑降温，避免长时间暴晒。"
        ),
        DeveloperWeatherAlertSample(
            key = "thunder",
            label = "雷暴",
            eventName = "雷电",
            colorCode = "yellow",
            description = "预计未来 6 小时有雷电活动，并可能伴有短时强降水。",
            instruction = "请远离高处、水域和金属设施。"
        ),
        DeveloperWeatherAlertSample(
            key = "rainstorm",
            label = "暴雨",
            eventName = "暴雨",
            colorCode = "red",
            description = "预计未来 3 小时降雨量将达 80 毫米以上，城市内涝风险较高。",
            instruction = "请避开低洼路段，注意交通安全。"
        ),
        DeveloperWeatherAlertSample(
            key = "wind",
            label = "大风",
            eventName = "大风",
            colorCode = "blue",
            description = "预计未来 12 小时阵风可达 8 级以上。",
            instruction = "请收好阳台物品，减少户外高空作业。"
        ),
        DeveloperWeatherAlertSample(
            key = "haze",
            label = "雾霾",
            eventName = "霾",
            colorCode = "yellow",
            description = "预计未来 12 小时有中度霾，能见度和空气质量下降。",
            instruction = "敏感人群请减少户外活动。"
        ),
        DeveloperWeatherAlertSample(
            key = "snow",
            label = "降雪",
            eventName = "暴雪",
            colorCode = "orange",
            description = "预计未来 12 小时降雪量将达 8 毫米以上，道路结冰风险较高。",
            instruction = "出行请注意防滑并预留通勤时间。"
        ),
        DeveloperWeatherAlertSample(
            key = "cold",
            label = "低温",
            eventName = "低温",
            colorCode = "blue",
            description = "预计未来 24 小时最低气温将降至 -3℃ 左右，清晨夜间体感寒冷。",
            instruction = "请注意添衣保暖，照顾老人儿童。"
        ),
        DeveloperWeatherAlertSample(
            key = "cold_wave_update",
            label = "寒潮更新",
            eventName = "寒潮",
            colorCode = "orange",
            description = "气象台继续发布寒潮橙色预警信号：预计未来 24 小时气温下降 10℃ 以上。",
            instruction = "请做好防寒保暖和设施防风加固。",
            messageTypeCode = "update"
        ),
        DeveloperWeatherAlertSample(
            key = "thunder_cancel",
            label = "雷电解除",
            eventName = "雷电",
            colorCode = "yellow",
            description = "目前雷雨云团已移出本区，雷电黄色预警信号解除。",
            instruction = "后续仍需关注天气变化。",
            messageTypeCode = "cancel"
        )
    )
}

private fun developerWeatherRiskSamples(): List<DeveloperWeatherRiskSample> {
    return listOf(
        DeveloperWeatherRiskSample(
            key = "risk_heat",
            label = "高温风险",
            title = "天气风险提醒：高温",
            level = "high",
            weatherText = "晴热",
            message = "未来几小时体感温度较高，户外活动中暑风险上升。"
        ),
        DeveloperWeatherRiskSample(
            key = "risk_rain",
            label = "降雨风险",
            title = "天气风险提醒：强降雨",
            level = "medium",
            weatherText = "阵雨",
            message = "短时降雨可能影响通勤，请携带雨具并关注路面积水。"
        ),
        DeveloperWeatherRiskSample(
            key = "risk_haze",
            label = "雾霾风险",
            title = "天气风险提醒：雾霾",
            level = "low",
            weatherText = "霾",
            message = "空气质量可能转差，敏感人群建议减少长时间户外运动。"
        )
    )
}

private fun sendDeveloperPlainNotification(
    context: Context,
    app: App?,
    sample: DeveloperPlainNotificationSample
): String {
    if (app == null) return "应用上下文不可用"
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return "系统通知权限未开启"
    app.notificationCenter.showPlainNotification(
        notificationId = developerNotificationId("plain_${sample.key}"),
        title = sample.title,
        content = sample.content,
        channelId = sample.channelId,
        smallIcon = sample.smallIcon
    )
    return "已发送 ${sample.title}"
}

private fun sendDeveloperWeatherNotification(
    context: Context,
    app: App?,
    sample: DeveloperWeatherAlertSample
): String {
    val alert = sample.toWeatherAlert()
    return sendDeveloperPlainNotification(
        context = context,
        app = app,
        sample = DeveloperPlainNotificationSample(
            key = "weather_${sample.key}",
            title = "开发者测试${sample.label}预警",
            content = alert.description,
            channelId = App.CHANNEL_ID_WEATHER,
            smallIcon = WeatherAlertIconMapper.officialIconRes(alert)
        )
    )
}

private fun showDeveloperLiveOcrProgress(app: App?, settings: MySettings?): String {
    val unavailable = liveCapsuleUnavailableMessage(app, settings)
    if (unavailable != null) return unavailable
    app!!.capsuleCenter.showOcrProgress(
        title = "正在分析截图",
        content = "开发者测试：OCR 识别中，预计数秒后完成。"
    )
    return "已发送 OCR 进度胶囊"
}

private fun showDeveloperRecognitionSuccess(app: App?, settings: MySettings?, count: Int): String {
    val unavailable = liveCapsuleUnavailableMessage(app, settings)
    if (unavailable != null) return unavailable
    app!!.notificationCenter.showCreatedEventResultNotifications(
        sourceType = "developer",
        events = developerRecognitionEvents(count)
    )
    return "已发送 ${count} 条识别成功结果"
}

private fun showDeveloperRecognitionFailure(app: App?, settings: MySettings?): String {
    val unavailable = liveCapsuleUnavailableMessage(app, settings)
    if (unavailable != null) return unavailable
    app!!.notificationCenter.showRecognitionFailureResultNotification(
        RecognitionFailureDisplay(
            reason = "模型返回格式异常",
            suggestion = "请重试，或切换到更稳定的模型"
        )
    )
    return "已发送识别失败结果"
}

private fun showDeveloperDailySummaryNotification(
    context: Context,
    app: App?,
    settings: MySettings?,
    isMorning: Boolean
): String {
    if (app == null) return "应用上下文不可用"
    if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return "系统通知权限未开启"
    val payload = settings?.let { currentSettings ->
        app.dailySummaryQueryApi.buildPayload(
            isMorning = isMorning,
            settings = currentSettings.copy(isDailySummaryEnabled = true),
            events = app.scheduleCenter.events.value,
            weatherData = app.weatherQueryApi.weatherData.value
        )
    } ?: developerDailySummaryPayload(isMorning)
    app.notificationCenter.showDailySummaryNotification(payload, isMorning)
    return "已发送${payload.shortTitle}测试通知"
}

private fun developerDailySummaryPayload(isMorning: Boolean): DailySummaryPayload {
    val shortTitle = if (isMorning) "今日提醒" else "明日预告"
    val titles = if (isMorning) {
        listOf("开发者测试晨会", "开发者测试取件", "开发者测试复盘")
    } else {
        listOf("开发者测试早课", "开发者测试航班", "开发者测试晚间复盘")
    }
    return DailySummaryPayload(
        targetDate = if (isMorning) LocalDate.now() else LocalDate.now().plusDays(1),
        title = "$shortTitle|24°C 阴",
        shortTitle = shortTitle,
        content = "您有 ${titles.size} 个日程：${titles.joinToString("，")}",
        eventCount = titles.size,
        fullLines = titles,
        compactLines = listOf(titles.first(), "以及其他 ${titles.size - 1} 个日程")
    )
}

private fun showDeveloperLiveModelLoading(app: App?, settings: MySettings?): String {
    val unavailable = liveCapsuleUnavailableMessage(app, settings)
    if (unavailable != null) return unavailable
    app!!.capsuleCenter.showModelLoading(
        title = "本地模型加载中",
        content = "开发者测试：正在准备本地语义模型。"
    )
    return "已发送模型加载胶囊"
}

private fun showDeveloperLiveNetworkSpeed(app: App?, settings: MySettings?): String {
    val unavailable = liveCapsuleUnavailableMessage(app, settings)
    if (unavailable != null) return unavailable
    app!!.capsuleCenter.updateNetworkSpeed(
        NetworkSpeedMonitor.NetworkSpeed(
            downloadSpeed = 2_621_440L,
            formattedSpeed = "2.5MB/s"
        )
    )
    return if (settings?.isNetworkSpeedCapsuleEnabled == true) {
        "已发送网速胶囊"
    } else {
        "已写入网速测试值；需开启网速胶囊开关才会显示"
    }
}

private fun showDeveloperWeatherAlertCapsule(
    app: App?,
    settings: MySettings?,
    sample: DeveloperWeatherAlertSample
): String {
    val unavailable = liveCapsuleUnavailableMessage(app, settings)
    if (unavailable != null) return unavailable
    app!!.capsuleCenter.showWeatherAlert("开发者测试城市", sample.toWeatherAlert())
    return "已发送${sample.label}预警胶囊"
}

private fun showDeveloperWeatherRiskCapsule(
    app: App?,
    settings: MySettings?,
    sample: DeveloperWeatherRiskSample
): String {
    val unavailable = liveCapsuleUnavailableMessage(app, settings)
    if (unavailable != null) return unavailable
    app!!.capsuleCenter.showWeatherRisk("开发者测试城市", sample.toWeatherRisk())
    return "已发送${sample.label}胶囊"
}

private fun clearDeveloperLiveCapsules(app: App?): String {
    if (app == null) return "应用上下文不可用"
    app.capsuleCenter.clearOcrCapsule()
    app.capsuleCenter.clearModelLoading()
    app.capsuleCenter.clearWeatherCapsules()
    app.capsuleCenter.updateNetworkSpeed(null)
    return "已清除测试胶囊"
}

private fun liveCapsuleUnavailableMessage(app: App?, settings: MySettings?): String? {
    if (app == null) return "应用上下文不可用"
    if (settings?.isLiveCapsuleEnabled != true) return "请先开启实况胶囊通知"
    return null
}

private fun DeveloperWeatherAlertSample.toWeatherAlert(): WeatherAlertData {
    return WeatherAlertData(
        id = "developer_${key}",
        senderName = "Will do Developer",
        eventName = eventName,
        eventCode = key,
        severity = colorCode,
        colorCode = colorCode,
        messageTypeCode = messageTypeCode,
        headline = "开发者测试：${label}预警",
        description = description,
        instruction = instruction
    )
}

private fun DeveloperWeatherRiskSample.toWeatherRisk(): WeatherRiskAlert {
    return WeatherRiskAlert(
        id = "developer_${key}",
        title = title,
        level = level,
        weatherText = weatherText,
        message = message
    )
}

private fun developerNotificationId(key: String): Int {
    return "developer_notification_test:$key".hashCode() and Int.MAX_VALUE
}

private fun toastResult(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
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
