package com.antgskds.calendarassistant.shared.ui.material.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics

/**
 * 单选分段控件。选项应具有唯一且稳定的值，业务只传状态、标签和状态更新回调。
 * 选中项沿用底栏编辑的 primary/onPrimary 配色；触感由组件统一处理，业务不要重复触发。
 */
@Composable
fun <T : Any> AppSegmentedControl(
    options: List<T>,
    selectedOption: T,
    onSelected: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    if (options.isEmpty()) return
    val haptics = rememberAppHaptics()
    AppGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        fallbackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(6.dp).height(IntrinsicSize.Min).selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEach { option ->
                key(option) {
                    val selected = option == selectedOption
                    val background by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        label = "segmentBackground",
                    )
                    val foreground by animateColorAsState(
                        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        label = "segmentText",
                    )
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight().heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(background)
                            .selectable(selected = selected, enabled = enabled, role = Role.RadioButton) {
                                if (!selected) {
                                    haptics.selection()
                                    onSelected(option)
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (enabled) foreground else foreground.copy(alpha = 0.38f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}
