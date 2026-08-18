package com.antgskds.calendarassistant.shared.ui.edition

import androidx.compose.runtime.Composable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.shared.ui.material.settings.MaterialActionSettingItem
import com.antgskds.calendarassistant.shared.ui.material.settings.MaterialAdvanceReminderSettingItem
import com.antgskds.calendarassistant.shared.ui.material.settings.MaterialSideChoiceSettingItem
import com.antgskds.calendarassistant.shared.ui.material.settings.MaterialSliderSettingItem
import com.antgskds.calendarassistant.shared.ui.material.settings.MaterialSwitchSettingItem
import com.antgskds.calendarassistant.shared.ui.material.settings.MaterialVolumeLongPressSettingItem
import com.antgskds.calendarassistant.shared.ui.material.settings.settingsSliderLabelTextStyle

@Composable
fun EditionSwitchSettingItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
    enabled: Boolean = true,
    onDisabledClick: (() -> Unit)? = null,
) = MaterialSwitchSettingItem(
    title,
    subtitle,
    checked,
    onCheckedChange,
    cardTitleStyle,
    cardSubtitleStyle,
    enabled,
    onDisabledClick,
)

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
    cardValueStyle: TextStyle? = null,
) {
    Column {
        MaterialSwitchSettingItem(
            title = title,
            subtitle = subtitle,
            checked = checked,
            onCheckedChange = onCheckedChange,
            cardTitleStyle = cardTitleStyle,
            cardSubtitleStyle = cardSubtitleStyle,
        )
        AnimatedVisibility(
            visible = checked,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            EditionCategoricalPreference(
                title = optionTitle,
                summary = optionSummary,
                options = options,
                selectedIndex = selectedIndex,
                onSelectedIndexChange = onSelectedIndexChange,
                titleTextStyle = cardTitleStyle,
                summaryTextStyle = cardSubtitleStyle,
                valueTextStyle = cardValueStyle,
            )
        }
    }
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
    val sliderLabelStyle = settingsSliderLabelTextStyle()
    Column {
        MaterialSwitchSettingItem(
            title = title,
            subtitle = subtitle,
            checked = checked,
            onCheckedChange = onCheckedChange,
            cardTitleStyle = cardTitleStyle,
            cardSubtitleStyle = cardSubtitleStyle,
        )
        AnimatedVisibility(
            visible = checked,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (valueLabels.isNotEmpty()) {
                        valueLabels.forEach { label ->
                            Text(label, style = sliderLabelStyle)
                        }
                    } else {
                        Text(valueRange.start.toInt().toString(), style = sliderLabelStyle)
                        Text(valueText, style = sliderLabelStyle)
                        Text(valueRange.endInclusive.toInt().toString(), style = sliderLabelStyle)
                    }
                }
                Slider(
                    value = value,
                    onValueChange = onValueChange,
                    valueRange = valueRange,
                    steps = steps,
                )
            }
        }
    }
}

@Composable
fun EditionSideChoiceSettingItem(
    title: String,
    subtitle: String,
    selectedSide: String,
    onSideSelected: (String) -> Unit,
    cardTitleStyle: TextStyle,
    cardSubtitleStyle: TextStyle,
) = MaterialSideChoiceSettingItem(
    title,
    subtitle,
    selectedSide,
    onSideSelected,
    cardTitleStyle,
    cardSubtitleStyle,
)

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
) = MaterialActionSettingItem(
    title,
    subtitle,
    value,
    icon,
    enabled,
    hapticOnClick,
    onClick,
    cardTitleStyle,
    cardSubtitleStyle,
    cardValueStyle,
)

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
) = MaterialActionSettingItem(
    title = title,
    subtitle = subtitle,
    value = "",
    icon = icon,
    enabled = true,
    onClick = onNativeClick,
    cardTitleStyle = cardTitleStyle,
    cardSubtitleStyle = cardSubtitleStyle,
    cardValueStyle = cardValueStyle,
)

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
    enabled: Boolean = true,
) = MaterialSliderSettingItem(
    title,
    subtitle,
    value,
    onValueChange,
    valueRange,
    steps,
    cardTitleStyle,
    cardSubtitleStyle,
    cardValueStyle,
    showValueAsNumber,
    valueUnit,
    categoricalLabels,
    enabled,
)

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
) = MaterialVolumeLongPressSettingItem(
    title,
    subtitle,
    checked,
    action,
    onCheckedChange,
    onActionChange,
    cardTitleStyle,
    cardSubtitleStyle,
)

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
) = MaterialAdvanceReminderSettingItem(
    title,
    subtitle,
    checked,
    minutes,
    onCheckedChange,
    onMinutesChange,
    cardTitleStyle,
    cardSubtitleStyle,
)
