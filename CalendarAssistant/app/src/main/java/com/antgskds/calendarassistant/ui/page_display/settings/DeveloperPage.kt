package com.antgskds.calendarassistant.ui.page_display.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.developer.DebugAction
import com.antgskds.calendarassistant.core.developer.DebugActionRegistry
import com.antgskds.calendarassistant.data.model.LiveNotificationTemplateMode
import com.antgskds.calendarassistant.ui.components.AppCard
import com.antgskds.calendarassistant.ui.components.AppModalBottomSheet
import com.antgskds.calendarassistant.ui.components.AppSettingsCard
import com.antgskds.calendarassistant.ui.components.PredictiveFloatingActionCard
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 开发者页 —— 测试中心。
 *
 * UI 风格对齐偏好设置页（[PreferenceSettingsPage]）：分组标题 + 16dp 圆角卡片
 * （surfaceContainerLow 底色、无阴影）+ 行间分隔线，复用同包的 [SwitchSettingItem]。
 *
 * 调试动作完全由 [DebugActionRegistry] 驱动：每个动作渲染成一行，点击即在【真实链路】上执行
 * （与 adb `debug:<id>` 同源同实现），标记 dangerous 的动作先二次确认。
 * 加调试动作 = 在注册表登记一条，这里**自动出现**，无需改本页。
 */
@Composable
fun DeveloperPage(
    settingsViewModel: SettingsViewModel,
    uiSize: Int = 2,
    onNavigateToConfig: () -> Unit = {},
    onNavigateToRegexRules: () -> Unit = {}
) {
    val context = LocalContext.current
    val app = context.applicationContext as? App
    val scope = rememberCoroutineScope()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val settings by settingsViewModel.settings.collectAsState()

    var pendingDangerous by remember { mutableStateOf<DebugAction?>(null) }
    var pendingBatch by remember { mutableStateOf<DebugActionBatch?>(null) }
    var runningId by remember { mutableStateOf<String?>(null) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showLogExportSheet by remember { mutableStateOf(false) }
    var quickActionSheet by remember { mutableStateOf<QuickActionSheetSpec?>(null) }
    val actionsById = remember { DebugActionRegistry.actions.associateBy { it.id } }

    fun actions(ids: List<String>): List<DebugAction> = ids.mapNotNull { actionsById[it] }

    val createScheduleActions = remember {
        actions(
            listOf(
                "create-dev-general",
                "create-dev-recurring",
                "create-dev-pickup",
                "create-dev-food",
                "create-dev-ticket",
                "create-dev-sender",
                "create-dev-train",
                "create-dev-flight",
                "create-dev-taxi",
                "create-dev-course",
                "create-soon",
                "create-missed",
                "test-imported-restore"
            )
        )
    }
    val normalNotificationActions = remember {
        actions(
            listOf(
                "test-normal-double-action",
                "test-daily-today",
                "test-daily-tomorrow",
                "weather-alert-normal-heat",
                "weather-alert-normal-thunder",
                "weather-alert-normal-rainstorm",
                "weather-alert-normal-wind",
                "weather-alert-normal-haze",
                "weather-alert-normal-snow",
                "weather-alert-normal-cold",
                "weather-alert-normal-cold-wave-update",
                "weather-alert-normal-thunder-cancel",
                "weather-risk-normal-heat",
                "weather-risk-normal-rain",
                "weather-risk-normal-haze"
            )
        )
    }
    val liveNotificationActions = remember {
        actions(
            listOf(
                "test-live-double-action",
                "test-live-daily-today",
                "test-live-daily-tomorrow",
                "test-capsule-ocr",
                "test-capsule-recognition-1",
                "test-capsule-recognition-2",
                "test-capsule-recognition-fail",
                "test-capsule-model-loading",
                "test-capsule-network-speed",
                "weather-alert-live-heat",
                "weather-alert-live-thunder",
                "weather-alert-live-rainstorm",
                "weather-alert-live-wind",
                "weather-alert-live-haze",
                "weather-alert-live-snow",
                "weather-alert-live-cold",
                "weather-alert-live-cold-wave-update",
                "weather-alert-live-thunder-cancel",
                "weather-risk-live-heat",
                "weather-risk-live-rain",
                "weather-risk-live-haze"
            )
        )
    }
    val coveredQuickActionIds = remember(createScheduleActions, normalNotificationActions, liveNotificationActions) {
        buildSet {
            addAll(createScheduleActions.map { it.id })
            addAll(normalNotificationActions.map { it.id })
            addAll(liveNotificationActions.map { it.id })
            addAll(
                listOf(
                    "create-dev-all",
                    "create-single",
                    "create-recurring",
                    "weather-alert",
                    "weather-risk",
                    "test-normal-double-action",
                    "test-live-double-action",
                    "test-plain-schedule",
                    "test-plain-pickup",
                    "test-plain-course",
                    "test-daily-today",
                    "test-daily-tomorrow",
                    "test-new-chain-preview"
                )
            )
        }
    }

    LaunchedEffect(
        settings.developerOptionsUnlocked,
        settings.developerOptionsEnabled,
        settings.developerOptionsDisabledAtMillis
    ) {
        if (
            settings.developerOptionsUnlocked &&
            !settings.developerOptionsEnabled &&
            settings.developerOptionsDisabledAtMillis > 0L
        ) {
            val remaining = DEVELOPER_OPTION_HIDE_DELAY_MS -
                (System.currentTimeMillis() - settings.developerOptionsDisabledAtMillis)
            if (remaining > 0L) {
                delay(remaining)
            }
            val latest = settingsViewModel.settings.value
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

    // 与偏好设置页一致的字体样式
    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary
    )
    val cardTitleStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
    )
    val cardSubtitleStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )

    fun runAction(action: DebugAction) {
        val target = app
        if (target == null) {
            Toast.makeText(context, "应用上下文不可用", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            runningId = action.id
            try {
                action.execute(target)
                Toast.makeText(context, "已执行 ${action.label}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "执行失败：${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                runningId = null
            }
        }
    }

    fun runActions(actions: List<DebugAction>) {
        val target = app
        if (target == null) {
            Toast.makeText(context, "应用上下文不可用", Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            var successCount = 0
            val failed = mutableListOf<String>()
            try {
                actions.forEach { action ->
                    runningId = action.id
                    try {
                        action.execute(target)
                        successCount++
                    } catch (e: Exception) {
                        failed += "${action.label}: ${e.message ?: "未知错误"}"
                    }
                }
            } finally {
                runningId = null
            }
            val message = if (failed.isEmpty()) {
                "已执行 $successCount 项"
            } else {
                "执行完成：成功 $successCount 项，失败 ${failed.size} 项"
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    fun openQuickActionSheet(
        title: String,
        description: String,
        actions: List<DebugAction>,
        confirmText: String,
        requiresConfirm: Boolean,
        defaultSelected: Boolean = true
    ) {
        quickActionSheet = QuickActionSheetSpec(
            title = title,
            description = description,
            actions = actions,
            confirmText = confirmText,
            requiresConfirm = requiresConfirm,
            defaultSelected = defaultSelected
        )
    }

    fun exportLogs(minutes: Int?) {
        settingsViewModel.exportDiagnosticLogs(minutes) { result ->
            val message = result.fold(
                onSuccess = { path -> "日志包已导出到 $path" },
                onFailure = { error -> "日志导出失败: ${error.message ?: "未知错误"}" }
            )
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "测试中心",
                style = sectionTitleStyle
            )

            SettingsCard {
                SwitchSettingItem(
                    title = "开发者选项",
                    subtitle = "打开后才显示调试入口。关闭超过 5 分钟后会隐藏开发者页入口，需要重新在关于页解锁。",
                    checked = settings.developerOptionsEnabled,
                    onCheckedChange = { settingsViewModel.setDeveloperOptionsEnabled(it) },
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle
                )
            }

            if (!settings.developerOptionsUnlocked) {
                DisabledDeveloperCard(
                    message = "开发者选项尚未解锁，请在关于页连续点击版本号解锁。",
                    cardSubtitleStyle = cardSubtitleStyle
                )
                return@Column
            }

            if (!settings.developerOptionsEnabled) {
                DisabledDeveloperCard(
                    message = "开发者选项已关闭。打开上方开关后，才可以进入配置编辑、导出日志或执行调试动作。",
                    cardSubtitleStyle = cardSubtitleStyle
                )
                return@Column
            }

            // 配置编辑入口
            SettingsCard {
                ActionSettingItem(
                    title = "配置编辑",
                    subtitle = "编辑 ConfigCatalog 中登记的底层配置",
                    value = "",
                    icon = Icons.Default.ChevronRight,
                    enabled = true,
                    onClick = onNavigateToConfig,
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                    cardValueStyle = cardSubtitleStyle
                )
                RowDivider()
                ActionSettingItem(
                    title = "正则规则",
                    subtitle = "编辑正则日程识别规则并测试匹配结果",
                    value = "",
                    icon = Icons.Default.ChevronRight,
                    enabled = true,
                    onClick = onNavigateToRegexRules,
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                    cardValueStyle = cardSubtitleStyle
                )
            }

            Text(text = "通知与日志", style = sectionTitleStyle)
            DeveloperOptionsCard(
                liveNotificationTemplateMode = settings.liveNotificationTemplateMode,
                onOpenLogExportSheet = { showLogExportSheet = true },
                onLiveNotificationTemplateModeChange = { mode ->
                    settingsViewModel.updatePreference(liveNotificationTemplateMode = mode)
                    app?.capsuleCenter?.forceRefresh()
                    Toast.makeText(context, "原生实况通知模板已设为 ${liveTemplateModeLabel(mode)}", Toast.LENGTH_SHORT).show()
                }
            )

            Text(text = "快捷测试", style = sectionTitleStyle)
            SettingsCard {
                ActionSettingItem(
                    title = "创建测试日程",
                    subtitle = "普通、重复、取件/取餐/取票/寄件、列车、航班、打车、课程和专项测试",
                    value = "",
                    icon = Icons.Default.ChevronRight,
                    enabled = createScheduleActions.isNotEmpty(),
                    onClick = {
                        openQuickActionSheet(
                            title = "创建测试日程",
                            description = "勾选要创建的测试日程。该操作会写入本地日程，并触发真实提醒链路。",
                            actions = createScheduleActions,
                            confirmText = "创建",
                            requiresConfirm = true
                        )
                    },
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                    cardValueStyle = cardSubtitleStyle
                )
                RowDivider()
                ActionSettingItem(
                    title = "普通通知测试",
                    subtitle = "双按钮、每日提醒、明日预告和各类天气普通通知",
                    value = "",
                    icon = Icons.Default.ChevronRight,
                    enabled = normalNotificationActions.isNotEmpty(),
                    onClick = {
                        openQuickActionSheet(
                            title = "普通通知测试",
                            description = "勾选要触发的双按钮、每日提醒、明日预告或普通天气通知。",
                            actions = normalNotificationActions,
                            confirmText = "触发",
                            requiresConfirm = false,
                            defaultSelected = false
                        )
                    },
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                    cardValueStyle = cardSubtitleStyle
                )
                RowDivider()
                ActionSettingItem(
                    title = "实况通知测试",
                    subtitle = "双按钮、每日提醒、胶囊状态、识别结果、模型加载、网速和各类天气实况",
                    value = "",
                    icon = Icons.Default.ChevronRight,
                    enabled = liveNotificationActions.isNotEmpty(),
                    onClick = {
                        openQuickActionSheet(
                            title = "实况通知测试",
                            description = "勾选要触发的实况通知。每日提醒需开启实况胶囊开关才会以实况形态显示。",
                            actions = liveNotificationActions,
                            confirmText = "触发",
                            requiresConfirm = false,
                            defaultSelected = false
                        )
                    },
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                    cardValueStyle = cardSubtitleStyle
                )
            }

            // 列表排序方向
            Text(text = "列表排序", style = sectionTitleStyle)
            SettingsCard {
                SwitchSettingItem(
                    title = "首页列表倒序",
                    subtitle = "开启后今日/明日按时间从晚到早",
                    checked = settings.homeListReverseOrder,
                    onCheckedChange = { settingsViewModel.updateListSortOrder(homeListReverseOrder = it) },
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle
                )
                RowDivider()
                SwitchSettingItem(
                    title = "全部日程倒序",
                    subtitle = "开启后全部日程页按时间从晚到早",
                    checked = settings.allEventsListReverseOrder,
                    onCheckedChange = { settingsViewModel.updateListSortOrder(allEventsListReverseOrder = it) },
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle
                )
                RowDivider()
                SwitchSettingItem(
                    title = "悬浮窗倒序",
                    subtitle = "关闭后悬浮窗按时间从早到晚，打开自动定位到当前",
                    checked = settings.floatingListReverseOrder,
                    onCheckedChange = { settingsViewModel.updateListSortOrder(floatingListReverseOrder = it) },
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle
                )
                RowDivider()
                SwitchSettingItem(
                    title = "归档列表倒序",
                    subtitle = "开启后归档页按结束日期从晚到早",
                    checked = settings.archivesListReverseOrder,
                    onCheckedChange = { settingsViewModel.updateListSortOrder(archivesListReverseOrder = it) },
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle
                )
                RowDivider()
                ActionSettingItem(
                    title = "恢复列表排序默认",
                    subtitle = "首页/全部=正序，悬浮窗/归档=倒序",
                    value = "",
                    enabled = true,
                    onClick = { showResetConfirm = true },
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                    cardValueStyle = cardSubtitleStyle
                )
            }

            // 调试动作（按 category 分组，每组收进一张卡片）
            DebugActionRegistry.actions
                .filterNot { it.id in coveredQuickActionIds }
                .groupBy { it.category }
                .forEach { (category, items) ->
                    Text(text = category, style = sectionTitleStyle)
                    SettingsCard {
                        items.forEachIndexed { index, action ->
                            if (index > 0) RowDivider()
                            DeveloperActionRow(
                                action = action,
                                running = runningId == action.id,
                                cardTitleStyle = cardTitleStyle,
                                cardSubtitleStyle = cardSubtitleStyle,
                                onRun = {
                                    if (action.dangerous) {
                                        pendingDangerous = action
                                    } else {
                                        runAction(action)
                                    }
                                }
                            )
                        }
                    }
                }
        }

        val confirming = pendingDangerous
        PredictiveFloatingActionCard(
            visible = confirming != null,
            title = "确认执行",
            content = "「${confirming?.label ?: ""}」是高风险调试动作（debug:${confirming?.id ?: ""}）。\n确认执行？",
            confirmText = "执行",
            isDestructive = true,
            onConfirm = {
                val action = confirming
                pendingDangerous = null
                if (action != null) runAction(action)
            },
            onDismiss = { pendingDangerous = null },
            modifier = Modifier.padding(bottom = bottomInset)
        )

        val confirmingBatch = pendingBatch
        PredictiveFloatingActionCard(
            visible = confirmingBatch != null,
            title = "确认${confirmingBatch?.confirmText ?: "执行"}",
            content = confirmingBatch?.let { batch ->
                val names = batch.actions.joinToString("\n") { "• ${it.label}" }
                "将执行 ${batch.actions.size} 个调试动作：\n$names\n确认继续？"
            } ?: "",
            confirmText = confirmingBatch?.confirmText ?: "执行",
            isDestructive = true,
            onConfirm = {
                val batch = confirmingBatch
                pendingBatch = null
                if (batch != null) runActions(batch.actions)
            },
            onDismiss = { pendingBatch = null },
            modifier = Modifier.padding(bottom = bottomInset)
        )

        PredictiveFloatingActionCard(
            visible = showResetConfirm,
            title = "恢复默认",
            content = "将列表排序方向恢复为出厂默认：\n首页 / 全部日程 = 正序\n悬浮窗 / 归档 = 倒序",
            confirmText = "恢复",
            isDestructive = false,
            onConfirm = {
                showResetConfirm = false
                settingsViewModel.resetListSortOrderToDefault()
                Toast.makeText(context, "已恢复列表排序默认", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showResetConfirm = false },
            modifier = Modifier.padding(bottom = bottomInset)
        )

        if (showLogExportSheet) {
            LogExportSheet(
                onDismiss = { showLogExportSheet = false },
                onExport = { minutes ->
                    showLogExportSheet = false
                    exportLogs(minutes)
                }
            )
        }

        val sheet = quickActionSheet
        if (sheet != null) {
            DebugActionSelectSheet(
                spec = sheet,
                onDismiss = { quickActionSheet = null },
                onRun = { selectedActions ->
                    quickActionSheet = null
                    if (sheet.requiresConfirm) {
                        pendingBatch = DebugActionBatch(
                            actions = selectedActions,
                            confirmText = sheet.confirmText
                        )
                    } else {
                        runActions(selectedActions)
                    }
                }
            )
        }
    }
}

@Composable
private fun DisabledDeveloperCard(
    message: String,
    cardSubtitleStyle: androidx.compose.ui.text.TextStyle
) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Text(
            text = message,
            style = cardSubtitleStyle,
            modifier = Modifier.padding(16.dp)
        )
    }
}

@Composable
private fun DeveloperOptionsCard(
    liveNotificationTemplateMode: String,
    onOpenLogExportSheet: () -> Unit,
    onLiveNotificationTemplateModeChange: (String) -> Unit
) {
    val haptics = rememberAppHaptics()
    val titleStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
    val subtitleStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    val modes = LiveNotificationTemplateMode.ALL
    val normalizedMode = LiveNotificationTemplateMode.normalize(liveNotificationTemplateMode)
    val selectedIndex = modes.indexOf(normalizedMode).takeIf { it >= 0 } ?: 0
    SettingsCard {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "原生实况通知模板",
                style = titleStyle,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "控制原生实况通知使用完整多行内容还是两行精简内容；仅影响原生通知通道。",
                style = subtitleStyle
            )
            Text(
                text = "当前：${liveTemplateModeLabel(modes[selectedIndex])}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Slider(
                value = selectedIndex.toFloat(),
                onValueChange = { value ->
                    val nextIndex = value.roundToInt().coerceIn(0, modes.lastIndex)
                    val nextMode = modes[nextIndex]
                    if (nextMode != normalizedMode) {
                        haptics.selection()
                        onLiveNotificationTemplateModeChange(nextMode)
                    }
                },
                valueRange = 0f..modes.lastIndex.toFloat(),
                steps = (modes.size - 2).coerceAtLeast(0)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                modes.forEach { mode ->
                    Text(
                        text = liveTemplateModeLabel(mode),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (mode == normalizedMode) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        }
        RowDivider()
        ActionSettingItem(
            title = "日志导出",
            subtitle = "选择时间范围后导出本地日志、崩溃日志、识别日志和应用 logcat",
            value = "",
            icon = Icons.Default.ChevronRight,
            enabled = true,
            onClick = { haptics.confirm(); onOpenLogExportSheet() },
            cardTitleStyle = titleStyle,
            cardSubtitleStyle = subtitleStyle,
            cardValueStyle = subtitleStyle
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LogExportSheet(
    onDismiss: () -> Unit,
    onExport: (Int?) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedMinutes by remember { mutableStateOf<Int?>(15) }
    val haptics = rememberAppHaptics()
    val options = remember {
        listOf(
            LogExportOption(5, "最近 5 分钟", "适合刚复现的问题"),
            LogExportOption(15, "最近 15 分钟", "覆盖一次完整操作链路"),
            LogExportOption(30, "最近 30 分钟", "适合间歇问题排查"),
            LogExportOption(60, "最近 60 分钟", "覆盖较长后台任务"),
            LogExportOption(null, "完整诊断日志", "导出当前保留的全部诊断信息")
        )
    }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "日志导出",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "勾选要导出的日志范围。日志可能包含识别文本、Prompt、模型响应和接口返回；有 Shizuku/Root 时 logcat 更完整。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            options.forEach { option ->
                LogExportOptionRow(
                    option = option,
                    selected = selectedMinutes == option.minutes,
                    onSelect = {
                        haptics.selection()
                        selectedMinutes = option.minutes
                    }
                )
            }
            Button(
                onClick = { haptics.confirm(); onExport(selectedMinutes) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("导出")
            }
        }
    }
}

@Composable
private fun LogExportOptionRow(
    option: LogExportOption,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = option.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = option.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Checkbox(checked = selected, onCheckedChange = { onSelect() })
    }
}

private data class LogExportOption(
    val minutes: Int?,
    val title: String,
    val subtitle: String
)

private data class QuickActionSheetSpec(
    val title: String,
    val description: String,
    val actions: List<DebugAction>,
    val confirmText: String,
    val requiresConfirm: Boolean,
    val defaultSelected: Boolean
)

private data class DebugActionBatch(
    val actions: List<DebugAction>,
    val confirmText: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DebugActionSelectSheet(
    spec: QuickActionSheetSpec,
    onDismiss: () -> Unit,
    onRun: (List<DebugAction>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = rememberAppHaptics()
    var selectedIds by remember(spec) {
        mutableStateOf(if (spec.defaultSelected) spec.actions.map { it.id }.toSet() else emptySet())
    }
    val selectedActions = remember(spec, selectedIds) {
        spec.actions.filter { it.id in selectedIds }
    }

    AppModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = spec.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = spec.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                spec.actions.forEach { action ->
                    DebugActionOptionRow(
                        action = action,
                        selected = action.id in selectedIds,
                        onSelect = {
                            haptics.selection()
                            selectedIds = if (action.id in selectedIds) {
                                selectedIds - action.id
                            } else {
                                selectedIds + action.id
                            }
                        }
                    )
                }
            }
            Button(
                onClick = { haptics.confirm(); onRun(selectedActions) },
                enabled = selectedActions.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(spec.confirmText)
            }
        }
    }
}

@Composable
private fun DebugActionOptionRow(
    action: DebugAction,
    selected: Boolean,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = action.label,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "debug:${action.id}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Checkbox(checked = selected, onCheckedChange = { onSelect() })
    }
}

private fun liveTemplateModeLabel(mode: String): String {
    return when (LiveNotificationTemplateMode.normalize(mode)) {
        LiveNotificationTemplateMode.FULL -> "完整"
        LiveNotificationTemplateMode.COMPACT -> "精简"
        else -> "自动"
    }
}

/** 偏好设置页同款卡片容器：16dp 圆角、surfaceContainerLow 底色、无阴影、内部上下 8dp。 */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    AppSettingsCard { content() }
}

/** 偏好设置页同款行间分隔线。 */
@Composable
private fun RowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

/** 注册总览的一行：显示「分类名 — N 项」，点击展开/收起看明细。只读。 */
@Composable
private fun RegistryOverviewRow(
    name: String,
    items: List<String>,
    cardTitleStyle: androidx.compose.ui.text.TextStyle,
    cardSubtitleStyle: androidx.compose.ui.text.TextStyle
) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = name, style = cardTitleStyle, modifier = Modifier.weight(1f))
            Text(text = "${items.size} 项", style = cardSubtitleStyle)
        }
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                items.forEachIndexed { i, line ->
                    Text(
                        text = "${i + 1}. $line",
                        style = cardSubtitleStyle,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

/** 单个调试动作行（无独立卡片背景，放进分组卡片内）。 */
@Composable
private fun DeveloperActionRow(
    action: DebugAction,
    running: Boolean,
    cardTitleStyle: androidx.compose.ui.text.TextStyle,
    cardSubtitleStyle: androidx.compose.ui.text.TextStyle,
    onRun: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = action.label,
                    style = cardTitleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "debug:${action.id}",
                style = cardSubtitleStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Button(
            onClick = onRun,
            enabled = !running
        ) {
            Text(if (running) "执行中…" else "执行")
        }
    }
}

private const val DEVELOPER_OPTION_HIDE_DELAY_MS = 5 * 60 * 1000L
