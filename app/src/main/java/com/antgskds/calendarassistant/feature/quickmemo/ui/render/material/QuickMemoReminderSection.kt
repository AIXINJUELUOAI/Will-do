package com.antgskds.calendarassistant.feature.quickmemo.ui.render.material


import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.app.ui.theme.material.background.LocalAppBackgroundStyleEnabled
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoReminderEntity
import com.antgskds.calendarassistant.feature.schedule.domain.model.RepeatEnd
import com.antgskds.calendarassistant.feature.schedule.domain.model.RepeatFrequency
import com.antgskds.calendarassistant.feature.schedule.domain.model.RepeatSpec
import com.antgskds.calendarassistant.feature.schedule.domain.model.shortCn
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.material.component.AppCard
import com.antgskds.calendarassistant.shared.ui.material.component.AppSheetAction
import com.antgskds.calendarassistant.shared.ui.material.component.AppSheetActionRole
import com.antgskds.calendarassistant.shared.ui.material.component.AppModalBottomSheet
import com.antgskds.calendarassistant.shared.ui.material.component.WheelDatePicker
import com.antgskds.calendarassistant.shared.ui.material.component.WheelTimePicker
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val reminderSummaryFormatter = DateTimeFormatter.ofPattern("M月d日 EEEE HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QuickMemoReminderSection(
    reminders: List<QuickMemoReminderEntity>,
    onSaveReminder: (Long?, Long?, String) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    uiSize: Int,
    hapticEnabled: Boolean
) {
    var showSheet by remember { mutableStateOf(false) }
    var editingReminder by remember { mutableStateOf<QuickMemoReminderEntity?>(null) }
    val haptics = rememberAppHaptics(hapticEnabled)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        reminders.sortedWith(compareBy<QuickMemoReminderEntity> { it.triggerAt }.thenBy { it.id }).forEach { reminder ->
            androidx.compose.runtime.key(reminder.id) {
                QuickMemoReminderRow(
                    title = formatReminderTime(reminder.triggerAt),
                    subtitle = RepeatSpec.fromRRule(reminder.rrule)?.summary()?.let { "· $it" },
                    actionLabel = "修改",
                    uiSize = uiSize,
                    onClick = { haptics.click(); editingReminder = reminder; showSheet = true },
                )
            }
        }
        QuickMemoReminderRow(
            title = "提醒",
            subtitle = null,
            actionLabel = "添加",
            uiSize = uiSize,
            onClick = { haptics.click(); editingReminder = null; showSheet = true },
        )
    }

    if (showSheet) {
        QuickMemoReminderSheet(
            reminder = editingReminder,
            onDismiss = { showSheet = false },
            onSave = { reminderId, value, rrule ->
                onSaveReminder(reminderId, value, rrule)
                showSheet = false
            },
            onDelete = { reminderId ->
                onDeleteReminder(reminderId)
                showSheet = false
            }
        )
    }
}

/** 摘要和底部添加入口共用一行，不再叠加单独的提醒框。 */
@Composable
private fun QuickMemoReminderRow(
    title: String,
    subtitle: String?,
    actionLabel: String,
    uiSize: Int,
    onClick: () -> Unit,
) {
    val wallpaper = LocalAppBackgroundStyleEnabled.current
    val scheme = MaterialTheme.colorScheme
    val primaryText = if (wallpaper) scheme.onBackground else scheme.onSurface
    val secondaryText = if (wallpaper) primaryText.copy(alpha = 0.68f) else scheme.onSurfaceVariant
    val accent = if (wallpaper) primaryText.copy(alpha = 0.78f) else scheme.primary
    val buttonColor = if (wallpaper) primaryText else scheme.primary.copy(alpha = 0.12f)
    val buttonText = if (wallpaper) {
        if (primaryText.luminance() > 0.5f) Color.Black else Color.White
    } else scheme.primary

    // 与下方日程待办相同的无底色行，不经过会自动叠加壁纸材质的 AppCard。
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(vertical = when (uiSize) { 1 -> 10.dp; 3 -> 14.dp; else -> 12.dp }),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(5.dp).height(when (uiSize) { 1 -> 34.dp; 3 -> 44.dp; else -> 40.dp })
            .clip(RoundedCornerShape(3.dp)).background(accent))
        Spacer(Modifier.width(16.dp))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title, modifier = Modifier.weight(1f, fill = false).alignByBaseline(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = primaryText,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle, modifier = Modifier.alignByBaseline(),
                    style = MaterialTheme.typography.bodyMedium, color = secondaryText,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Surface(
            onClick = onClick,
            shape = RoundedCornerShape(999.dp),
            color = buttonColor,
            contentColor = buttonText,
        ) {
            Text(
                actionLabel, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickMemoReminderSheet(
    reminder: QuickMemoReminderEntity?,
    onDismiss: () -> Unit,
    onSave: (Long?, Long, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val initial = remember(reminder?.id, reminder?.triggerAt) {
        reminder?.triggerAt
            ?.takeIf { it > System.currentTimeMillis() }
            ?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() }
            ?: LocalDateTime.now().plusHours(1).withSecond(0).withNano(0)
    }
    var selectedDate by remember(reminder?.id) { mutableStateOf(initial.toLocalDate()) }
    var selectedHour by remember(reminder?.id) { mutableIntStateOf(initial.hour) }
    var selectedMinute by remember(reminder?.id) { mutableIntStateOf(initial.minute) }
    var selectedRepeatSpec by remember(reminder?.id, reminder?.rrule) {
        mutableStateOf(RepeatSpec.fromRRule(reminder?.rrule.orEmpty()))
    }
    var expandedSection by remember(reminder?.id) { mutableStateOf<ReminderEditorSection?>(null) }

    AppModalBottomSheet(
        title = if (reminder == null) "添加提醒" else "编辑提醒",
        onDismissRequest = onDismiss,
        actions = buildList {
            reminder?.id?.let { reminderId ->
                add(AppSheetAction(
                    text = "删除此提醒",
                    role = AppSheetActionRole.Destructive,
                    onClick = { haptics.warning(); onDelete(reminderId) },
                ))
            }
            add(AppSheetAction(
                text = "保存",
                onClick = {
                    val value = selectedDate
                        .atTime(selectedHour, selectedMinute)
                        .atZone(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                    if (value <= System.currentTimeMillis()) {
                        haptics.warning()
                        Toast.makeText(context, "请选择未来时间", Toast.LENGTH_SHORT).show()
                    } else {
                        haptics.confirm()
                        onSave(reminder?.id, value, selectedRepeatSpec?.toRRule().orEmpty())
                    }
                },
            ))
        },
    ) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppCard(
                modifier = Modifier.animateContentSize(),
                shape = RoundedCornerShape(20.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                ReminderSettingRow(
                    label = "日期",
                    value = "${selectedDate.year}年${selectedDate.monthValue}月${selectedDate.dayOfMonth}日",
                    expanded = expandedSection == ReminderEditorSection.DATE,
                    onClick = { expandedSection = expandedSection.toggle(ReminderEditorSection.DATE) }
                )
                AnimatedVisibility(visible = expandedSection == ReminderEditorSection.DATE) {
                    WheelDatePicker(initialDate = selectedDate) { selectedDate = it }
                }
                ReminderDivider()
                ReminderSettingRow(
                    label = "时间",
                    value = String.format("%02d:%02d", selectedHour, selectedMinute),
                    expanded = expandedSection == ReminderEditorSection.TIME,
                    onClick = { expandedSection = expandedSection.toggle(ReminderEditorSection.TIME) }
                )
                AnimatedVisibility(visible = expandedSection == ReminderEditorSection.TIME) {
                    WheelTimePicker(
                        initialHour = selectedHour,
                        initialMinute = selectedMinute
                    ) { hour, minute ->
                        selectedHour = hour
                        selectedMinute = minute
                    }
                }
                ReminderDivider()
                ReminderSettingRow(
                    label = "重复",
                    value = selectedRepeatSpec?.summary() ?: "不重复",
                    expanded = expandedSection == ReminderEditorSection.REPEAT,
                    onClick = { expandedSection = expandedSection.toggle(ReminderEditorSection.REPEAT) }
                )
                AnimatedVisibility(visible = expandedSection == ReminderEditorSection.REPEAT) {
                    InlineRepeatPicker(
                        currentSpec = selectedRepeatSpec,
                        startDate = selectedDate,
                        onChange = { selectedRepeatSpec = it }
                    )
                }
            }

        }
    }
}

@Composable
private fun ReminderSettingRow(
    label: String,
    value: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = value,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (expanded) "⌃" else "⌄",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReminderDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun InlineRepeatPicker(
    currentSpec: RepeatSpec?,
    startDate: LocalDate,
    onChange: (RepeatSpec?) -> Unit
) {
    var untilDate by remember(currentSpec, startDate) {
        mutableStateOf((currentSpec?.end as? RepeatEnd.Until)?.date ?: startDate.plusMonths(1))
    }
    var showUntilPicker by remember(currentSpec) {
        mutableStateOf(currentSpec?.end is RepeatEnd.Until)
    }
    val choice = repeatChoice(currentSpec)
    val customSpec = currentSpec.takeIf { choice == RepeatChoice.CUSTOM }
        ?: RepeatSpec(
            frequency = RepeatFrequency.WEEKLY,
            byDays = setOf(startDate.dayOfWeek)
        )

    Column(
        modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RepeatChoiceButton(
                label = "不重复",
                selected = choice == RepeatChoice.NONE,
                modifier = Modifier.weight(1f),
                onClick = { onChange(null) }
            )
            RepeatChoiceButton(
                label = "每天",
                selected = choice == RepeatChoice.DAILY,
                modifier = Modifier.weight(1f),
                onClick = { onChange(RepeatSpec.daily()) }
            )
            RepeatChoiceButton(
                label = "每周",
                selected = choice == RepeatChoice.WEEKLY,
                modifier = Modifier.weight(1f),
                onClick = { onChange(RepeatSpec.weekly()) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RepeatChoiceButton(
                label = "工作日",
                selected = choice == RepeatChoice.WEEKDAYS,
                modifier = Modifier.weight(1f),
                onClick = { onChange(RepeatSpec.weekdays()) }
            )
            RepeatChoiceButton(
                label = "自定义",
                selected = choice == RepeatChoice.CUSTOM,
                modifier = Modifier.weight(2f),
                onClick = { onChange(customSpec) }
            )
        }

        AnimatedVisibility(visible = choice == RepeatChoice.CUSTOM) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DayOfWeek.entries.forEach { day ->
                        val selected = day in customSpec.byDays
                        Surface(
                            onClick = {
                                val nextDays = if (selected) customSpec.byDays - day else customSpec.byDays + day
                                onChange(customSpec.copy(byDays = nextDays.ifEmpty { setOf(startDate.dayOfWeek) }))
                            },
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            contentColor = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(day.shortCn().removePrefix("周"))
                            }
                        }
                    }
                }
                RepeatEndRow(
                    label = "永不结束",
                    selected = customSpec.end is RepeatEnd.Never,
                    onClick = {
                        showUntilPicker = false
                        onChange(customSpec.copy(end = RepeatEnd.Never))
                    }
                )
                RepeatEndRow(
                    label = "截止日期 ${untilDate.year}年${untilDate.monthValue}月${untilDate.dayOfMonth}日",
                    selected = customSpec.end is RepeatEnd.Until,
                    onClick = {
                        showUntilPicker = true
                        onChange(customSpec.copy(end = RepeatEnd.Until(untilDate)))
                    }
                )
                AnimatedVisibility(visible = showUntilPicker && customSpec.end is RepeatEnd.Until) {
                    WheelDatePicker(initialDate = untilDate) { date ->
                        untilDate = date
                        onChange(customSpec.copy(end = RepeatEnd.Until(date)))
                    }
                }
            }
        }
    }
}

@Composable
private fun RepeatChoiceButton(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun RepeatEndRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = selected, onClick = onClick)
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun repeatChoice(spec: RepeatSpec?): RepeatChoice = when {
    spec == null -> RepeatChoice.NONE
    spec.toRRule() == RepeatSpec.daily().toRRule() -> RepeatChoice.DAILY
    spec.toRRule() == RepeatSpec.weekly().toRRule() -> RepeatChoice.WEEKLY
    spec.toRRule() == RepeatSpec.weekdays().toRRule() -> RepeatChoice.WEEKDAYS
    else -> RepeatChoice.CUSTOM
}

private fun ReminderEditorSection?.toggle(target: ReminderEditorSection): ReminderEditorSection? =
    if (this == target) null else target

private fun formatReminderTime(reminderAt: Long): String {
    val time = Instant.ofEpochMilli(reminderAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
    val today = LocalDate.now()
    return when (time.toLocalDate()) {
        today -> "今天 ${time.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}"
        today.plusDays(1) -> "明天 ${time.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}"
        else -> time.format(reminderSummaryFormatter)
    }
}

private enum class ReminderEditorSection {
    DATE,
    TIME,
    REPEAT
}

private enum class RepeatChoice {
    NONE,
    DAILY,
    WEEKLY,
    WEEKDAYS,
    CUSTOM
}
