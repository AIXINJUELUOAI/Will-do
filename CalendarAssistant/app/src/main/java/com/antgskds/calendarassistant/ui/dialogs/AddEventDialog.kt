package com.antgskds.calendarassistant.ui.dialogs

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.EventType
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.ui.components.WheelDatePickerDialog
import com.antgskds.calendarassistant.ui.components.WheelReminderPickerDialog
import com.antgskds.calendarassistant.ui.components.WheelTimePickerDialog
import com.antgskds.calendarassistant.ui.theme.EventColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID

// 简单的提醒选项辅助
val REMINDER_OPTIONS = listOf(
    0 to "开始时", 5 to "5分钟前", 10 to "10分钟前", 15 to "15分钟前",
    30 to "30分钟前", 60 to "1小时前", 120 to "2小时前", 1440 to "1天前"
)

private const val DEFAULT_EVENT_DURATION_MINUTES = 60L
private const val AUTO_ADJUST_END_MESSAGE = "结束时间已自动调整为开始时间+1小时"

private data class EventDateTimeRange(
    val start: LocalDateTime,
    val end: LocalDateTime
)

private fun parseLocalTimeValue(
    value: String,
    formatter: DateTimeFormatter
): LocalTime? {
    return runCatching { LocalTime.parse(value, formatter) }
        .recoverCatching { LocalTime.parse(value) }
        .getOrNull()
}

private fun parseDateTimeValue(
    date: LocalDate,
    time: String,
    formatter: DateTimeFormatter
): LocalDateTime? {
    val parsedTime = parseLocalTimeValue(time, formatter) ?: return null
    return LocalDateTime.of(date, parsedTime)
}

private fun resolveInitialRange(
    eventToEdit: MyEvent?,
    fallbackStart: LocalDateTime,
    formatter: DateTimeFormatter
): EventDateTimeRange {
    if (eventToEdit == null) {
        return EventDateTimeRange(
            start = fallbackStart,
            end = fallbackStart.plusMinutes(DEFAULT_EVENT_DURATION_MINUTES)
        )
    }

    val parsedStart = parseDateTimeValue(eventToEdit.startDate, eventToEdit.startTime, formatter)
        ?: fallbackStart
    val parsedEnd = parseDateTimeValue(eventToEdit.endDate, eventToEdit.endTime, formatter)
        ?.takeIf { it.isAfter(parsedStart) }
        ?: parsedStart.plusMinutes(DEFAULT_EVENT_DURATION_MINUTES)

    return EventDateTimeRange(parsedStart, parsedEnd)
}

private fun resolveEndAfterStartChange(
    newStart: LocalDateTime,
    currentEnd: LocalDateTime,
    isEndTimeManuallySet: Boolean,
    followDurationMinutes: Long
): Pair<LocalDateTime, String?> {
    return if (!isEndTimeManuallySet) {
        newStart.plusMinutes(followDurationMinutes.coerceAtLeast(1L)) to null
    } else if (!currentEnd.isAfter(newStart)) {
        newStart.plusMinutes(DEFAULT_EVENT_DURATION_MINUTES) to AUTO_ADJUST_END_MESSAGE
    } else {
        currentEnd to null
    }
}

private fun resolveManualEndChange(
    start: LocalDateTime,
    candidateEnd: LocalDateTime
): Pair<LocalDateTime, String?> {
    return if (!candidateEnd.isAfter(start)) {
        start.plusMinutes(DEFAULT_EVENT_DURATION_MINUTES) to AUTO_ADJUST_END_MESSAGE
    } else {
        candidateEnd to null
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEventDialog(
    eventToEdit: MyEvent? = null,
    currentEventsCount: Int = 0,
    settings: MySettings = MySettings(),
    recurringNextOccurrenceText: String? = null,
    recurringEditHint: String? = null,
    visible: Boolean = true,
    onShowMessage: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (MyEvent) -> Unit
) {
    val context = LocalContext.current
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val defaultStartDateTime = remember {
        LocalDateTime.now().withSecond(0).withNano(0)
    }
    val initialRange = remember(eventToEdit?.id) {
        resolveInitialRange(eventToEdit, defaultStartDateTime, timeFormatter)
    }
    val initialAutoDurationMinutes = remember(initialRange) {
        Duration.between(initialRange.start, initialRange.end).toMinutes().coerceAtLeast(1L)
    }

    // 计算过滤后的提醒选项
    val filteredReminderOptions = remember(settings.isAdvanceReminderEnabled, settings.advanceReminderMinutes) {
        if (settings.isAdvanceReminderEnabled && settings.advanceReminderMinutes > 0) {
            REMINDER_OPTIONS.filter { it.first > settings.advanceReminderMinutes }
        } else {
            REMINDER_OPTIONS
        }
    }

    var title by remember { mutableStateOf(eventToEdit?.title ?: "") }
    var startDate by remember { mutableStateOf(initialRange.start.toLocalDate()) }
    var endDate by remember { mutableStateOf(initialRange.end.toLocalDate()) }
    var startTime by remember { mutableStateOf(initialRange.start.toLocalTime().format(timeFormatter)) }
    var endTime by remember { mutableStateOf(initialRange.end.toLocalTime().format(timeFormatter)) }
    var location by remember { mutableStateOf(eventToEdit?.location ?: "") }
    var desc by remember { mutableStateOf(eventToEdit?.description ?: "") }
    var eventType by remember { mutableStateOf(eventToEdit?.eventType ?: EventType.EVENT) }
    var eventTag by remember { mutableStateOf(eventToEdit?.tag ?: EventTags.GENERAL) }
    val reminders = remember { mutableStateListOf<Int>().apply { addAll(eventToEdit?.reminders ?: emptyList()) } }

    var sourceBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(eventToEdit, visible) {
        if (!visible) return@LaunchedEffect
        val path = eventToEdit?.sourceImagePath
        if (!path.isNullOrBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val file = File(path)
                    if (file.exists()) sourceBitmap = BitmapFactory.decodeFile(file.absolutePath)
                } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showReminderPicker by remember { mutableStateOf(false) }
    var autoDurationMinutes by remember { mutableStateOf(initialAutoDurationMinutes) }
    var isEndTimeManuallySet by remember { mutableStateOf(false) }

    fun applyDateTimeRange(start: LocalDateTime, end: LocalDateTime) {
        startDate = start.toLocalDate()
        startTime = start.toLocalTime().format(timeFormatter)
        endDate = end.toLocalDate()
        endTime = end.toLocalTime().format(timeFormatter)
    }

    if (!visible) return

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.85f).heightIn(max = 670.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(if (eventToEdit == null) "新增日程" else "编辑日程", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text("类型:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))

                    AssistChip(
                        onClick = {},
                        label = { Text("日程") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (eventTag == EventTags.GENERAL) MaterialTheme.colorScheme.primary else Color.Transparent,
                            labelColor = if (eventTag == EventTags.GENERAL) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ),
                        border = null
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text("取件") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (eventTag == EventTags.PICKUP) MaterialTheme.colorScheme.primary else Color.Transparent,
                            labelColor = if (eventTag == EventTags.PICKUP) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ),
                        border = null
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text("火车") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (eventTag == EventTags.TRAIN) MaterialTheme.colorScheme.primary else Color.Transparent,
                            labelColor = if (eventTag == EventTags.TRAIN) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ),
                        border = null
                    )
                    AssistChip(
                        onClick = {},
                        label = { Text("打车") },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (eventTag == EventTags.TAXI) MaterialTheme.colorScheme.primary else Color.Transparent,
                            labelColor = if (eventTag == EventTags.TAXI) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        ),
                        border = null
                    )
                }

                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("始", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(8.dp))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showStartDatePicker = true }, modifier = Modifier.weight(1.5f)) { Text(startDate.toString(), style = MaterialTheme.typography.bodyMedium) }
                        OutlinedButton(onClick = { showStartTimePicker = true }, modifier = Modifier.weight(1f)) { Text(startTime, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("终", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(8.dp))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showEndDatePicker = true }, modifier = Modifier.weight(1.5f)) { Text(endDate.toString(), style = MaterialTheme.typography.bodyMedium) }
                        OutlinedButton(onClick = { showEndTimePicker = true }, modifier = Modifier.weight(1f)) { Text(endTime, style = MaterialTheme.typography.bodyMedium) }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { showReminderPicker = true }.padding(vertical = 8.dp)
                ) {
                    Icon(Icons.Outlined.Notifications, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("添加提醒", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
                if (reminders.isNotEmpty()) {
                    FlowRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        reminders.forEach { mins ->
                            val label = REMINDER_OPTIONS.find { it.first == mins }?.second ?: "${mins}分钟前"
                            InputChip(selected = false, onClick = { reminders.remove(mins) }, label = { Text(label) }, trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) })
                        }
                    }
                }

                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("地点") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)

                if (sourceBitmap != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Image(bitmap = sourceBitmap!!.asImageBitmap(), contentDescription = "Source", modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.FillWidth)
                }

                if (recurringNextOccurrenceText != null || recurringEditHint != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("此日程为重复日程", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            if (recurringNextOccurrenceText != null) {
                                Text(
                                    "下一次提醒时间：$recurringNextOccurrenceText",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (recurringEditHint != null) {
                                Text(
                                    recurringEditHint,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (title.isNotBlank()) {
                            val finalStart = parseDateTimeValue(startDate, startTime, timeFormatter)
                            val finalEnd = parseDateTimeValue(endDate, endTime, timeFormatter)

                            if (finalStart == null || finalEnd == null) {
                                onShowMessage("时间格式无效，请重新选择")
                                return@Button
                            }

                            if (!finalEnd.isAfter(finalStart)) {
                                onShowMessage("结束时间必须晚于开始时间")
                                return@Button
                            }

                            val nextColor = if (EventColors.isNotEmpty()) EventColors[currentEventsCount % EventColors.size] else Color.Gray
                            val newEvent = MyEvent(
                                id = eventToEdit?.id ?: UUID.randomUUID().toString(),
                                title = title,
                                startDate = finalStart.toLocalDate(),
                                endDate = finalEnd.toLocalDate(),
                                startTime = finalStart.toLocalTime().format(timeFormatter),
                                endTime = finalEnd.toLocalTime().format(timeFormatter),
                                location = location, description = desc, color = eventToEdit?.color ?: nextColor,
                                isImportant = eventToEdit?.isImportant ?: false, sourceImagePath = eventToEdit?.sourceImagePath,
                                reminders = reminders.toList(), eventType = eventType, tag = eventTag
                            )
                            onConfirm(newEvent)
                        }
                    }) { Text("确定") }
                }
            }
        }
    }

    if (showStartDatePicker) WheelDatePickerDialog(startDate, { showStartDatePicker = false }) {
        val newStart = parseDateTimeValue(it, startTime, timeFormatter)
        val currentEnd = parseDateTimeValue(endDate, endTime, timeFormatter)
        if (newStart != null) {
            val safeCurrentEnd = currentEnd ?: newStart.plusMinutes(DEFAULT_EVENT_DURATION_MINUTES)
            val (resolvedEnd, message) = resolveEndAfterStartChange(
                newStart = newStart,
                currentEnd = safeCurrentEnd,
                isEndTimeManuallySet = isEndTimeManuallySet,
                followDurationMinutes = autoDurationMinutes
            )
            applyDateTimeRange(newStart, resolvedEnd)
            if (message != null) {
                onShowMessage(message)
            }
        }
        showStartDatePicker = false
    }
    if (showEndDatePicker) WheelDatePickerDialog(endDate, { showEndDatePicker = false }) {
        val currentStart = parseDateTimeValue(startDate, startTime, timeFormatter)
        val candidateEnd = parseDateTimeValue(it, endTime, timeFormatter)
        if (currentStart != null && candidateEnd != null) {
            val (resolvedEnd, message) = resolveManualEndChange(currentStart, candidateEnd)
            endDate = resolvedEnd.toLocalDate()
            endTime = resolvedEnd.toLocalTime().format(timeFormatter)
            if (message != null) {
                onShowMessage(message)
            }
        }
        isEndTimeManuallySet = true
        showEndDatePicker = false
    }
    if (showStartTimePicker) WheelTimePickerDialog(startTime, { showStartTimePicker = false }) {
        val newStart = parseDateTimeValue(startDate, it, timeFormatter)
        val currentEnd = parseDateTimeValue(endDate, endTime, timeFormatter)
        if (newStart != null) {
            val safeCurrentEnd = currentEnd ?: newStart.plusMinutes(DEFAULT_EVENT_DURATION_MINUTES)
            val (resolvedEnd, message) = resolveEndAfterStartChange(
                newStart = newStart,
                currentEnd = safeCurrentEnd,
                isEndTimeManuallySet = isEndTimeManuallySet,
                followDurationMinutes = autoDurationMinutes
            )
            applyDateTimeRange(newStart, resolvedEnd)
            if (message != null) {
                onShowMessage(message)
            }
        }
        showStartTimePicker = false
    }
    if (showEndTimePicker) WheelTimePickerDialog(endTime, { showEndTimePicker = false }) {
        val currentStart = parseDateTimeValue(startDate, startTime, timeFormatter)
        val candidateEnd = parseDateTimeValue(endDate, it, timeFormatter)
        if (currentStart != null && candidateEnd != null) {
            val (resolvedEnd, message) = resolveManualEndChange(currentStart, candidateEnd)
            endDate = resolvedEnd.toLocalDate()
            endTime = resolvedEnd.toLocalTime().format(timeFormatter)
            if (message != null) {
                onShowMessage(message)
            }
        }
        isEndTimeManuallySet = true
        showEndTimePicker = false
    }
    if (showReminderPicker) {
        WheelReminderPickerDialog(
            initialMinutes = 30,
            onDismiss = { showReminderPicker = false },
            onConfirm = { if (!reminders.contains(it)) reminders.add(it) },
            availableOptions = filteredReminderOptions  // 传入过滤后的选项
        )
    }
}
