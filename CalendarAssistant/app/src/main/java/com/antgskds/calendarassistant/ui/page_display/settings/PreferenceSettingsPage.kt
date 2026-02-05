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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.calendar.CalendarPermissionHelper
import com.antgskds.calendarassistant.data.repository.AppRepository
import com.antgskds.calendarassistant.service.receiver.DailySummaryReceiver
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

    // --- 字体样式优化 ---
    // 板块标题：Primary + ExtraBold
    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary
    )
    // 列表项标题：OnSurface + Medium
    val cardTitleStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
    )
    // 副标题：Grey + Transparent
    val cardSubtitleStyle = MaterialTheme.typography.bodySmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )
    // 右侧数值：Grey + Normal (不抢眼)
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
            scope.launch {
                viewModel.toggleCalendarSync(true)
                viewModel.manualSync()
                (context.applicationContext as? App)?.initCalendarObserver()
                snackbarHostState.showSnackbar("日历同步已开启")
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
                .padding(bottom = 80.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 显示板块
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
                        subtitle = "调整字体大小",
                        value = settings.uiSize.toFloat(),
                        onValueChange = { viewModel.updateUiSize(it.toInt()) },
                        valueRange = 1f..3f,
                        steps = 1,
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle,
                        cardValueStyle = cardValueStyle
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
                        title = "显示明日日程",
                        subtitle = "在今日日程列表底部预览明日安排",
                        checked = settings.showTomorrowEvents,
                        onCheckedChange = { viewModel.updatePreference(showTomorrow = it) },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                }
            }

            // 通知板块
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
                        title = "实况胶囊通知 (Beta)",
                        subtitle = "日程开始前显示灵动岛/胶囊",
                        checked = settings.isLiveCapsuleEnabled,
                        onCheckedChange = { isChecked ->
                            viewModel.updatePreference(liveCapsule = isChecked)
                            if (isChecked) showToast("请确保已开启无障碍权限", ToastType.INFO)
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

                    // 日程提前提醒设置项
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
                            // 只在打开开关时检测重复提醒
                            if (isChecked && settings.advanceReminderMinutes > 0) {
                                val hasDuplicate = events.any { event ->
                                    event.eventType != "temp" &&
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
                }
            }

            // 同步板块
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
                                    scope.launch {
                                        viewModel.toggleCalendarSync(true)
                                        viewModel.manualSync()
                                        (context.applicationContext as? App)?.initCalendarObserver()
                                        showToast("日历同步已开启")
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
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SwitchSettingItem(
                        title = "智能推荐",
                        subtitle = "基于历史数据在创建日程时显示推荐",
                        checked = settings.enableSmartRecommendation,
                        onCheckedChange = { viewModel.updateSmartRecommendation(it) },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp + bottomInset),
            snackbar = { data -> UniversalToast(message = data.visuals.message, type = currentToastType) }
        )
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要日历权限") },
            text = { Text("为了让您在系统日历中查看和管理课程与日程，需要授予应用读取和写入日历的权限。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
                        } else { emptyArray() }
                        calendarPermissionLauncher.launch(permissions)
                    }
                ) { Text("授予权限") }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) { Text("取消") }
            }
        )
    }
}

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
    cardValueStyle: TextStyle
) {
    val sizeLabels = mapOf(1f to "小", 2f to "中", 3f to "大")
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
            Text(
                text = sizeLabels[value] ?: "",
                style = cardValueStyle // 灰色
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

        // 展开区：三档滑块（10/20/30分钟）
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
                    Text(text = "10分钟", style = cardSubtitleStyle)
                    Text(text = "20分钟", style = cardSubtitleStyle)
                    Text(text = "30分钟", style = cardSubtitleStyle)
                }
                Slider(
                    value = minutes.toFloat(),
                    onValueChange = { onMinutesChange(it.toInt()) },
                    valueRange = 10f..30f,
                    steps = 1 // 10, 20, 30 三个离散值
                )
            }
        }
    }
}