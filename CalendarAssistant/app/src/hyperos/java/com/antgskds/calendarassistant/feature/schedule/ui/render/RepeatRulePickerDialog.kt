package com.antgskds.calendarassistant.feature.schedule.ui.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.schedule.domain.model.RepeatEnd
import com.antgskds.calendarassistant.feature.schedule.domain.model.RepeatFrequency
import com.antgskds.calendarassistant.feature.schedule.domain.model.RepeatSpec
import com.antgskds.calendarassistant.feature.schedule.domain.model.shortCn
import com.antgskds.calendarassistant.shared.ui.material.component.WheelDatePicker
import java.time.DayOfWeek
import java.time.LocalDate
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private enum class RepeatEditorPage { OPTIONS, UNTIL_DATE }
private enum class RepeatPreset { NONE, DAILY, WEEKLY, WEEKDAYS, CUSTOM }

@Composable
fun RepeatRulePickerDialog(
    currentSpec: RepeatSpec?,
    startDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (RepeatSpec?) -> Unit,
) {
    val initialPreset = remember(currentSpec) { currentSpec.toPreset() }
    var preset by remember(currentSpec) { mutableStateOf(initialPreset) }
    var byDays by remember(currentSpec, startDate) {
        mutableStateOf(currentSpec?.byDays?.ifEmpty { setOf(startDate.dayOfWeek) } ?: setOf(startDate.dayOfWeek))
    }
    var endMode by remember(currentSpec) {
        mutableStateOf(if (currentSpec?.end is RepeatEnd.Until) 1 else 0)
    }
    var untilDate by remember(currentSpec, startDate) {
        mutableStateOf((currentSpec?.end as? RepeatEnd.Until)?.date ?: startDate.plusMonths(1))
    }
    var pendingDate by remember(untilDate) { mutableStateOf(untilDate) }
    var page by remember { mutableStateOf(RepeatEditorPage.OPTIONS) }

    fun buildResult(): RepeatSpec? = when (preset) {
        RepeatPreset.NONE -> null
        RepeatPreset.DAILY -> RepeatSpec.daily()
        RepeatPreset.WEEKLY -> RepeatSpec.weekly()
        RepeatPreset.WEEKDAYS -> RepeatSpec.weekdays()
        RepeatPreset.CUSTOM -> RepeatSpec(
            frequency = RepeatFrequency.WEEKLY,
            byDays = byDays.ifEmpty { setOf(startDate.dayOfWeek) },
            end = if (endMode == 1) RepeatEnd.Until(untilDate) else RepeatEnd.Never,
        )
    }

    WindowDialog(
        show = true,
        title = if (page == RepeatEditorPage.OPTIONS) "重复" else "截止日期",
        onDismissRequest = {
            if (page == RepeatEditorPage.UNTIL_DATE) page = RepeatEditorPage.OPTIONS else onDismiss()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (page == RepeatEditorPage.UNTIL_DATE) {
                WheelDatePicker(
                    initialDate = pendingDate,
                    onDateChanged = { pendingDate = it },
                )
            } else {
                RepeatPreset.entries.forEach { item ->
                    RadioButtonPreference(
                        title = item.label,
                        selected = preset == item,
                        onClick = { preset = item },
                    )
                }

                if (preset == RepeatPreset.CUSTOM) {
                    Text(
                        text = "重复日期",
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                    )
                    DayOfWeek.entries.forEach { day ->
                        CheckboxPreference(
                            title = day.shortCn(),
                            checked = day in byDays,
                            onCheckedChange = { checked ->
                                val next = if (checked) byDays + day else byDays - day
                                byDays = next.ifEmpty { setOf(startDate.dayOfWeek) }
                            },
                        )
                    }
                    WindowDropdownPreference(
                        items = listOf("永不结束", "指定日期"),
                        selectedIndex = endMode,
                        title = "结束方式",
                        summary = if (endMode == 0) "永不结束" else "截止到 $untilDate",
                        onSelectedIndexChange = { endMode = it },
                    )
                    if (endMode == 1) {
                        ArrowPreference(
                            title = "截止日期",
                            summary = untilDate.toString(),
                            onClick = {
                                pendingDate = untilDate
                                page = RepeatEditorPage.UNTIL_DATE
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(onClick = {
                    if (page == RepeatEditorPage.UNTIL_DATE) page = RepeatEditorPage.OPTIONS else onDismiss()
                }, modifier = Modifier.weight(1f)) {
                    Text(if (page == RepeatEditorPage.UNTIL_DATE) "返回" else "取消")
                }
                Button(
                    onClick = {
                        if (page == RepeatEditorPage.UNTIL_DATE) {
                            untilDate = pendingDate
                            endMode = 1
                            page = RepeatEditorPage.OPTIONS
                        } else {
                            onConfirm(buildResult())
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text("确定")
                }
            }
        }
    }
}

private val RepeatPreset.label: String
    get() = when (this) {
        RepeatPreset.NONE -> "不重复"
        RepeatPreset.DAILY -> "每天"
        RepeatPreset.WEEKLY -> "每周"
        RepeatPreset.WEEKDAYS -> "周一至周五"
        RepeatPreset.CUSTOM -> "自定义"
    }

private fun RepeatSpec?.toPreset(): RepeatPreset = when {
    this == null -> RepeatPreset.NONE
    toRRule() == RepeatSpec.daily().toRRule() -> RepeatPreset.DAILY
    toRRule() == RepeatSpec.weekly().toRRule() -> RepeatPreset.WEEKLY
    toRRule() == RepeatSpec.weekdays().toRRule() -> RepeatPreset.WEEKDAYS
    else -> RepeatPreset.CUSTOM
}
