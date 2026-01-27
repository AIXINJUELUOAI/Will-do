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
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.ui.components.WheelDatePickerDialog
import com.antgskds.calendarassistant.ui.components.WheelReminderPickerDialog
import com.antgskds.calendarassistant.ui.components.WheelTimePickerDialog
import com.antgskds.calendarassistant.ui.theme.EventColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

// 简单的提醒选项辅助
val REMINDER_OPTIONS = listOf(
    0 to "开始时", 5 to "5分钟前", 10 to "10分钟前", 15 to "15分钟前",
    30 to "30分钟前", 60 to "1小时前", 120 to "2小时前", 1440 to "1天前"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEventDialog(
    eventToEdit: MyEvent? = null,
    currentEventsCount: Int = 0,
    onDismiss: () -> Unit,
    onConfirm: (MyEvent) -> Unit
) {
    val context = LocalContext.current
    val now = LocalDateTime.now()
    val defaultEnd = now.plusHours(1)
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    var title by remember { mutableStateOf(eventToEdit?.title ?: "") }
    var startDate by remember { mutableStateOf(eventToEdit?.startDate ?: now.toLocalDate()) }
    var endDate by remember { mutableStateOf(eventToEdit?.endDate ?: defaultEnd.toLocalDate()) }
    var startTime by remember { mutableStateOf(eventToEdit?.startTime ?: now.format(timeFormatter)) }
    var endTime by remember { mutableStateOf(eventToEdit?.endTime ?: defaultEnd.format(timeFormatter)) }
    var location by remember { mutableStateOf(eventToEdit?.location ?: "") }
    var desc by remember { mutableStateOf(eventToEdit?.description ?: "") }
    var eventType by remember { mutableStateOf(eventToEdit?.eventType ?: "event") }
    val reminders = remember { mutableStateListOf<Int>().apply { addAll(eventToEdit?.reminders ?: emptyList()) } }

    var sourceBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(eventToEdit) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("类型:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = eventType == "event", onClick = { eventType = "event" }, label = { Text("日程") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = eventType == "temp", onClick = { eventType = "temp" }, label = { Text("取件/取餐") })
                }

                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("始", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showStartDatePicker = true }, modifier = Modifier.weight(1.5f)) { Text(startDate.toString(), fontSize = 13.sp) }
                        OutlinedButton(onClick = { showStartTimePicker = true }, modifier = Modifier.weight(1f)) { Text(startTime, fontSize = 13.sp) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("终", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(8.dp))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showEndDatePicker = true }, modifier = Modifier.weight(1.5f)) { Text(endDate.toString(), fontSize = 13.sp) }
                        OutlinedButton(onClick = { showEndTimePicker = true }, modifier = Modifier.weight(1f)) { Text(endTime, fontSize = 13.sp) }
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
                OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text(if (eventType == "temp") "取件码/取餐码" else "备注") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)

                if (sourceBitmap != null) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Image(bitmap = sourceBitmap!!.asImageBitmap(), contentDescription = "Source", modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.FillWidth)
                }
                }

                Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (title.isNotBlank()) {
                            val nextColor = if (EventColors.isNotEmpty()) EventColors[currentEventsCount % EventColors.size] else Color.Gray
                            val newEvent = MyEvent(
                                id = eventToEdit?.id ?: UUID.randomUUID().toString(),
                                title = title, startDate = startDate, endDate = endDate, startTime = startTime, endTime = endTime,
                                location = location, description = desc, color = eventToEdit?.color ?: nextColor,
                                isImportant = eventToEdit?.isImportant ?: false, sourceImagePath = eventToEdit?.sourceImagePath,
                                reminders = reminders.toList(), eventType = eventType
                            )
                            onConfirm(newEvent)
                        }
                    }) { Text("确定") }
                }
            }
        }
    }

    if (showStartDatePicker) WheelDatePickerDialog(startDate, { showStartDatePicker = false }) { startDate = it; endDate = it; showStartDatePicker = false }
    if (showEndDatePicker) WheelDatePickerDialog(endDate, { showEndDatePicker = false }) { endDate = it; showEndDatePicker = false }
    if (showStartTimePicker) WheelTimePickerDialog(startTime, { showStartTimePicker = false }) {
        startTime = it
        // 计算结束时间为开始时间+1小时
        val startParts = it.split(":")
        val startHour = startParts[0].toIntOrNull() ?: 0
        val endHour = (startHour + 1) % 24
        val startMinute = startParts.getOrElse(1) { "00" }
        endTime = String.format("%02d:%s", endHour, startMinute)
        showStartTimePicker = false
    }
    if (showEndTimePicker) WheelTimePickerDialog(endTime, { showEndTimePicker = false }) { endTime = it; showEndTimePicker = false }
    if (showReminderPicker) {
        WheelReminderPickerDialog(30, { showReminderPicker = false }) { if (!reminders.contains(it)) reminders.add(it) }
    }
}