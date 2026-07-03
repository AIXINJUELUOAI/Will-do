package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.antgskds.calendarassistant.data.model.DEFAULT_EVENT_COLOR_PALETTE_HEX
import com.antgskds.calendarassistant.data.model.eventColorHexToArgb
import com.antgskds.calendarassistant.data.model.normalizeEventColorHex
import com.antgskds.calendarassistant.data.model.sanitizeEventColorPaletteHex
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScheduleColorSettingsPage(
    viewModel: SettingsViewModel,
    uiSize: Int = 2
) {
    val settings by viewModel.settings.collectAsState()
    val colors = remember(settings.eventColorPaletteHex) {
        sanitizeEventColorPaletteHex(settings.eventColorPaletteHex)
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val haptics = rememberAppHaptics(settings.hapticFeedbackEnabled)
    var input by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun addInputColor() {
        val normalized = normalizeEventColorHex(input)
        when {
            normalized == null -> errorText = "请输入 6 位十六进制颜色，例如 #91A3B0"
            normalized in colors -> errorText = "这个颜色已在色盘中"
            else -> {
                haptics.confirm()
                viewModel.updateEventColorPalette(colors + normalized)
                input = ""
                errorText = null
            }
        }
    }

    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 80.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("当前色盘", style = sectionTitleStyle)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "新建日程会按这个色盘轮换取色，AI/正则识别日程会从这里随机取色；已有日程颜色不会被批量修改。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    colors.forEach { hex ->
                        ScheduleColorChip(
                            hex = hex,
                            deletable = colors.size > 1,
                            onDelete = {
                                haptics.selection()
                                viewModel.updateEventColorPalette(colors - hex)
                            }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = {
                            input = it
                            errorText = null
                        },
                        label = { Text("新增颜色") },
                        placeholder = { Text("#91A3B0") },
                        singleLine = true,
                        isError = errorText != null,
                        supportingText = { errorText?.let { Text(it) } },
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { addInputColor() },
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text("添加")
                    }
                }
            }
        }

        Text("默认配色", style = sectionTitleStyle)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "默认保留原来的 8 个莫兰迪颜色。点击色块可把缺失颜色加回当前色盘。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DEFAULT_EVENT_COLOR_PALETTE_HEX.forEach { hex ->
                        DefaultColorSwatch(
                            hex = hex,
                            selected = hex in colors,
                            onClick = {
                                if (hex !in colors) {
                                    haptics.selection()
                                    viewModel.updateEventColorPalette(colors + hex)
                                }
                            }
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                TextButton(
                    onClick = {
                        haptics.confirm()
                        viewModel.resetEventColorPalette()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("恢复默认色盘")
                }
            }
        }
    }
}

@Composable
private fun ScheduleColorChip(
    hex: String,
    deletable: Boolean,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(start = 8.dp, top = 6.dp, bottom = 6.dp, end = if (deletable) 2.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(eventColorHexToArgb(hex)))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = hex,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        if (deletable) {
            IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "删除颜色",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DefaultColorSwatch(
    hex: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
            )
            .clickable(onClick = onClick)
            .padding(5.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color(eventColorHexToArgb(hex)))
        )
    }
}
