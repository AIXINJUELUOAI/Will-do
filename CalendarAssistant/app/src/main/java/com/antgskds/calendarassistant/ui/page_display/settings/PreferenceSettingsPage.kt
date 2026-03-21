package com.antgskds.calendarassistant.ui.page_display.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.calendar.CalendarPermissionHelper
import com.antgskds.calendarassistant.data.repository.AppRepository
import com.antgskds.calendarassistant.service.floating.EdgeBarService
import com.antgskds.calendarassistant.service.receiver.DailySummaryReceiver
import com.antgskds.calendarassistant.ui.components.FloatingActionCard
import com.antgskds.calendarassistant.ui.components.ToastType
import com.antgskds.calendarassistant.ui.components.UniversalToast
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun PreferenceSettingsPage(
    viewModel: SettingsViewModel,
    uiSize: Int = 2
) {
    val settings by viewModel.settings.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var currentToastType by remember { mutableStateOf(ToastType.INFO) }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Toast 辅助函数
    fun showToast(message: String, type: ToastType = ToastType.INFO) {
        currentToastType = type
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    // 获取 events 用于检测重复提醒
    val repository = remember { AppRepository.getInstance(context) }
    val events by repository.events.collectAsState()

    var hasOverlayPermission by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    fun refreshOverlayPermission() {
        hasOverlayPermission = Settings.canDrawOverlays(context)
    }

    LaunchedEffect(Unit) {
        refreshOverlayPermission()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshOverlayPermission()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    fun openOverlayPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    }

    fun startEdgeBarService() {
        context.startService(Intent(context, EdgeBarService::class.java))
    }

    fun stopEdgeBarService() {
        context.stopService(Intent(context, EdgeBarService::class.java))
    }

    // --- 字体样式优化 ---
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
    val cardValueStyle = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    // 日历权限请求
    var showPermissionDialog by remember { mutableStateOf(false) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            viewModel.enableCalendarSyncAndSyncNow { result ->
                (context.applicationContext as? App)?.initCalendarObserver()
                if (result.isSuccess) {
                    snackbarHostState.showSnackbar("日历同步已开启，并已立即同步")
                } else {
                    snackbarHostState.showSnackbar("日历同步开启失败：${result.exceptionOrNull()?.message ?: "未知错误"}")
                }
            }
        } else {
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "需要日历权限才能使用同步功能",
                    actionLabel = "去设置",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }
            }
        }
    }

    val requestCalendarPermission = {
        showPermissionDialog = false
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
        } else {
            emptyArray()
        }
        calendarPermissionLauncher.launch(permissions)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
                .padding(bottom = 80.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ================== 显示板块 ==================
            Text("显示", style = sectionTitleStyle)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SliderSettingItem(
                        title = "界面大小",
                        subtitle = "调整界面缩放（相对于设备原生大小）",
                        value = settings.uiSize.toFloat(),
                        onValueChange = { viewModel.updateUiSize(it.toInt()) },
                        valueRange = 1f..3f,
                        steps = 1, // 离散：1, 2, 3
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle,
                        cardValueStyle = cardValueStyle,
                        showValueAsNumber = false // 显示文字：小/中/大
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SwitchSettingItem(
                        title = "显示明日日程",
                        subtitle = "在今日日程列表底部预览明日安排",
                        checked = settings.showTomorrowEvents,
                        onCheckedChange = { viewModel.updatePreference(showTomorrow = it) },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SwitchSettingItem(
                        title = "悬浮日程",
                        subtitle = "长按音量+键呼出悬浮窗",
                        checked = settings.isFloatingWindowEnabled,
                        onCheckedChange = { isChecked ->
                            if (isChecked && !hasOverlayPermission) {
                                openOverlayPermissionSettings()
                                return@SwitchSettingItem
                            }
                            viewModel.updatePreference(
                                floatingWindow = isChecked,
                                edgeBarEnabled = if (isChecked) settings.edgeBarEnabled else false
                            )
                            if (!isChecked) {
                                stopEdgeBarService()
                            } else if (settings.edgeBarEnabled) {
                                startEdgeBarService()
                            }
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "需要悬浮窗权限才能正常使用",
                            style = cardSubtitleStyle,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = settings.isFloatingWindowEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        FloatingEventRangeSlider(
                            title = "悬浮窗日程范围",
                            subtitle = when (settings.floatingEventRange) {
                                0 -> "显示全部日程"
                                1 -> "只显示今日日程"
                                2 -> "显示今日和明日日程"
                                else -> "显示全部日程"
                            },
                            eventRange = settings.floatingEventRange,
                            onEventRangeChange = { range ->
                                viewModel.updatePreference(floatingEventRange = range)
                            },
                            cardTitleStyle = cardTitleStyle,
                            cardSubtitleStyle = cardSubtitleStyle
                        )

                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )

                        SwitchSettingItem(
                            title = "侧边栏唤起",
                            subtitle = "在屏幕侧边滑动呼出悬浮窗",
                            checked = settings.edgeBarEnabled,
                            onCheckedChange = { isChecked ->
                                if (isChecked && !hasOverlayPermission) {
                                    openOverlayPermissionSettings()
                                    return@SwitchSettingItem
                                }
                                viewModel.updateEdgeBarSettings(enabled = isChecked)
                                if (isChecked) {
                                    startEdgeBarService()
                                } else {
                                    stopEdgeBarService()
                                }
                            },
                            cardTitleStyle = cardTitleStyle,
                            cardSubtitleStyle = cardSubtitleStyle
                        )

                        AnimatedVisibility(
                            visible = settings.edgeBarEnabled,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            Column {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("侧边位置", style = cardTitleStyle)
                                        Text("默认右侧", style = cardSubtitleStyle)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        val isRight = settings.edgeBarSide == "RIGHT"
                                        if (isRight) {
                                            Button(onClick = { viewModel.updateEdgeBarSettings(side = "RIGHT") }) {
                                                Text("右侧")
                                            }
                                            OutlinedButton(onClick = { viewModel.updateEdgeBarSettings(side = "LEFT") }) {
                                                Text("左侧")
                                            }
                                        } else {
                                            OutlinedButton(onClick = { viewModel.updateEdgeBarSettings(side = "RIGHT") }) {
                                                Text("右侧")
                                            }
                                            Button(onClick = { viewModel.updateEdgeBarSettings(side = "LEFT") }) {
                                                Text("左侧")
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )

                                SliderSettingItem(
                                    title = "纵向位置",
                                    subtitle = "上下位置百分比",
                                    value = settings.edgeBarYPercent,
                                    onValueChange = { viewModel.updateEdgeBarSettings(yPercent = it) },
                                    valueRange = 0f..100f,
                                    steps = 9,
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle,
                                    showValueAsNumber = true,
                                    valueUnit = "%"
                                )

                                SliderSettingItem(
                                    title = "宽度",
                                    subtitle = "侧边条宽度",
                                    value = settings.edgeBarWidthDp.toFloat(),
                                    onValueChange = { viewModel.updateEdgeBarSettings(widthDp = it.toInt()) },
                                    valueRange = 4f..20f,
                                    steps = 15,
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle,
                                    showValueAsNumber = true,
                                    valueUnit = "dp"
                                )

                                SliderSettingItem(
                                    title = "高度",
                                    subtitle = "侧边条高度",
                                    value = settings.edgeBarHeightDp.toFloat(),
                                    onValueChange = { viewModel.updateEdgeBarSettings(heightDp = it.toInt()) },
                                    valueRange = 60f..240f,
                                    steps = 17,
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle,
                                    showValueAsNumber = true,
                                    valueUnit = "dp"
                                )

                                SliderSettingItem(
                                    title = "颜色深浅",
                                    subtitle = "调整透明度",
                                    value = (settings.edgeBarAlpha * 100f),
                                    onValueChange = { viewModel.updateEdgeBarSettings(alpha = it / 100f) },
                                    valueRange = 10f..100f,
                                    steps = 8,
                                    cardTitleStyle = cardTitleStyle,
                                    cardSubtitleStyle = cardSubtitleStyle,
                                    cardValueStyle = cardValueStyle,
                                    showValueAsNumber = true,
                                    valueUnit = "%"
                                )

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(onClick = {
                                        viewModel.updateEdgeBarSettings(
                                            enabled = true,
                                            side = "RIGHT",
                                            yPercent = 50f,
                                            widthDp = 8,
                                            heightDp = 120,
                                            alpha = 0.4f
                                        )
                                    }) {
                                        Text("恢复默认")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ================== 通知板块 ==================
            Text("通知", style = sectionTitleStyle)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SwitchSettingItem(
                        title = "每日日程提醒",
                        subtitle = "早06:00和晚22:00推送汇总",
                        checked = settings.isDailySummaryEnabled,
                        onCheckedChange = { isChecked ->
                            viewModel.updatePreference(dailySummary = isChecked)
                            if (isChecked) DailySummaryReceiver.schedule(context)
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SwitchSettingItem(
                        title = "实况胶囊通知",
                        subtitle = "日程开始时显示实况通知",
                        checked = settings.isLiveCapsuleEnabled,
                        onCheckedChange = { isChecked ->
                            viewModel.updatePreference(liveCapsule = isChecked)
                            if (isChecked) showToast("实况胶囊已开启", ToastType.INFO)
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                    AnimatedVisibility(
                        visible = settings.isLiveCapsuleEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            SwitchSettingItem(
                                title = "取件码聚合 (Beta)",
                                subtitle = "当有多个取件码时合并显示为一个胶囊",
                                checked = settings.isPickupAggregationEnabled,
                                onCheckedChange = { isChecked ->
                                    viewModel.updatePreference(pickupAggregation = isChecked)
                                },
                                cardTitleStyle = cardTitleStyle,
                                cardSubtitleStyle = cardSubtitleStyle
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    AdvanceReminderSettingItem(
                        title = "日程提前提醒",
                        subtitle = if (settings.isAdvanceReminderEnabled)
                            "提前 ${settings.advanceReminderMinutes} 分钟"
                        else
                            "日程开始时",
                        checked = settings.isAdvanceReminderEnabled,
                        minutes = settings.advanceReminderMinutes,
                        onCheckedChange = { isChecked ->
                            viewModel.updatePreference(advanceReminderEnabled = isChecked)
                            if (isChecked && settings.advanceReminderMinutes > 0) {
                                val hasDuplicate = events.any { event ->
                                    event.reminders.any { it <= settings.advanceReminderMinutes }
                                }
                                if (hasDuplicate) {
                                    showToast("检测到可能存在的重复提醒", ToastType.INFO)
                                }
                            }
                        },
                        onMinutesChange = { minutes ->
                            viewModel.updatePreference(advanceReminderMinutes = minutes)
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    SwitchSettingItem(
                        title = "网速胶囊",
                        subtitle = "在状态栏显示下载速度",
                        checked = settings.isNetworkSpeedCapsuleEnabled,
                        onCheckedChange = { isChecked ->
                            viewModel.updatePreference(networkSpeedCapsule = isChecked)
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "网速胶囊会覆盖其他胶囊显示",
                            style = cardSubtitleStyle,
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                }
            }


            // ================== AI 板块 ==================
            Text("AI", style = sectionTitleStyle)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SwitchSettingItem(
                        title = "使用多模态AI",
                        subtitle = "开启后图片识别将使用多模态模型",
                        checked = settings.useMultimodalAi,
                        onCheckedChange = { isChecked ->
                            viewModel.updatePreference(useMultimodalAi = isChecked)
                            showToast(if (isChecked) "已切换为多模态AI" else "已切换为文本AI")
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SwitchSettingItem(
                        title = "关闭思考",
                        subtitle = "仅适配 OpenAI",
                        checked = settings.disableThinking,
                        onCheckedChange = { isChecked ->
                            viewModel.updatePreference(disableThinking = isChecked)
                            showToast(if (isChecked) "快速模式已开启" else "快速模式已关闭")
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                }
            }

            // ================== 日程板块 ==================
            Text("日程", style = sectionTitleStyle)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SwitchSettingItem(
                        title = "日历同步",
                        subtitle = "将课程和日程同步到系统日历",
                        checked = syncStatus.isEnabled,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                if (CalendarPermissionHelper.hasAllPermissions(context)) {
                                    viewModel.enableCalendarSyncAndSyncNow { result ->
                                        (context.applicationContext as? App)?.initCalendarObserver()
                                        if (result.isSuccess) {
                                            showToast("日历同步已开启，并已立即同步")
                                        } else {
                                            showToast("日历同步开启失败", ToastType.ERROR)
                                        }
                                    }
                                } else {
                                    showPermissionDialog = true
                                }
                            } else {
                                viewModel.toggleCalendarSync(false)
                                showToast("日历同步已关闭")
                            }
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )

                    AnimatedVisibility(
                        visible = syncStatus.isEnabled,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            SwitchSettingItem(
                                title = "同步重复日程 (Beta)",
                                subtitle = "从系统日历导入重复日程，仅同步正负30天实例",
                                checked = settings.isRecurringCalendarSyncEnabled,
                                onCheckedChange = { isChecked ->
                                    viewModel.toggleRecurringCalendarSync(isChecked) { result ->
                                        if (result.isSuccess) {
                                            if (isChecked) {
                                                showToast("重复日程同步已开启，并已立即同步")
                                            } else {
                                                val removedCount = result.getOrNull() ?: 0
                                                showToast("重复日程同步已关闭，已清理 $removedCount 条导入记录")
                                            }
                                        } else {
                                            val message = result.exceptionOrNull()?.message
                                            showToast(message ?: "重复日程同步切换失败", ToastType.ERROR)
                                        }
                                    }
                                },
                                cardTitleStyle = cardTitleStyle,
                                cardSubtitleStyle = cardSubtitleStyle
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SwitchSettingItem(
                        title = "自动归档",
                        subtitle = "日程过期后立即自动归档",
                        checked = settings.autoArchiveEnabled,
                        onCheckedChange = { isChecked ->
                            viewModel.updatePreference(autoArchive = isChecked)
                        },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                }
            }

            // ================== 截图板块 (新) ==================
            // 注意：现在它在 Column 内部，位于“日程”卡片之后
            Text("截图", style = sectionTitleStyle)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SliderSettingItem(
                        title = "截图延迟",
                        subtitle = "截图与分析之间的等待时间",
                        value = settings.screenshotDelayMs.toFloat(),
                        onValueChange = { viewModel.updateScreenshotDelay(it.toLong()) },
                        valueRange = 1000f..2500f,
                        steps = 0, // 0 = 无极调节
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle,
                        cardValueStyle = cardValueStyle,
                        showValueAsNumber = true, // 开启数字显示
                        valueUnit = "ms"
                    )
                }
            }

        } // <--- Column 结束在这里，确保所有板块都在里面

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp + bottomInset),
            snackbar = { data -> UniversalToast(message = data.visuals.message, type = currentToastType) }
        )

        AnimatedVisibility(
            visible = showPermissionDialog,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showPermissionDialog = false }
                    )
            )
        }

        FloatingActionCard(
            visible = showPermissionDialog,
            title = "需要日历权限",
            content = "为了让您在系统日历中查看和管理课程与日程，需要授予应用读取和写入日历的权限。",
            confirmText = "授予权限",
            dismissText = "取消",
            isDestructive = false,
            isLoading = false,
            onConfirm = requestCalendarPermission,
            onDismiss = { showPermissionDialog = false },
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

// ... SwitchSettingItem 保持不变 ...
@Composable
fun SwitchSettingItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = cardTitleStyle)
            Text(subtitle, style = cardSubtitleStyle)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ... SliderSettingItem 修复并优化 ...
@Composable
fun SliderSettingItem(
    title: String,
    subtitle: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
    cardValueStyle: TextStyle,
    showValueAsNumber: Boolean = false, // 新增参数
    valueUnit: String = "ms"            // 新增参数
) {
    // 根据 showValueAsNumber 决定显示逻辑
    val displayValue = if (showValueAsNumber) {
        "${value.toInt()}$valueUnit"
    } else {
        // 旧逻辑：界面大小
        val sizeLabels = mapOf(1f to "小", 2f to "中", 3f to "大")
        sizeLabels[value] ?: ""
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = cardTitleStyle)
                Text(subtitle, style = cardSubtitleStyle)
            }
            // 显示动态数值
            Text(
                text = displayValue,
                style = cardValueStyle
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            steps = steps
        )
    }
}

// ... AdvanceReminderSettingItem 保持不变 ...
@Composable
fun AdvanceReminderSettingItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    minutes: Int,
    onCheckedChange: (Boolean) -> Unit,
    onMinutesChange: (Int) -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // 主行：开关 + 标题 + 副标题
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = cardTitleStyle)
                Text(subtitle, style = cardSubtitleStyle)
            }
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }

        // 展开区：三档滑块（30/45/60分钟）
        AnimatedVisibility(
            visible = checked,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                // 标签行 - 添加与滑块轨道相同的 padding 以对齐节点
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp), // 与滑块轨道 padding 匹配
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "30分钟", style = cardSubtitleStyle)
                    Text(text = "45分钟", style = cardSubtitleStyle)
                    Text(text = "60分钟", style = cardSubtitleStyle)
                }
                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { onMinutesChange(it.toInt()) },
                    valueRange = 30f..60f,
                    steps = 1 // 30, 45, 60 三个离散值
                )
            }
        }
    }
}

@Composable
fun FloatingEventRangeSlider(
    title: String,
    subtitle: String,
    eventRange: Int,
    onEventRangeChange: (Int) -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        // 标题行
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = cardTitleStyle)
                Text(subtitle, style = cardSubtitleStyle)
            }
        }

        // 滑块区域
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
        ) {
            // 标签行
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "全部日程", style = cardSubtitleStyle)
                Text(text = "今日日程", style = cardSubtitleStyle)
                Text(text = "今日+明日", style = cardSubtitleStyle)
            }
            Slider(
                value = eventRange.toFloat(),
                onValueChange = { onEventRangeChange(it.toInt()) },
                valueRange = 0f..2f,
                steps = 1
            )
        }
    }
}
