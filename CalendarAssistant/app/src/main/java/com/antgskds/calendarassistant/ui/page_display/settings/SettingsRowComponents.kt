package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.vector.ImageVector
import com.antgskds.calendarassistant.ui.haptic.HapticValueChangeEffect
import com.antgskds.calendarassistant.ui.haptic.sliderHapticBucket
import androidx.compose.foundation.shape.RoundedCornerShape
import com.antgskds.calendarassistant.data.model.RecognitionMode
import com.antgskds.calendarassistant.ui.components.AppAlertDialog
import com.antgskds.calendarassistant.ui.components.CenteredDialogTitle
import com.antgskds.calendarassistant.ui.components.WheelPicker
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import kotlin.math.roundToInt

/**
 * 设置页通用 UI 行组件（公共组件库）。
 *
 * 约束：这里只放“纯展示/输入”组件——只接收 value/onChange/title/description/样式等参数，
 * 不读写 MySettings，不依赖 App / SettingsOperationApi / Repository / Center 等业务层。
 * 带业务流程的（定位选择、API Key 校验、权限申请、draft 保存、通知/胶囊预览）不要放进来。
 *
 * 与各设置页同包（com.antgskds.calendarassistant.ui.page_display.settings），
 * 因此调用方无需新增 import；从 PreferenceSettingsPage 抽出以降低其行数、消除组件散落。
 */

/** 标题 + 说明 + 开关 行。 */
@Composable
fun SwitchSettingItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle
) {
    val haptics = rememberAppHaptics()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = cardTitleStyle)
            Text(subtitle, style = cardSubtitleStyle)
        }
        Switch(
            checked = checked,
            onCheckedChange = {
                haptics.selection()
                onCheckedChange(it)
            }
        )
    }
}

/** 标题 + 说明 + 左/右侧二选一 行。 */
@Composable
fun SideChoiceSettingItem(
    title: String,
    subtitle: String,
    selectedSide: String,
    onSideSelected: (String) -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle
) {
    val haptics = rememberAppHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = cardTitleStyle)
            Text(subtitle, style = cardSubtitleStyle)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val isLeft = selectedSide == "LEFT"
            if (isLeft) {
                Button(onClick = { haptics.selection(); onSideSelected("LEFT") }) {
                    Text("左侧")
                }
            } else {
                OutlinedButton(onClick = { haptics.selection(); onSideSelected("LEFT") }) {
                    Text("左侧")
                }
            }

            if (isLeft) {
                OutlinedButton(onClick = { haptics.selection(); onSideSelected("RIGHT") }) {
                    Text("右侧")
                }
            } else {
                Button(onClick = { haptics.selection(); onSideSelected("RIGHT") }) {
                    Text("右侧")
                }
            }
        }
    }
}

/** 标题 + 说明 + 可点击动作行（可选尾部图标，否则显示 value 文本）。 */
@Composable
fun ActionSettingItem(
    title: String,
    subtitle: String,
    value: String,
    icon: ImageVector? = null,
    enabled: Boolean,
    hapticOnClick: Boolean = true,
    onClick: () -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
    cardValueStyle: TextStyle
) {
    val haptics = rememberAppHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                if (hapticOnClick) haptics.selection()
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = cardTitleStyle)
            Text(subtitle, style = cardSubtitleStyle)
        }
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(value, style = cardValueStyle)
        }
    }
}

/** 标题 + 说明 + 滑杆 行（带触感反馈；showValueAsNumber 时显示数值，否则显示大小标签）。 */
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
    HapticValueChangeEffect(valueKey = sliderHapticBucket(value, valueRange, steps))
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

/** 标题 + 说明 + 开关 + 展开三档滑杆（长按音量动作：识屏/悬浮窗/语音）行。 */
@Composable
fun VolumeLongPressSettingItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    action: Int,
    onCheckedChange: (Boolean) -> Unit,
    onActionChange: (Int) -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle
) {
    val normalizedAction = action.coerceIn(1, 3)
    HapticValueChangeEffect(valueKey = normalizedAction)
    val haptics = rememberAppHaptics()
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
            Switch(
                checked = checked,
                onCheckedChange = {
                    haptics.selection()
                    onCheckedChange(it)
                }
            )
        }

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "识屏", style = cardSubtitleStyle)
                    Text(text = "悬浮窗", style = cardSubtitleStyle)
                    Text(text = "语音", style = cardSubtitleStyle)
                }
                Slider(
                    value = normalizedAction.toFloat(),
                    onValueChange = { onActionChange(it.roundToInt().coerceIn(1, 3)) },
                    valueRange = 1f..3f,
                    steps = 1
                )
            }
        }
    }
}

/** 标题 + 说明 + 开关 + 展开三档滑杆（提前提醒 30/45/60 分钟）行。 */
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
    HapticValueChangeEffect(valueKey = minutes)
    val haptics = rememberAppHaptics()
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
            Switch(
                checked = checked,
                onCheckedChange = {
                    haptics.selection()
                    onCheckedChange(it)
                }
            )
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

/** 日程默认持续时间：设置行 + 选择对话框 + 选项表/格式化（簇内私有）。 */
private data class EventDurationOption(
    val minutes: Int,
    val label: String
)

private val EVENT_DURATION_OPTIONS = listOf(
    EventDurationOption(60, "1小时"),
    EventDurationOption(120, "2小时"),
    EventDurationOption(180, "3小时"),
    EventDurationOption(360, "6小时"),
    EventDurationOption(1440, "24小时"),
    EventDurationOption(-1, "今天结束")
)

private fun formatEventDuration(minutes: Int): String {
    return EVENT_DURATION_OPTIONS.firstOrNull { it.minutes == minutes }?.label ?: "1小时"
}

@Composable
fun EventDurationSettingItem(
    title: String,
    subtitle: String,
    durationMinutes: Int,
    onClick: () -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
    cardValueStyle: TextStyle
) {
    ActionSettingItem(
        title = title,
        subtitle = subtitle,
        value = formatEventDuration(durationMinutes),
        enabled = true,
        onClick = onClick,
        cardTitleStyle = cardTitleStyle,
        cardSubtitleStyle = cardSubtitleStyle,
        cardValueStyle = cardValueStyle
    )
}

@Composable
fun EventDurationPickerDialog(
    selectedDuration: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val defaultIndex = EVENT_DURATION_OPTIONS.indexOfFirst { it.minutes == selectedDuration }
        .takeIf { it >= 0 } ?: 0
    var selectedIndex by remember(selectedDuration) { mutableIntStateOf(defaultIndex) }
    val haptics = rememberAppHaptics()

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { CenteredDialogTitle("日程默认持续时间") },
        text = {
            WheelPicker(
                items = EVENT_DURATION_OPTIONS.map { it.label },
                initialIndex = defaultIndex,
                onSelectionChanged = { selectedIndex = it }
            )
        },
        confirmButton = {
            TextButton(onClick = { haptics.confirm(); onConfirm(EVENT_DURATION_OPTIONS[selectedIndex].minutes) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = { haptics.click(); onDismiss() }) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

/** 每日提醒/明日预告时间：设置行 + 时间滚轮选择对话框。 */
private fun normalizeMinuteOfDay(minuteOfDay: Int): Int = minuteOfDay.coerceIn(0, 23 * 60 + 59)

private fun formatTimeOfDay(minuteOfDay: Int): String {
    val safeMinuteOfDay = normalizeMinuteOfDay(minuteOfDay)
    return "%02d:%02d".format(safeMinuteOfDay / 60, safeMinuteOfDay % 60)
}

@Composable
fun DailySummaryTimeSettingItem(
    title: String,
    subtitle: String,
    minuteOfDay: Int,
    onClick: () -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
    cardValueStyle: TextStyle
) {
    ActionSettingItem(
        title = title,
        subtitle = subtitle,
        value = formatTimeOfDay(minuteOfDay),
        enabled = true,
        onClick = onClick,
        cardTitleStyle = cardTitleStyle,
        cardSubtitleStyle = cardSubtitleStyle,
        cardValueStyle = cardValueStyle
    )
}

@Composable
fun DailySummaryTimePickerDialog(
    title: String,
    selectedMinuteOfDay: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val safeMinuteOfDay = normalizeMinuteOfDay(selectedMinuteOfDay)
    val initialHour = safeMinuteOfDay / 60
    val initialMinute = safeMinuteOfDay % 60
    var selectedHour by remember(selectedMinuteOfDay) { mutableIntStateOf(initialHour) }
    var selectedMinute by remember(selectedMinuteOfDay) { mutableIntStateOf(initialMinute) }
    val haptics = rememberAppHaptics()

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { CenteredDialogTitle(title) },
        text = {
            DailySummaryTimeWheelPicker(
                initialHour = initialHour,
                initialMinute = initialMinute,
                onTimeChanged = { hour, minute ->
                    selectedHour = hour
                    selectedMinute = minute
                }
            )
        },
        confirmButton = {
            TextButton(onClick = { haptics.confirm(); onConfirm(selectedHour * 60 + selectedMinute) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = { haptics.click(); onDismiss() }) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

@Composable
private fun DailySummaryTimeWheelPicker(
    initialHour: Int,
    initialMinute: Int,
    onTimeChanged: (Int, Int) -> Unit
) {
    var selectedHour by remember(initialHour) { mutableIntStateOf(initialHour.coerceIn(0, 23)) }
    var selectedMinute by remember(initialMinute) { mutableIntStateOf(initialMinute.coerceIn(0, 59)) }
    val hours = remember { (0..23).map { "%02d".format(it) } }
    val minutes = remember { (0..59).map { "%02d".format(it) } }

    LaunchedEffect(selectedHour, selectedMinute) {
        onTimeChanged(selectedHour, selectedMinute)
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        WheelPicker(
            items = hours,
            initialIndex = selectedHour,
            modifier = Modifier.weight(1f),
            onSelectionChanged = { selectedHour = it }
        )
        Text(
            text = ":",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        WheelPicker(
            items = minutes,
            initialIndex = selectedMinute,
            modifier = Modifier.weight(1f),
            onSelectionChanged = { selectedMinute = it }
        )
    }
}

/** 识别模式：设置行 + 滚轮选择对话框。 */
@Composable
fun RecognitionModeSettingItem(
    mode: Int,
    onClick: () -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
    cardValueStyle: TextStyle
) {
    ActionSettingItem(
        title = "识别模式",
        subtitle = RecognitionMode.description(mode),
        value = RecognitionMode.label(mode),
        enabled = true,
        onClick = onClick,
        cardTitleStyle = cardTitleStyle,
        cardSubtitleStyle = cardSubtitleStyle,
        cardValueStyle = cardValueStyle
    )
}

@Composable
fun RecognitionModePickerDialog(
    selectedMode: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    val modes = remember { RecognitionMode.ALL }
    val normalized = RecognitionMode.normalize(selectedMode)
    val defaultIndex = modes.indexOf(normalized).takeIf { it >= 0 } ?: 0
    var selectedIndex by remember(selectedMode) { mutableIntStateOf(defaultIndex) }
    val haptics = rememberAppHaptics()

    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { CenteredDialogTitle("识别模式") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                WheelPicker(
                    items = modes.map { RecognitionMode.label(it) },
                    initialIndex = defaultIndex,
                    onSelectionChanged = { selectedIndex = it }
                )
                Text(
                    text = RecognitionMode.description(modes[selectedIndex]),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { haptics.confirm(); onConfirm(modes[selectedIndex]) }) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = { haptics.click(); onDismiss() }) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}
