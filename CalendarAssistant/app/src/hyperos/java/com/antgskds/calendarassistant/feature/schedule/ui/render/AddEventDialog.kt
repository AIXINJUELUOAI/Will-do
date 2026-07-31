package com.antgskds.calendarassistant.feature.schedule.ui.render

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.app.ui.theme.material.resolveEventColors
import com.antgskds.calendarassistant.feature.schedule.application.model.EditDraft
import com.antgskds.calendarassistant.feature.schedule.application.model.EventPatch
import com.antgskds.calendarassistant.feature.schedule.data.attachment.EventAttachmentManager
import com.antgskds.calendarassistant.feature.schedule.domain.model.EventAttachment
import com.antgskds.calendarassistant.feature.schedule.domain.model.EventTags
import com.antgskds.calendarassistant.feature.schedule.domain.model.RepeatSpec
import com.antgskds.calendarassistant.feature.recognition.domain.rule.RecognitionRuleCatalog
import com.antgskds.calendarassistant.feature.schedule.domain.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.shared.ui.material.component.WheelDatePickerDialog
import com.antgskds.calendarassistant.shared.ui.material.component.WheelReminderPickerDialog
import com.antgskds.calendarassistant.shared.ui.material.component.WheelTimePickerDialog
import com.antgskds.calendarassistant.shared.util.stripSourceImageMarkers
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowBottomSheet

private const val DefaultDurationMinutes = 60L

private val reminderOptions = listOf(
    0 to "开始时",
    5 to "5分钟前",
    10 to "10分钟前",
    15 to "15分钟前",
    30 to "30分钟前",
    60 to "1小时前",
    120 to "2小时前",
    1440 to "1天前",
)

private val eventTypes = listOf(
    EventTags.GENERAL to "日程",
    EventTags.TRAIN to "列车",
    EventTags.FLIGHT to "航班",
    EventTags.TAXI to "打车",
    EventTags.PICKUP to "取件",
    EventTags.FOOD to "取餐",
    EventTags.TICKET to "取票",
    EventTags.SENDER to "寄件",
)

private val structuredFields = mapOf(
    EventTags.TRAIN to listOf("车次", "检票口", "座位号"),
    EventTags.FLIGHT to listOf("航班号", "登机口", "座位号"),
    EventTags.TAXI to listOf("颜色", "车型", "车牌"),
    EventTags.PICKUP to listOf("取件码", "品牌", "位置"),
    EventTags.FOOD to listOf("取餐码", "品牌", "位置"),
    EventTags.TICKET to listOf("取票码", "品牌", "位置"),
    EventTags.SENDER to listOf("寄件码", "品牌", "地点"),
)

private fun parseStructuredValues(
    tag: String,
    description: String,
): List<String> {
    val labels = structuredFields[tag] ?: return emptyList()
    val payload = RuleMatchingEngine.resolvePayload(description, tag)?.payload.orEmpty()
    return RuleMatchingEngine.splitFields(payload, labels.size)
}

@Composable
fun AddEventDialog(
    editDraft: EditDraft? = null,
    currentEventsCount: Int = 0,
    settings: MySettings = MySettings(),
    visible: Boolean = true,
    attachments: List<EventAttachment> = emptyList(),
    onAddAttachment: (Uri) -> Unit = {},
    onAddPendingAttachment: (Uri, String) -> Unit = { _, _ -> },
    onOpenAttachment: (EventAttachment) -> Unit = {},
    onDeleteAttachment: (EventAttachment) -> Unit = {},
    onShowMessage: (String) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (EventPatch) -> Unit,
) {
    val draftKey = editDraft?.hashCode() ?: 0
    val formatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val initialStart = remember(draftKey) {
        editDraft?.let { LocalDateTime.of(it.startDate, it.startTime) }
            ?: LocalDateTime.now().withSecond(0).withNano(0)
    }
    val initialEnd = remember(draftKey) {
        editDraft?.let { LocalDateTime.of(it.endDate, it.endTime) }
            ?: initialStart.plusMinutes(DefaultDurationMinutes)
    }
    val zone = remember { ZoneId.systemDefault() }
    val pendingAttachmentKey = remember(draftKey) {
        EventAttachmentManager.eventKey(
            title = editDraft?.title.orEmpty(),
            startTS = initialStart.atZone(zone).toEpochSecond(),
            endTS = initialEnd.atZone(zone).toEpochSecond(),
            timeZone = zone.id,
        )
    }

    var title by remember(draftKey) { mutableStateOf(editDraft?.title.orEmpty()) }
    var startDate by remember(draftKey) { mutableStateOf(initialStart.toLocalDate()) }
    var startTime by remember(draftKey) { mutableStateOf(initialStart.toLocalTime().format(formatter)) }
    var endDate by remember(draftKey) { mutableStateOf(initialEnd.toLocalDate()) }
    var endTime by remember(draftKey) { mutableStateOf(initialEnd.toLocalTime().format(formatter)) }
    var location by remember(draftKey) { mutableStateOf(editDraft?.location.orEmpty()) }
    var description by remember(draftKey) {
        mutableStateOf(stripSourceImageMarkers(editDraft?.description.orEmpty()))
    }
    var eventTag by remember(draftKey) { mutableStateOf(editDraft?.tag ?: EventTags.GENERAL) }
    val structuredValues = remember(draftKey) {
        mutableStateListOf<String>().apply {
            addAll(parseStructuredValues(editDraft?.tag ?: EventTags.GENERAL, editDraft?.description.orEmpty()))
        }
    }
    var repeatSpec by remember(draftKey) { mutableStateOf(RepeatSpec.fromRRule(editDraft?.rrule.orEmpty())) }
    val reminders = remember(draftKey) {
        mutableStateListOf<Int>().apply { addAll(editDraft?.reminders.orEmpty()) }
    }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showReminderPicker by remember { mutableStateOf(false) }
    var showRepeatPicker by remember { mutableStateOf(false) }

    val attachmentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            if (editDraft?.eventId != null) onAddAttachment(uri)
            else onAddPendingAttachment(uri, pendingAttachmentKey)
        }
    }
    val typeIndex = eventTypes.indexOfFirst { it.first == eventTag }.coerceAtLeast(0)
    val colors = remember(settings.eventColorPaletteHex) {
        resolveEventColors(settings.eventColorPaletteHex)
    }
    val childPickerVisible = showStartDatePicker || showStartTimePicker ||
        showEndDatePicker || showEndTimePicker || showReminderPicker || showRepeatPicker

    WindowBottomSheet(
        show = visible,
        title = if (editDraft == null) "新增日程" else "编辑日程",
        onDismissRequest = onDismiss,
        allowDismiss = !childPickerVisible,
        enableNestedScroll = true,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            editDraft?.editHint?.takeIf { it.isNotBlank() }?.let { hint ->
                Text(
                    text = hint,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }

            Card(insideMargin = PaddingValues(0.dp)) {
                WindowDropdownPreference(
                    items = eventTypes.map { it.second },
                    selectedIndex = typeIndex,
                    title = "日程类型",
                    summary = "选择通知展示和解析方式",
                    onSelectedIndexChange = {
                        eventTag = eventTypes[it].first
                        structuredValues.clear()
                        structuredValues.addAll(List(structuredFields[eventTag]?.size ?: 0) { "" })
                    },
                )
            }

            TextField(
                value = title,
                onValueChange = { title = it },
                label = "标题",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            EventDateTimeRow(
                label = "开始",
                date = startDate.toString(),
                time = startTime,
                onDateClick = { showStartDatePicker = true },
                onTimeClick = { showStartTimePicker = true },
            )
            EventDateTimeRow(
                label = "结束",
                date = endDate.toString(),
                time = endTime,
                onDateClick = { showEndDatePicker = true },
                onTimeClick = { showEndTimePicker = true },
            )

            Card(insideMargin = PaddingValues(0.dp)) {
                ArrowPreference(
                    title = "提醒",
                    summary = reminders.takeIf { it.isNotEmpty() }
                        ?.joinToString("、") { minutes -> reminderOptions.firstOrNull { it.first == minutes }?.second ?: "${minutes}分钟前" }
                        ?: "未设置",
                    endActions = {
                        if (reminders.isNotEmpty()) {
                            IconButton(onClick = { reminders.clear() }) {
                                Icon(
                                    imageVector = MiuixIcons.Normal.Delete,
                                    contentDescription = "清除提醒",
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                                )
                            }
                        }
                    },
                    onClick = { showReminderPicker = true },
                )
                ArrowPreference(
                    title = "重复",
                    summary = repeatSpec?.summary() ?: "不重复",
                    onClick = { showRepeatPicker = true },
                )
            }

            TextField(
                value = location,
                onValueChange = { location = it },
                label = "地点",
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val activeStructuredFields = structuredFields[eventTag]
            if (activeStructuredFields == null) {
                TextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "备注",
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                activeStructuredFields.forEachIndexed { index, fieldLabel ->
                    TextField(
                        value = structuredValues.getOrElse(index) { "" },
                        onValueChange = { value ->
                            while (structuredValues.size <= index) structuredValues.add("")
                            structuredValues[index] = value
                        },
                        label = fieldLabel,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Card(insideMargin = PaddingValues(0.dp)) {
                ArrowPreference(
                    title = "附件",
                    summary = if (attachments.isEmpty()) "添加图片或文件" else "${attachments.size} 个附件",
                    startAction = {
                        Icon(
                            imageVector = MiuixIcons.Normal.Add,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(end = 12.dp)
                                .size(22.dp),
                        )
                    },
                    onClick = { attachmentPicker.launch(arrayOf("*/*")) },
                )
                attachments.forEach { attachment ->
                    AttachmentPreference(
                        attachment = attachment,
                        onOpen = { onOpenAttachment(attachment) },
                        onDelete = { onDeleteAttachment(attachment) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("取消")
                }
                Button(
                    onClick = {
                        val start = runCatching {
                            LocalDateTime.of(startDate, java.time.LocalTime.parse(startTime, formatter))
                        }.getOrNull()
                        val end = runCatching {
                            LocalDateTime.of(endDate, java.time.LocalTime.parse(endTime, formatter))
                        }.getOrNull()
                        when {
                            title.isBlank() -> onShowMessage("请填写标题")
                            start == null || end == null -> onShowMessage("时间格式无效，请重新选择")
                            !end.isAfter(start) -> onShowMessage("结束时间必须晚于开始时间")
                            else -> {
                                val reminderList = reminders.toList()
                                val nextColor = colors.getOrNull(currentEventsCount % colors.size.coerceAtLeast(1))
                                    ?: Color.Gray
                                onConfirm(
                                    EventPatch(
                                        title = title.trim(),
                                        startTS = start.atZone(zone).toEpochSecond(),
                                        endTS = end.atZone(zone).toEpochSecond(),
                                        location = location.trim(),
                                        description = if (activeStructuredFields == null) {
                                            stripSourceImageMarkers(description.trim())
                                        } else {
                                            RecognitionRuleCatalog.formatDescription(
                                                eventTag,
                                                List(activeStructuredFields.size) { index ->
                                                    structuredValues.getOrElse(index) { "" }.trim()
                                                }.joinToString("|"),
                                            )
                                        },
                                        tag = eventTag,
                                        color = editDraft?.color ?: nextColor.toArgb(),
                                        rrule = repeatSpec?.toRRule().orEmpty(),
                                        reminder1Minutes = reminderList.getOrElse(0) { -1 },
                                        reminder2Minutes = reminderList.getOrElse(1) { -1 },
                                        reminder3Minutes = reminderList.getOrElse(2) { -1 },
                                        pendingAttachmentKey = pendingAttachmentKey,
                                    ),
                                )
                            }
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = title.isNotBlank(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(if (editDraft == null) "创建" else "保存")
                }
            }
        }
    }

    if (showStartDatePicker) {
        WheelDatePickerDialog(startDate, { showStartDatePicker = false }, title = "开始日期") {
            startDate = it
            val start = LocalDateTime.of(startDate, java.time.LocalTime.parse(startTime, formatter))
            val end = LocalDateTime.of(endDate, java.time.LocalTime.parse(endTime, formatter))
            if (!end.isAfter(start)) {
                val adjusted = start.plusMinutes(DefaultDurationMinutes)
                endDate = adjusted.toLocalDate()
                endTime = adjusted.toLocalTime().format(formatter)
            }
            showStartDatePicker = false
        }
    }
    if (showEndDatePicker) {
        WheelDatePickerDialog(endDate, { showEndDatePicker = false }, title = "结束日期") {
            endDate = it
            showEndDatePicker = false
        }
    }
    if (showStartTimePicker) {
        WheelTimePickerDialog(startTime, { showStartTimePicker = false }, title = "开始时间") {
            startTime = it
            showStartTimePicker = false
        }
    }
    if (showEndTimePicker) {
        WheelTimePickerDialog(endTime, { showEndTimePicker = false }, title = "结束时间") {
            endTime = it
            showEndTimePicker = false
        }
    }
    if (showReminderPicker) {
        WheelReminderPickerDialog(
            initialMinutes = 30,
            onDismiss = { showReminderPicker = false },
            onConfirm = {
                if (it !in reminders && reminders.size < 3) reminders.add(it)
                showReminderPicker = false
            },
            availableOptions = reminderOptions,
        )
    }
    if (showRepeatPicker) {
        RepeatRulePickerDialog(
            currentSpec = repeatSpec,
            startDate = startDate,
            onDismiss = { showRepeatPicker = false },
            onConfirm = {
                repeatSpec = it
                showRepeatPicker = false
            },
        )
    }
}

@Composable
private fun EventDateTimeRow(
    label: String,
    date: String,
    time: String,
    onDateClick: () -> Unit,
    onTimeClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = onDateClick,
                modifier = Modifier.weight(1.5f),
            ) {
                Text(date)
            }
            Button(
                onClick = onTimeClick,
                modifier = Modifier.weight(1f),
            ) {
                Text(time)
            }
        }
    }
}

@Composable
private fun AttachmentPreference(
    attachment: EventAttachment,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    ArrowPreference(
        title = attachment.displayName.ifBlank { java.io.File(attachment.localPath).name },
        summary = EventAttachmentManager.formatSize(attachment.sizeBytes),
        endActions = {
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = MiuixIcons.Normal.Delete,
                    contentDescription = "删除附件",
                    tint = MiuixTheme.colorScheme.error,
                )
            }
        },
        onClick = onOpen,
    )
}
