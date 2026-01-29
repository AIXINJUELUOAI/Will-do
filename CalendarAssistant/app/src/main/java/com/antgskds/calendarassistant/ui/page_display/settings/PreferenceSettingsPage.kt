package com.antgskds.calendarassistant.ui.page_display.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.calendar.CalendarPermissionHelper
import com.antgskds.calendarassistant.service.receiver.DailySummaryReceiver
import com.antgskds.calendarassistant.ui.components.ToastType
import com.antgskds.calendarassistant.ui.components.UniversalToast
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import java.util.Date

@Composable
fun PreferenceSettingsPage(
    viewModel: SettingsViewModel
) {
    val settings by viewModel.settings.collectAsState()
    val syncStatus by viewModel.syncStatus.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var currentToastType by remember { mutableStateOf(ToastType.INFO) }

    // 获取底部导航栏高度
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

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

                // 立即初始化观察者，无需重启
                (context.applicationContext as? App)?.initCalendarObserver()

                snackbarHostState.showSnackbar("日历同步已开启")
            }
        } else {
            // 权限被拒绝，显示带 "去设置" 的 Snackbar
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = "需要日历权限才能使用同步功能",
                    actionLabel = "去设置",
                    duration = SnackbarDuration.Long
                )
                if (result == SnackbarResult.ActionPerformed) {
                    // 跳转到应用设置页面
                    try {
                        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        // 降级：跳转到系统设置页面
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }
            }
        }
    }

    fun showToast(message: String, type: ToastType = ToastType.INFO) {
        currentToastType = type
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    // 使用 float 状态来控制 Slider 的流畅滑动
    var delayMs by remember(settings.screenshotDelayMs) { mutableFloatStateOf(settings.screenshotDelayMs.toFloat()) }

    // 格式化上次同步时间（使用系统格式化器，避免重复创建 SimpleDateFormat）
    val lastSyncTimeText = remember(syncStatus.lastSyncTime) {
        if (syncStatus.lastSyncTime > 0) {
            DateFormat.getDateFormat(context).format(Date(syncStatus.lastSyncTime)) +
            " " + android.text.format.DateFormat.getTimeFormat(context).format(Date(syncStatus.lastSyncTime))
        } else {
            "未同步"
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp)
                // 修改：确保内容不被底部 Snackbar 和 小白条 遮挡
                .padding(bottom = 80.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("显示", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            SliderSettingItem(
                title = "界面大小",
                subtitle = "调整字体大小",
                value = settings.uiSize.toFloat(),
                onValueChange = { viewModel.updateUiSize(it.toInt()) },
                valueRange = 1f..3f,
                steps = 1
            )
            SwitchSettingItem(
                title = "显示明日日程",
                subtitle = "在今日日程列表底部预览明日安排",
                checked = settings.showTomorrowEvents,
                onCheckedChange = { viewModel.updatePreference(showTomorrow = it) }
            )

            HorizontalDivider()
            Text("通知", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            SwitchSettingItem(
                title = "每日日程提醒",
                subtitle = "早06:00和晚22:00推送汇总",
                checked = settings.isDailySummaryEnabled,
                onCheckedChange = { isChecked ->
                    viewModel.updatePreference(dailySummary = isChecked)
                    if (isChecked) DailySummaryReceiver.schedule(context)
                }
            )
            SwitchSettingItem(
                title = "实况胶囊通知 (Beta)",
                subtitle = "日程开始前显示灵动岛/胶囊",
                checked = settings.isLiveCapsuleEnabled,
                onCheckedChange = { isChecked ->
                    viewModel.updatePreference(liveCapsule = isChecked)
                    if (isChecked) showToast("请确保已开启无障碍权限", ToastType.INFO)
                }
            )

            // 只有当开启实况胶囊时，才显示聚合开关
            if (settings.isLiveCapsuleEnabled) {
                SwitchSettingItem(
                    title = "取件码聚合 (Beta)",
                    subtitle = "当有多个取件码时合并显示为一个胶囊",
                    checked = settings.isPickupAggregationEnabled,
                    onCheckedChange = { isChecked ->
                        viewModel.updatePreference(pickupAggregation = isChecked)
                    }
                )
            }

            // ==================== 日历同步 ====================
            HorizontalDivider()
            Text("同步", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)

            SwitchSettingItem(
                title = "日历同步",
                subtitle = "将课程和日程同步到系统日历",
                checked = syncStatus.isEnabled,
                onCheckedChange = { isChecked ->
                    if (isChecked) {
                        // 检查权限
                        if (CalendarPermissionHelper.hasAllPermissions(context)) {
                            scope.launch {
                                viewModel.toggleCalendarSync(true)
                                viewModel.manualSync()

                                // 确保观察者已启动（App.kt 中有防重复检查，所以这里调用是安全的）
                                (context.applicationContext as? App)?.initCalendarObserver()

                                showToast("日历同步已开启")
                            }
                        } else {
                            // 显示权限说明对话框
                            showPermissionDialog = true
                        }
                    } else {
                        viewModel.toggleCalendarSync(false)
                        showToast("日历同步已关闭")
                    }
                }
            )

            // 显示同步状态
            if (syncStatus.isEnabled) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 4.dp, bottom = 8.dp)
                ) {
                    Text(
                        text = "上次同步: $lastSyncTimeText",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "已同步 ${syncStatus.mappedEventCount} 个事件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 手动同步按钮
                    var isSyncing by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            if (!isSyncing) {
                                scope.launch {
                                    isSyncing = true
                                    val result = viewModel.manualSync()
                                    isSyncing = false
                                    if (result.isSuccess) {
                                        showToast("同步成功", ToastType.SUCCESS)
                                    } else {
                                        showToast("同步失败: ${result.exceptionOrNull()?.message}", ToastType.ERROR)
                                    }
                                }
                            }
                        },
                        enabled = !isSyncing,
                        modifier = Modifier.padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(if (isSyncing) "同步中..." else "立即同步")
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding() // 修改：避让小白条
                .padding(bottom = 32.dp),
            snackbar = { data ->
                UniversalToast(message = data.visuals.message, type = currentToastType)
            }
        )
    }

    // 权限请求对话框
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("需要日历权限") },
            text = {
                Text("为了让您在系统日历中查看和管理课程与日程，需要授予应用读取和写入日历的权限。")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showPermissionDialog = false
                        // 请求日历权限
                        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            arrayOf(
                                Manifest.permission.READ_CALENDAR,
                                Manifest.permission.WRITE_CALENDAR
                            )
                        } else {
                            emptyArray()
                        }
                        calendarPermissionLauncher.launch(permissions)
                    }
                ) {
                    Text("授予权限")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun SwitchSettingItem(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    steps: Int
) {
    val sizeLabels = mapOf(1f to "小", 2f to "中", 3f to "大")
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = sizeLabels[value] ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
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
