package com.antgskds.calendarassistant.ui.dialogs

import android.net.Uri
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.core.attachment.EventAttachmentManager
import com.antgskds.calendarassistant.core.model.RepeatSpec
import com.antgskds.calendarassistant.core.rule.RecognitionRuleCatalog
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.core.util.stripSourceImageMarkers
import com.antgskds.calendarassistant.data.model.EditDraft
import com.antgskds.calendarassistant.data.model.EventPatch
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.ui.components.WheelDatePickerDialog
import com.antgskds.calendarassistant.ui.components.WheelReminderPickerDialog
import com.antgskds.calendarassistant.ui.components.WheelTimePickerDialog
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import com.antgskds.calendarassistant.ui.motion.PredictiveBottomDialogHost
import com.antgskds.calendarassistant.ui.theme.EventColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

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

private data class DialogEventTypeSpec(
    val tag: String,
    val label: String,
    val fieldLabels: List<String> = emptyList()
) {
    val isStructured: Boolean get() = fieldLabels.isNotEmpty()
}

private val DIALOG_EVENT_TYPE_SPECS = listOf(
    DialogEventTypeSpec(EventTags.GENERAL, "日程"),
    DialogEventTypeSpec(EventTags.TRAIN, "列车", listOf("车次", "检票口", "座位号")),
    DialogEventTypeSpec(EventTags.FLIGHT, "航班", listOf("航班号", "登机口", "座位号")),
    DialogEventTypeSpec(EventTags.TAXI, "打车", listOf("颜色", "车型", "车牌")),
    DialogEventTypeSpec(EventTags.PICKUP, "取件", listOf("取件码", "品牌", "位置")),
    DialogEventTypeSpec(EventTags.FOOD, "取餐", listOf("取餐码", "品牌", "位置")),
    DialogEventTypeSpec(EventTags.TICKET, "取票", listOf("取票码", "品牌", "位置")),
    DialogEventTypeSpec(EventTags.SENDER, "寄件", listOf("寄件码", "品牌", "地点"))
)

private val DIALOG_EVENT_TYPE_PAGES = listOf(
    listOf(EventTags.GENERAL, EventTags.TRAIN, EventTags.FLIGHT, EventTags.TAXI),
    listOf(EventTags.PICKUP, EventTags.FOOD, EventTags.TICKET, EventTags.SENDER)
)

private fun eventTypeSpecFor(tag: String): DialogEventTypeSpec? {
    return DIALOG_EVENT_TYPE_SPECS.firstOrNull { it.tag == tag }
}

private fun eventTypeLabel(tag: String): String {
    return when (tag) {
        EventTags.COURSE -> "课程"
        else -> eventTypeSpecFor(tag)?.label ?: "日程"
    }
}

private fun parseStructuredFieldValues(
    description: String,
    spec: DialogEventTypeSpec,
    allowFallback: Boolean
): List<String> {
    val explicitPayload = RuleMatchingEngine.resolvePayload(description, null)
    val payload = when {
        explicitPayload?.ruleId == spec.tag -> explicitPayload.payload
        allowFallback -> RuleMatchingEngine.resolvePayload(description, spec.tag)?.payload.orEmpty()
        else -> ""
    }
    return RuleMatchingEngine.splitFields(payload, spec.fieldLabels.size)
}

private fun buildStructuredDescription(spec: DialogEventTypeSpec, values: List<String>): String {
    val payload = List(spec.fieldLabels.size) { index -> values.getOrElse(index) { "" } }
        .joinToString("|") { it.trim() }
    return RecognitionRuleCatalog.formatDescription(spec.tag, payload)
}

private fun descriptionForGeneralMode(description: String): String {
    val payload = RuleMatchingEngine.resolvePayload(description, null)
    return if (payload != null && payload.ruleId != EventTags.GENERAL) {
        payload.payload.trim()
    } else {
        description
    }
}

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
    eventToEdit: Event?,
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
    onConfirm: (EventPatch) -> Unit
) {
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val haptics = rememberAppHaptics(settings.hapticFeedbackEnabled)
    val isEditing = editDraft != null
    val draftKey = editDraft?.hashCode() ?: 0

    val initialStart = editDraft?.let { LocalDateTime.of(it.startDate, it.startTime) }
        ?: LocalDateTime.now().withSecond(0).withNano(0)
    val initialEnd = editDraft?.let { LocalDateTime.of(it.endDate, it.endTime) }
        ?: initialStart.plusHours(1)
    val initialStartEpoch = initialStart.atZone(java.time.ZoneId.systemDefault()).toEpochSecond()
    val initialEndEpoch = initialEnd.atZone(java.time.ZoneId.systemDefault()).toEpochSecond()
    val pendingAttachmentKey = remember(draftKey) {
        EventAttachmentManager.eventKey(
            title = editDraft?.title ?: "",
            startTS = initialStartEpoch,
            endTS = initialEndEpoch,
            timeZone = java.time.ZoneId.systemDefault().id
        )
    }
    val initialAutoDurationMinutes = remember(draftKey) {
        Duration.between(initialStart, initialEnd).toMinutes().coerceAtLeast(1L)
    }

    // 计算过滤后的提醒选项
    val filteredReminderOptions = remember(settings.isAdvanceReminderEnabled, settings.advanceReminderMinutes) {
        if (settings.isAdvanceReminderEnabled && settings.advanceReminderMinutes > 0) {
            REMINDER_OPTIONS.filter { it.first > settings.advanceReminderMinutes }
        } else {
            REMINDER_OPTIONS
        }
    }

    val initialDescription = editDraft?.description.orEmpty()

    var title by remember(draftKey) { mutableStateOf(editDraft?.title ?: "") }
    var startDate by remember(draftKey) { mutableStateOf(initialStart.toLocalDate()) }
    var endDate by remember(draftKey) { mutableStateOf(initialEnd.toLocalDate()) }
    var startTime by remember(draftKey) { mutableStateOf(initialStart.toLocalTime().format(timeFormatter)) }
    var endTime by remember(draftKey) { mutableStateOf(initialEnd.toLocalTime().format(timeFormatter)) }
    var location by remember(draftKey) { mutableStateOf(editDraft?.location ?: "") }
    var desc by remember(draftKey) { mutableStateOf(stripSourceImageMarkers(initialDescription)) }
    var eventTag by remember(draftKey) { mutableStateOf(editDraft?.tag ?: EventTags.GENERAL) }
    val reminders = remember(draftKey) { mutableStateListOf<Int>().apply { addAll(editDraft?.reminders ?: emptyList()) } }
    var repeatSpec by remember(draftKey) { mutableStateOf(RepeatSpec.fromRRule(editDraft?.rrule.orEmpty())) }
    val attachmentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            if (editDraft?.eventId != null) {
                onAddAttachment(uri)
            } else {
                onAddPendingAttachment(uri, pendingAttachmentKey)
            }
        }
    }

    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showStartTimePicker by remember { mutableStateOf(false) }
    var showEndTimePicker by remember { mutableStateOf(false) }
    var showReminderPicker by remember { mutableStateOf(false) }
    var showRepeatPicker by remember { mutableStateOf(false) }
    var isTypePickerExpanded by remember(draftKey) { mutableStateOf(false) }
    var structuredEditingTag by remember(draftKey) { mutableStateOf<String?>(null) }
    val structuredFieldValues = remember(draftKey) { mutableStateListOf<String>() }
    var autoDurationMinutes by remember { mutableStateOf(initialAutoDurationMinutes) }
    var isEndTimeManuallySet by remember { mutableStateOf(false) }
    val eventTypePagerState = rememberPagerState(pageCount = { DIALOG_EVENT_TYPE_PAGES.size })
    val activeStructuredSpec = structuredEditingTag?.let { eventTypeSpecFor(it) }
    val isChildDialogVisible = showStartDatePicker || showEndDatePicker ||
            showStartTimePicker || showEndTimePicker || showReminderPicker || showRepeatPicker

    fun setStructuredFieldValues(values: List<String>, size: Int) {
        structuredFieldValues.clear()
        structuredFieldValues.addAll(List(size) { index -> values.getOrElse(index) { "" } })
    }

    fun selectEventType(spec: DialogEventTypeSpec) {
        val currentSpec = activeStructuredSpec
        val currentStructuredValues = structuredFieldValues.toList()
        val fallbackText = if (currentSpec != null) {
            currentStructuredValues.map { it.trim() }.filter { it.isNotBlank() }.joinToString(" ")
        } else {
            ""
        }

        if (spec.isStructured) {
            val parsedValues = parseStructuredFieldValues(
                description = desc,
                spec = spec,
                allowFallback = eventTag == spec.tag
            )
            val values = if (parsedValues.any { it.isNotBlank() }) parsedValues else currentStructuredValues
            eventTag = spec.tag
            setStructuredFieldValues(values, spec.fieldLabels.size)
            structuredEditingTag = spec.tag
        } else {
            eventTag = spec.tag
            structuredEditingTag = null
            desc = fallbackText.ifBlank { descriptionForGeneralMode(desc) }
        }
        isTypePickerExpanded = false
    }

    fun applyStructuredFields(): Boolean {
        val spec = activeStructuredSpec ?: return false
        desc = buildStructuredDescription(spec, structuredFieldValues)
        eventTag = spec.tag
        structuredEditingTag = null
        isTypePickerExpanded = false
        return true
    }

    fun applyDateTimeRange(start: LocalDateTime, end: LocalDateTime) {
        startDate = start.toLocalDate()
        startTime = start.toLocalTime().format(timeFormatter)
        endDate = end.toLocalDate()
        endTime = end.toLocalTime().format(timeFormatter)
    }

    if (!visible) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            decorFitsSystemWindows = false
        )
    ) {
        DialogEdgeToEdgeEffect(isDarkTheme = settings.isDarkMode)
        PredictiveBottomDialogHost(
            visible = visible,
            onDismiss = onDismiss,
            predictiveBackEnabled = settings.predictiveBackEnabled && !isChildDialogVisible,
            backHandlerEnabled = !isChildDialogVisible,
            contentAlignment = Alignment.Center,
            contentPadding = WindowInsets.navigationBars.asPaddingValues()
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(0.85f).heightIn(max = 670.dp),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(if (!isEditing) "新增日程" else "编辑日程", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }

                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text("类型:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.width(10.dp))
                    val baseTagLabel = eventTypeLabel(eventTag)
                    val tagLabel = if (repeatSpec != null || editDraft?.isRecurring == true) {
                        "重复 · $baseTagLabel"
                    } else {
                        baseTagLabel
                    }
                    BoxWithConstraints(modifier = Modifier.weight(1f)) {
                        val itemWidth = ((maxWidth - 24.dp) / 4).coerceAtLeast(52.dp)
                        if (isTypePickerExpanded) {
                            HorizontalPager(
                                state = eventTypePagerState,
                                modifier = Modifier.fillMaxWidth(),
                                pageSpacing = 0.dp
                            ) { pageIndex ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    DIALOG_EVENT_TYPE_PAGES[pageIndex].forEach { tag ->
                                        val spec = eventTypeSpecFor(tag) ?: return@forEach
                                        EventTypeChip(
                                            label = if (tag == EventTags.GENERAL && (repeatSpec != null || editDraft?.isRecurring == true)) "重复" else spec.label,
                                            selected = spec.tag == eventTag,
                                            modifier = Modifier.width(itemWidth),
                                            fillLabel = true,
                                            onClick = {
                                                haptics.click()
                                                if (spec.tag == eventTag) {
                                                    isTypePickerExpanded = false
                                                } else {
                                                    selectEventType(spec)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        } else {
                            EventTypeChip(
                                label = tagLabel,
                                selected = true,
                                modifier = Modifier.width(itemWidth),
                                fillLabel = true,
                                onClick = {
                                    haptics.click()
                                    isTypePickerExpanded = true
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("始", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(8.dp))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { haptics.click(); showStartDatePicker = true }, modifier = Modifier.weight(1.5f)) { Text(startDate.toString(), style = MaterialTheme.typography.bodyMedium) }
                        OutlinedButton(onClick = { haptics.click(); showStartTimePicker = true }, modifier = Modifier.weight(1f)) { Text(startTime, style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("终", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(8.dp))
                    Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { haptics.click(); showEndDatePicker = true }, modifier = Modifier.weight(1.5f)) { Text(endDate.toString(), style = MaterialTheme.typography.bodyMedium) }
                        OutlinedButton(onClick = { haptics.click(); showEndTimePicker = true }, modifier = Modifier.weight(1f)) { Text(endTime, style = MaterialTheme.typography.bodyMedium) }
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    InlineEventAction(
                        icon = Icons.Outlined.Notifications,
                        text = if (reminders.isEmpty()) "添加提醒" else "提醒 ${reminders.size} 个",
                        hapticEnabled = settings.hapticFeedbackEnabled
                    ) { showReminderPicker = true }
                    InlineEventAction(
                        icon = Icons.Outlined.Repeat,
                        text = repeatSpec?.summary() ?: "重复",
                        hapticEnabled = settings.hapticFeedbackEnabled
                    ) { showRepeatPicker = true }
                }
                if (reminders.isNotEmpty()) {
                    FlowRow(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        reminders.forEach { mins ->
                            val label = REMINDER_OPTIONS.find { it.first == mins }?.second ?: "${mins}分钟前"
                            InputChip(selected = false, onClick = { reminders.remove(mins) }, label = { Text(label) }, trailingIcon = { Icon(Icons.Default.Close, null, Modifier.size(14.dp)) })
                        }
                    }
                }

                if (activeStructuredSpec != null) {
                    StructuredEventFields(
                        spec = activeStructuredSpec,
                        values = structuredFieldValues,
                        onValueChange = { index, value -> structuredFieldValues[index] = value }
                    )
                } else {
                    OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("地点") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text("备注") }, modifier = Modifier.fillMaxWidth(), maxLines = 3)
                }

                    AttachmentSection(
                        attachments = attachments,
                        onAddClick = { haptics.click(); attachmentPickerLauncher.launch(arrayOf("*/*")) },
                        onOpenAttachment = onOpenAttachment,
                        onDeleteAttachment = onDeleteAttachment,
                        hapticEnabled = settings.hapticFeedbackEnabled,
                    )

                }

                Row(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { haptics.click(); onDismiss() }) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        if (applyStructuredFields()) {
                            haptics.confirm()
                            return@Button
                        }
                        if (title.isNotBlank()) {
                            val finalStart = parseDateTimeValue(startDate, startTime, timeFormatter)
                            val finalEnd = parseDateTimeValue(endDate, endTime, timeFormatter)

                            if (finalStart == null || finalEnd == null) {
                                haptics.error()
                                onShowMessage("时间格式无效，请重新选择")
                                return@Button
                            }

                            if (!finalEnd.isAfter(finalStart)) {
                                haptics.error()
                                onShowMessage("结束时间必须晚于开始时间")
                                return@Button
                            }

                            val zone = java.time.ZoneId.systemDefault()
                            val startEpoch = finalStart.atZone(zone).toEpochSecond()
                            val endEpoch = finalEnd.atZone(zone).toEpochSecond()
                            val reminderList = reminders.toList()
                            val nextColor = if (EventColors.isNotEmpty()) EventColors[currentEventsCount % EventColors.size] else Color.Gray
                            val patch = EventPatch(
                                title = title,
                                startTS = startEpoch,
                                endTS = endEpoch,
                                location = location,
                                description = stripSourceImageMarkers(desc),
                                color = editDraft?.color ?: nextColor.toArgb(),
                                tag = eventTag,
                                rrule = repeatSpec?.toRRule().orEmpty(),
                                reminder1Minutes = reminderList.getOrElse(0) { -1 },
                                reminder2Minutes = reminderList.getOrElse(1) { -1 },
                                reminder3Minutes = reminderList.getOrElse(2) { -1 },
                                pendingAttachmentKey = pendingAttachmentKey,
                                pendingAttachmentUris = emptyList()
                            )
                            haptics.confirm()
                            onConfirm(patch)
                        } else {
                            haptics.error()
                        }
                    }) { Text(if (activeStructuredSpec != null) "完成" else "确定") }
                }
            }
        }
        }
    }

    if (showStartDatePicker) WheelDatePickerDialog(startDate, { showStartDatePicker = false }, title = "开始日期") {
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
    if (showEndDatePicker) WheelDatePickerDialog(endDate, { showEndDatePicker = false }, title = "结束日期") {
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
    if (showStartTimePicker) WheelTimePickerDialog(startTime, { showStartTimePicker = false }, title = "开始时间") {
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
    if (showEndTimePicker) WheelTimePickerDialog(endTime, { showEndTimePicker = false }, title = "结束时间") {
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
    if (showRepeatPicker) {
        RepeatRulePickerDialog(
            currentSpec = repeatSpec,
            startDate = startDate,
            onDismiss = { showRepeatPicker = false },
            onConfirm = {
                repeatSpec = it
                showRepeatPicker = false
            }
        )
    }
}

@Composable
private fun EventTypeChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    fillLabel: Boolean = false,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        modifier = modifier,
        label = {
            Text(
                text = label,
                modifier = if (fillLabel) Modifier.fillMaxWidth() else Modifier,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
            labelColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
        ),
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    )
}

@Composable
private fun StructuredEventFields(
    spec: DialogEventTypeSpec,
    values: List<String>,
    onValueChange: (Int, String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "填写${spec.label}信息，点击完成后写入备注",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        spec.fieldLabels.forEachIndexed { index, label ->
            OutlinedTextField(
                value = values.getOrElse(index) { "" },
                onValueChange = { onValueChange(index, it) },
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}

@Composable
private fun AttachmentSection(
    attachments: List<EventAttachment>,
    onAddClick: () -> Unit,
    onOpenAttachment: (EventAttachment) -> Unit,
    onDeleteAttachment: (EventAttachment) -> Unit,
    hapticEnabled: Boolean = true
) {
    val hasAttachments = attachments.isNotEmpty()
    HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("附件", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        TextButton(onClick = onAddClick) {
            Icon(Icons.Outlined.AttachFile, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text("添加")
        }
    }
    if (!hasAttachments) {
        Text(
            "可添加截图、图片或其他文件",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        attachments.forEach { attachment ->
            AttachmentRow(
                title = attachment.displayName.ifBlank { java.io.File(attachment.localPath).name },
                subtitle = EventAttachmentManager.formatSize(attachment.sizeBytes),
                isImage = attachment.isImage,
                previewSource = AttachmentPreviewSource.FilePath(attachment.localPath).takeIf { attachment.isImage },
                onClick = { onOpenAttachment(attachment) },
                onDelete = { onDeleteAttachment(attachment) },
                hapticEnabled = hapticEnabled
            )
        }
    }
}

@Composable
private fun AttachmentRow(
    title: String,
    subtitle: String,
    isImage: Boolean,
    previewSource: AttachmentPreviewSource?,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    hapticEnabled: Boolean = true
) {
    val haptics = rememberAppHaptics(hapticEnabled)
    var bitmap by remember(previewSource) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(previewSource) {
        bitmap = null
        val source = previewSource ?: return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching { decodeAttachmentPreview(source) }.getOrNull()
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { haptics.click(); onClick() },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (isImage) Icons.Outlined.AttachFile else Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { haptics.warning(); onDelete() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "删除附件", modifier = Modifier.size(18.dp))
                }
            }
            bitmap?.let { image ->
                Image(
                    bitmap = image.asImageBitmap(),
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

private sealed class AttachmentPreviewSource {
    data class FilePath(val path: String) : AttachmentPreviewSource()
}

private fun decodeAttachmentPreview(source: AttachmentPreviewSource): android.graphics.Bitmap? {
    return when (source) {
        is AttachmentPreviewSource.FilePath -> {
            val file = File(source.path)
            if (file.exists()) BitmapFactory.decodeFile(file.absolutePath) else null
        }
    }
}

@Composable
private fun InlineEventAction(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    hapticEnabled: Boolean = true,
    onClick: () -> Unit
) {
    val haptics = rememberAppHaptics(hapticEnabled)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { haptics.click(); onClick() }
            .padding(vertical = 8.dp)
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
