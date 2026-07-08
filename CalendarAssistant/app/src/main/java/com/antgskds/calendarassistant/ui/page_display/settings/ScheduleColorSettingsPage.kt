package com.antgskds.calendarassistant.ui.page_display.settings

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.data.model.DEFAULT_EVENT_COLOR_PALETTE_HEX
import com.antgskds.calendarassistant.data.model.eventColorHexToArgb
import com.antgskds.calendarassistant.data.model.normalizeEventColorHex
import com.antgskds.calendarassistant.data.model.sanitizeEventColorPaletteHex
import com.antgskds.calendarassistant.ui.components.AppCard
import com.antgskds.calendarassistant.ui.components.AppModalBottomSheet
import com.antgskds.calendarassistant.ui.haptic.HapticValueChangeEffect
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import com.antgskds.calendarassistant.ui.haptic.sliderHapticBucket
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
    val context = LocalContext.current

    // 控制底部弹窗的显示状态
    var showAddColorSheet by remember { mutableStateOf(false) }

    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary
    )
    val cardSubtitleStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 80.dp + bottomInset),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ================== 当前色盘 ==================
            Text("当前色盘", style = sectionTitleStyle)
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "新建日程会按这个色盘轮换取色，AI/正则识别日程会从这里随机取色；已有日程颜色不会被批量修改。",
                        style = cardSubtitleStyle
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. 遍历显示当前颜色
                        colors.forEach { hex ->
                            ColorCircleItem(
                                hex = hex,
                                isDeletable = colors.size > 1,
                                isAlreadyAdded = false,
                                onDelete = {
                                    haptics.selection()
                                    viewModel.updateEventColorPalette(colors - hex)
                                }
                            )
                        }

                        // 2. 添加按钮 (+号圆圈)
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable {
                                    haptics.selection()
                                    showAddColorSheet = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "添加颜色",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ================== 默认配色 ==================
            Text("默认配色", style = sectionTitleStyle)
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "默认保留原来的 8 个莫兰迪颜色。点击色块可把缺失颜色加回当前色盘。",
                        style = cardSubtitleStyle
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        DEFAULT_EVENT_COLOR_PALETTE_HEX.forEach { hex ->
                            val isSelected = hex in colors
                            ColorCircleItem(
                                hex = hex,
                                isDeletable = false,
                                isAlreadyAdded = isSelected,
                                onClick = {
                                    if (!isSelected) {
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

        // ================== 添加颜色 BottomSheet ==================
        if (showAddColorSheet) {
            AppModalBottomSheet(
                onDismissRequest = { showAddColorSheet = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                AddColorEditorSheet(
                    onConfirm = { newHex ->
                        val normalized = normalizeEventColorHex(newHex)
                        when {
                            normalized == null -> {
                                Toast.makeText(context, "无效的颜色代码", Toast.LENGTH_SHORT).show()
                            }
                            normalized in colors -> {
                                Toast.makeText(context, "这个颜色已在色盘中", Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                haptics.confirm()
                                viewModel.updateEventColorPalette(colors + normalized)
                                showAddColorSheet = false
                            }
                        }
                    },
                    onCancel = { showAddColorSheet = false }
                )
            }
        }
    }
}

/**
 * 统一的纯色圆圈组件 (支持展示、删除角标、已选中状态)
 */
@Composable
private fun ColorCircleItem(
    hex: String,
    isDeletable: Boolean = false,
    isAlreadyAdded: Boolean = false,
    onDelete: () -> Unit = {},
    onClick: () -> Unit = {}
) {
    val color = Color(eventColorHexToArgb(hex))

    Box(
        modifier = Modifier
            .size(48.dp)
            .alpha(if (isAlreadyAdded && !isDeletable) 0.4f else 1f) // 已添加的默认颜色降低透明度
    ) {
        // 主体颜色圈
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(color)
                .clickable(enabled = !isAlreadyAdded || isDeletable) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            // 如果是已添加的默认颜色，中心打个勾
            if (isAlreadyAdded && !isDeletable) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已添加",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // 右上角删除小叉号
        if (isDeletable) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

/**
 * 底部弹窗内的颜色编辑器 (复刻主题页的 RGB + Hex)
 */
@Composable
private fun AddColorEditorSheet(
    onConfirm: (String) -> Unit,
    onCancel: () -> Unit
) {
    var hexInput by remember { mutableStateOf("#4A90E2") } // 默认选个蓝色
    var currentHex by remember { mutableStateOf("#4A90E2") }

    val color = try { Color(android.graphics.Color.parseColor(currentHex)) } catch (e: Exception) { Color.Gray }
    val red = ((color.red * 255f).roundToInt()).coerceIn(0, 255)
    val green = ((color.green * 255f).roundToInt()).coerceIn(0, 255)
    val blue = ((color.blue * 255f).roundToInt()).coerceIn(0, 255)

    val updateColor = { newHex: String ->
        currentHex = newHex
        hexInput = newHex
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("新增自定义颜色", style = MaterialTheme.typography.titleLarge)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )
        }

        // RGB 滑块
        SheetRgbSliderItem("R", red) { updateColor(rgbToHex(it, green, blue)) }
        SheetRgbSliderItem("G", green) { updateColor(rgbToHex(red, it, blue)) }
        SheetRgbSliderItem("B", blue) { updateColor(rgbToHex(red, green, it)) }

        // Hex 输入 (复用 ThemeSettingsPage 的精致样式)
        SheetHexInputItem(
            value = hexInput,
            onValueChange = { input ->
                hexInput = input
                val normalized = normalizeEventColorHex(input)
                if (normalized != null) {
                    currentHex = normalized
                }
            }
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) {
                Text("取消")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onConfirm(currentHex) }) {
                Text("确认添加")
            }
        }
    }
}

@Composable
private fun SheetRgbSliderItem(label: String, value: Int, onValueChange: (Int) -> Unit) {
    HapticValueChangeEffect(valueKey = sliderHapticBucket(value.toFloat(), 0f..255f, 0, continuousBucketCount = 16))
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.width(24.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        )
        Text(value.toString(), modifier = Modifier.width(36.dp), textAlign = TextAlign.End, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SheetHexInputItem(
    value: String,
    onValueChange: (String) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    var fieldValue by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Hex", modifier = Modifier.width(40.dp), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)

        BasicTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                fieldValue = newValue
                onValueChange(newValue.text)
            },
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End
            ),
            singleLine = true,
            interactionSource = interactionSource,
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.weight(1f)
        )
    }
}

private fun rgbToHex(red: Int, green: Int, blue: Int): String {
    return "#%02X%02X%02X".format(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))
}