package com.antgskds.calendarassistant.shared.ui.edition

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun EditionSwitchSettingItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
) {
    val haptics = rememberAppHaptics()
    SwitchPreference(
        checked = checked,
        onCheckedChange = {
            haptics.selection()
            onCheckedChange(it)
        },
        title = title,
        summary = subtitle.takeIf(String::isNotBlank),
    )
}

@Composable
fun EditionOptionalCategoricalSettingItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    optionTitle: String,
    optionSummary: String,
    options: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
) {
    if (options.isEmpty()) return
    val haptics = rememberAppHaptics()
    val dropdownOptions = listOf("无") + options
    WindowDropdownPreference(
        items = dropdownOptions,
        selectedIndex = if (checked) selectedIndex.coerceIn(options.indices) + 1 else 0,
        title = title,
        summary = if (checked) optionSummary else subtitle,
        onSelectedIndexChange = { index ->
            haptics.selection()
            if (index == 0) {
                onCheckedChange(false)
            } else {
                if (!checked) onCheckedChange(true)
                onSelectedIndexChange(index - 1)
            }
        },
    )
}

@Composable
fun EditionSwitchSliderSettingItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    optionTitle: String,
    optionSummary: String,
    value: Float,
    valueText: String,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabels: List<String> = emptyList(),
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
) {
    val haptics = rememberAppHaptics()
    SwitchPreference(
        checked = checked,
        onCheckedChange = {
            haptics.selection()
            onCheckedChange(it)
        },
        title = title,
        summary = subtitle,
        bottomAction = if (checked) {
            {
                SliderPreference(
                    value = value,
                    onValueChange = onValueChange,
                    title = optionTitle,
                    summary = optionSummary,
                    valueText = valueText,
                    valueRange = valueRange,
                    steps = steps,
                    showKeyPoints = steps > 0,
                )
            }
        } else {
            null
        },
    )
}

@Composable
fun EditionSideChoiceSettingItem(
    title: String,
    subtitle: String,
    selectedSide: String,
    onSideSelected: (String) -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
) {
    val haptics = rememberAppHaptics()
    val sides = listOf("左侧", "右侧")
    WindowDropdownPreference(
        items = sides,
        selectedIndex = if (selectedSide == "RIGHT") 1 else 0,
        title = title,
        summary = subtitle,
        onSelectedIndexChange = {
            haptics.selection()
            onSideSelected(if (it == 1) "RIGHT" else "LEFT")
        },
    )
}

@Composable
fun EditionActionSettingItem(
    title: String,
    subtitle: String,
    value: String,
    icon: ImageVector?,
    enabled: Boolean,
    hapticOnClick: Boolean,
    onClick: () -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
    cardValueStyle: TextStyle,
) {
    val haptics = rememberAppHaptics()
    ArrowPreference(
        title = title,
        summary = subtitle.takeIf(String::isNotBlank),
        enabled = enabled,
        onClick = {
            if (hapticOnClick) haptics.selection()
            onClick()
        },
        endActions = {
            if (value.isNotBlank()) {
                Text(
                    text = value,
                    color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                )
            }
        },
    )
}

@Composable
fun EditionDropdownActionSettingItem(
    title: String,
    subtitle: String,
    options: List<String>,
    selectedIndex: Int,
    onSelectedIndexChange: (Int) -> Unit,
    icon: ImageVector?,
    onNativeClick: () -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
    cardValueStyle: TextStyle,
) {
    if (options.isEmpty()) return
    val haptics = rememberAppHaptics()
    WindowDropdownPreference(
        items = options,
        selectedIndex = selectedIndex.coerceIn(options.indices),
        title = title,
        summary = subtitle,
        onSelectedIndexChange = {
            haptics.selection()
            onSelectedIndexChange(it)
        },
    )
}

@Composable
fun EditionSliderSettingItem(
    title: String,
    subtitle: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
    cardValueStyle: TextStyle,
    showValueAsNumber: Boolean,
    valueUnit: String,
    categoricalLabels: List<String>?,
) {
    val optionCount = steps + 2
    if (optionCount == 3 && !showValueAsNumber) {
        val options = categoricalLabels ?: if (valueRange.start == 1f && valueRange.endInclusive == 3f) {
            listOf("小", "中", "大")
        } else {
            List(optionCount) { index -> (valueRange.start + index).toInt().toString() }
        }
        WindowDropdownPreference(
            items = options,
            selectedIndex = (value.toInt() - valueRange.start.toInt()).coerceIn(options.indices),
            title = title,
            summary = subtitle,
            onSelectedIndexChange = { index ->
                onValueChange(valueRange.start + index)
            },
        )
        return
    }
    val displayValue = if (showValueAsNumber) {
        "${value.toInt()}$valueUnit"
    } else {
        when (value.toInt()) {
            1 -> "小"
            2 -> "中"
            3 -> "大"
            else -> value.toInt().toString()
        }
    }
    SliderPreference(
        value = value,
        onValueChange = onValueChange,
        title = title,
        summary = subtitle.takeIf(String::isNotBlank),
        valueText = displayValue,
        valueRange = valueRange,
        steps = steps,
        showKeyPoints = steps > 0,
    )
}

@Composable
fun EditionVolumeLongPressSettingItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    action: Int,
    onCheckedChange: (Boolean) -> Unit,
    onActionChange: (Int) -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
) {
    val options = listOf("识屏", "悬浮窗", "随口记")
    val selectedAction = action.coerceIn(1, 3)
    WindowDropdownPreference(
        items = listOf("无") + options,
        selectedIndex = if (checked) selectedAction else 0,
        title = title,
        summary = if (checked) options[selectedAction - 1] else subtitle,
        onSelectedIndexChange = { index ->
            if (index == 0) {
                onCheckedChange(false)
            } else {
                if (!checked) onCheckedChange(true)
                onActionChange(index)
            }
        },
    )
}

@Composable
fun EditionAdvanceReminderSettingItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    minutes: Int,
    onCheckedChange: (Boolean) -> Unit,
    onMinutesChange: (Int) -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
) {
    val values = listOf(30, 45, 60)
    val selectedValueIndex = values.indexOf(minutes).coerceAtLeast(0)
    WindowDropdownPreference(
        items = listOf("无") + values.map { "${it} 分钟" },
        selectedIndex = if (checked) selectedValueIndex + 1 else 0,
        title = title,
        summary = if (checked) "${values[selectedValueIndex]} 分钟" else subtitle,
        onSelectedIndexChange = { index ->
            if (index == 0) {
                onCheckedChange(false)
            } else {
                if (!checked) onCheckedChange(true)
                onMinutesChange(values[index - 1])
            }
        },
    )
}
