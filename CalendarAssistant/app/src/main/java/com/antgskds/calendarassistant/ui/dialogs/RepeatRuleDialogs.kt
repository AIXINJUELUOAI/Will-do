package com.antgskds.calendarassistant.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.core.model.RepeatEnd
import com.antgskds.calendarassistant.core.model.RepeatFrequency
import com.antgskds.calendarassistant.core.model.RepeatSpec
import com.antgskds.calendarassistant.core.model.shortCn
import com.antgskds.calendarassistant.ui.components.WheelDatePicker
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import java.time.DayOfWeek
import java.time.LocalDate

@Composable
fun RepeatRulePickerDialog(
    currentSpec: RepeatSpec?,
    startDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (RepeatSpec?) -> Unit
) {
    val haptics = rememberAppHaptics()
    var page by remember(currentSpec) { mutableStateOf(RepeatDialogPage.MAIN) }
    var selectedSpec by remember(currentSpec) { mutableStateOf(currentSpec) }
    var byDays by remember(currentSpec, startDate) {
        mutableStateOf(resolveInitialByDays(currentSpec, startDate))
    }
    var endMode by remember(currentSpec) {
        mutableStateOf(if (currentSpec?.end is RepeatEnd.Until) RepeatEndMode.UNTIL else RepeatEndMode.NEVER)
    }
    var untilDate by remember(currentSpec, startDate) {
        mutableStateOf((currentSpec?.end as? RepeatEnd.Until)?.date ?: startDate.plusMonths(1))
    }
    var pendingUntilDate by remember(currentSpec, startDate) { mutableStateOf(untilDate) }

    fun openCustomPage() {
        val baseSpec = selectedSpec ?: currentSpec
        byDays = resolveInitialByDays(baseSpec, startDate)
        endMode = if (baseSpec?.end is RepeatEnd.Until) RepeatEndMode.UNTIL else RepeatEndMode.NEVER
        untilDate = (baseSpec?.end as? RepeatEnd.Until)?.date ?: untilDate
        pendingUntilDate = untilDate
        page = RepeatDialogPage.CUSTOM_WEEKLY
    }

    fun buildCustomSpec(): RepeatSpec {
        return RepeatSpec(
            frequency = RepeatFrequency.WEEKLY,
            interval = 1,
            byDays = byDays,
            end = when (endMode) {
                RepeatEndMode.NEVER -> RepeatEnd.Never
                RepeatEndMode.UNTIL -> RepeatEnd.Until(untilDate)
            }
        )
    }

    AlertDialog(
        onDismissRequest = {
            if (page == RepeatDialogPage.UNTIL_DATE) page = RepeatDialogPage.CUSTOM_WEEKLY else onDismiss()
        },
        title = {
            RepeatDialogTitle(
                title = when (page) {
                    RepeatDialogPage.MAIN -> "重复"
                    RepeatDialogPage.CUSTOM_WEEKLY -> "自定义重复"
                    RepeatDialogPage.UNTIL_DATE -> "截止日期"
                },
                showBack = page != RepeatDialogPage.MAIN,
                onBack = {
                    haptics.click()
                    page = when (page) {
                        RepeatDialogPage.MAIN -> RepeatDialogPage.MAIN
                        RepeatDialogPage.CUSTOM_WEEKLY -> RepeatDialogPage.MAIN
                        RepeatDialogPage.UNTIL_DATE -> RepeatDialogPage.CUSTOM_WEEKLY
                    }
                }
            )
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                contentAlignment = if (page == RepeatDialogPage.UNTIL_DATE) Alignment.Center else Alignment.TopStart
            ) {
                when (page) {
                    RepeatDialogPage.MAIN -> RepeatMainPage(
                        currentSpec = currentSpec,
                        selectedSpec = selectedSpec,
                        onSelect = { haptics.selection(); selectedSpec = it },
                        onCustom = { openCustomPage() }
                    )

                    RepeatDialogPage.CUSTOM_WEEKLY -> RepeatCustomWeeklyPage(
                        byDays = byDays,
                        startDate = startDate,
                        endMode = endMode,
                        untilDate = untilDate,
                        onToggleDay = { day ->
                            haptics.selection()
                            val next = if (day in byDays) byDays - day else byDays + day
                            byDays = next.ifEmpty { setOf(startDate.dayOfWeek) }
                        },
                        onNeverEnd = { haptics.selection(); endMode = RepeatEndMode.NEVER },
                        onUntil = {
                            haptics.selection()
                            endMode = RepeatEndMode.UNTIL
                            pendingUntilDate = untilDate
                            page = RepeatDialogPage.UNTIL_DATE
                        }
                    )

                    RepeatDialogPage.UNTIL_DATE -> WheelDatePicker(
                        initialDate = pendingUntilDate,
                        onDateChanged = { pendingUntilDate = it }
                    )
                }
            }
        },
        confirmButton = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (page != RepeatDialogPage.UNTIL_DATE && (currentSpec != null || selectedSpec != null)) {
                    TextButton(onClick = { haptics.confirm(); onConfirm(null) }) { Text("不重复") }
                }
                TextButton(
                    onClick = {
                        if (page == RepeatDialogPage.UNTIL_DATE) page = RepeatDialogPage.CUSTOM_WEEKLY else onDismiss()
                    }
                ) { Text("取消") }
                TextButton(
                    onClick = {
                        when (page) {
                            RepeatDialogPage.MAIN -> { haptics.confirm(); onConfirm(selectedSpec) }
                            RepeatDialogPage.CUSTOM_WEEKLY -> { haptics.confirm(); onConfirm(buildCustomSpec()) }
                            RepeatDialogPage.UNTIL_DATE -> {
                                haptics.confirm()
                                untilDate = pendingUntilDate
                                endMode = RepeatEndMode.UNTIL
                                page = RepeatDialogPage.CUSTOM_WEEKLY
                            }
                        }
                    }
                ) { Text("确定") }
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

@Composable
private fun RepeatDialogTitle(
    title: String,
    showBack: Boolean,
    onBack: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showBack) {
            Text(
                text = "‹",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack)
                    .padding(end = 12.dp)
            )
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RepeatMainPage(
    currentSpec: RepeatSpec?,
    selectedSpec: RepeatSpec?,
    onSelect: (RepeatSpec?) -> Unit,
    onCustom: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        RepeatRuleOptionRow(
            text = "每天",
            selected = selectedSpec?.toRRule() == RepeatSpec.daily().toRRule(),
            onClick = { onSelect(RepeatSpec.daily()) }
        )
        RepeatRuleOptionRow(
            text = "每周",
            selected = selectedSpec?.toRRule() == RepeatSpec.weekly().toRRule(),
            onClick = { onSelect(RepeatSpec.weekly()) }
        )
        RepeatRuleOptionRow(
            text = "周一至周五",
            selected = selectedSpec?.toRRule() == RepeatSpec.weekdays().toRRule(),
            onClick = { onSelect(RepeatSpec.weekdays()) }
        )
        RepeatRuleOptionRow(
            text = "自定义",
            selected = false,
            trailing = ">",
            onClick = onCustom
        )
    }
}

@Composable
private fun RepeatCustomWeeklyPage(
    byDays: Set<DayOfWeek>,
    startDate: LocalDate,
    endMode: RepeatEndMode,
    untilDate: LocalDate,
    onToggleDay: (DayOfWeek) -> Unit,
    onNeverEnd: () -> Unit,
    onUntil: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RepeatSectionHeader("重复日期")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY
                    ).forEach { day ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            CircularDayButton(
                                text = day.shortCn().removePrefix("周"),
                                selected = day in byDays,
                                onClick = { onToggleDay(day) }
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY, null, null, null).forEach { day ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            if (day != null) {
                            CircularDayButton(
                                text = day.shortCn().removePrefix("周"),
                                selected = day in byDays,
                                onClick = { onToggleDay(day) }
                            )
                            }
                        }
                    }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            RepeatSectionHeader("结束")
            EndConditionRow(
                text = "永不结束",
                selected = endMode == RepeatEndMode.NEVER,
                onClick = onNeverEnd
            )
            EndConditionRow(
                text = "截止日期",
                selected = endMode == RepeatEndMode.UNTIL,
                onClick = onUntil,
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = untilDate.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = ">",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        }
    }
}

@Composable
private fun CircularDayButton(text: String, selected: Boolean, onClick: () -> Unit) {
    val haptics = rememberAppHaptics()
    val bgColor = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable { haptics.selection(); onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun EndConditionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    trailing: @Composable (() -> Unit)? = null
) {
    val haptics = rememberAppHaptics()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { haptics.selection(); onClick() }
            .padding(vertical = 4.dp, horizontal = 4.dp)
    ) {
        RadioButton(selected = selected, onClick = { haptics.selection(); onClick() })
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge)

        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

@Composable
private fun RepeatRuleOptionRow(
    text: String,
    selected: Boolean,
    trailing: String? = null,
    onClick: () -> Unit
) {
    val haptics = rememberAppHaptics()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { haptics.selection(); onClick() }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (trailing != null) {
            Text(trailing, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) {
            Spacer(Modifier.width(16.dp))
            Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun RepeatSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold
    )
}

private fun resolveInitialByDays(spec: RepeatSpec?, startDate: LocalDate): Set<DayOfWeek> {
    return spec?.byDays?.takeIf { it.isNotEmpty() } ?: setOf(startDate.dayOfWeek)
}

private enum class RepeatDialogPage {
    MAIN,
    CUSTOM_WEEKLY,
    UNTIL_DATE
}

private enum class RepeatEndMode {
    NEVER,
    UNTIL
}
