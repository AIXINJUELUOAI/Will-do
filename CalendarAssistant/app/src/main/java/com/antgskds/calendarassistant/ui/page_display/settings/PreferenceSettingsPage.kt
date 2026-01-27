package com.antgskds.calendarassistant.ui.page_display.settings

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
import com.antgskds.calendarassistant.service.receiver.DailySummaryReceiver
import com.antgskds.calendarassistant.ui.components.ToastType
import com.antgskds.calendarassistant.ui.components.UniversalToast
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch

@Composable
fun PreferenceSettingsPage(
    viewModel: SettingsViewModel
) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var currentToastType by remember { mutableStateOf(ToastType.INFO) }

    fun showToast(message: String, type: ToastType = ToastType.INFO) {
        currentToastType = type
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    // 使用 float 状态来控制 Slider 的流畅滑动
    var delayMs by remember(settings.screenshotDelayMs) { mutableFloatStateOf(settings.screenshotDelayMs.toFloat()) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
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

            // 【新增】只有当开启实况胶囊时，才显示聚合开关（可选优化，或者一直显示）
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

            // 隐藏识别延迟设置
//        HorizontalDivider()
//        Text("智能识别", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
//        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
//            Text("截图识别延迟: ${delayMs.toLong()}ms", style = MaterialTheme.typography.bodyMedium)
//            Slider(
//                value = delayMs,
//                onValueChange = { delayMs = it },
//                onValueChangeFinished = {
//                    // 【修复】松手时保存设置
//                    viewModel.updateScreenshotDelay(delayMs.toLong())
//                },
//                valueRange = 200f..2000f,
//                steps = 17 // (2000-200)/100 = 18个点，steps = 17
//            )
//            Text(
//                text = "针对不同手机截图动画时间调整，避免截到通知栏",
//                style = MaterialTheme.typography.bodySmall,
//                color = Color.Gray
//            )
//        }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            snackbar = { data ->
                UniversalToast(message = data.visuals.message, type = currentToastType)
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
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
