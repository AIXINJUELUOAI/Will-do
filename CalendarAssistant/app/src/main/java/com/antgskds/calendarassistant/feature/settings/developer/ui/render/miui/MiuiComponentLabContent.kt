package com.antgskds.calendarassistant.feature.settings.developer.ui.render.miui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val HyperBlue = Color(0xFF3482FF)

private data class LabColors(
    val background: Color,
    val surface: Color,
    val popup: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val divider: Color,
    val inactive: Color,
)

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MiuiComponentLabContent(uiSize: Int = 2) {
    var darkMode by remember { mutableStateOf(true) }
    var switchChecked by remember { mutableStateOf(true) }
    var checkboxChecked by remember { mutableStateOf(true) }
    var radioIndex by remember { mutableIntStateOf(0) }
    var sliderValue by remember { mutableFloatStateOf(62f) }
    var inputText by remember { mutableStateOf("明天下午两点开会") }
    var showDialog by remember { mutableStateOf(false) }
    var showBottomDialog by remember { mutableStateOf(false) }
    var showBottomSheet by remember { mutableStateOf(false) }
    var dropdownIndex by remember { mutableIntStateOf(1) }
    val colors = if (darkMode) {
        LabColors(
            background = Color.Black,
            surface = Color(0xFF1D1D1F),
            popup = Color(0xFF2C2C2E),
            primaryText = Color(0xFFF4F4F6),
            secondaryText = Color(0xFF98989F),
            divider = Color.White.copy(alpha = 0.08f),
            inactive = Color(0xFF4A4A4E),
        )
    } else {
        LabColors(
            background = Color(0xFFF4F4F6),
            surface = Color.White,
            popup = Color.White,
            primaryText = Color(0xFF1D1D1F),
            secondaryText = Color(0xFF7A7A80),
            divider = Color.Black.copy(alpha = 0.08f),
            inactive = Color(0xFFD5D5D9),
        )
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp)
                .padding(bottom = bottomInset),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "MIUI 组件实验",
                color = colors.primaryText,
                fontSize = if (uiSize >= 3) 32.sp else 28.sp,
                fontWeight = FontWeight.Bold,
            )

            LabSectionTitle("主题与字体", colors)
            LabCard(colors) {
                LabThemeSelector(darkMode = darkMode, colors = colors, onDarkModeChange = { darkMode = it })
                LabDivider(colors)
                Column(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("页面标题", color = colors.primaryText, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text("设置项标题", color = colors.primaryText, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text("辅助说明与当前状态", color = colors.secondaryText, fontSize = 14.sp)
                    Text("2026 / 07 / 20", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            LabSectionTitle("设置项", colors)
            LabCard(colors) {
                HyperosDropdownPreference(
                    title = "打开滑动键快速切换至",
                    items = listOf("游戏加速", "声音模式", "手电筒", "录音", "对话翻译", "语音助手", "同声传译", "选择应用"),
                    selectedIndex = dropdownIndex,
                    colors = colors,
                    onSelected = { dropdownIndex = it },
                )
                LabDivider(colors)
                LabSwitchPreference(
                    title = "实时提醒",
                    summary = "在状态栏显示日程实况",
                    checked = switchChecked,
                    colors = colors,
                    onCheckedChange = { switchChecked = it },
                )
                LabDivider(colors)
                LabActionPreference("更多设置", "进入二级页面", colors)
            }

            LabSectionTitle("状态反馈", colors)
            LabStatusCard(colors)

            LabSectionTitle("选择与数值", colors)
            LabCard(colors) {
                LabCheckPreference(
                    title = "启用智能识别",
                    checked = checkboxChecked,
                    colors = colors,
                    onCheckedChange = { checkboxChecked = it },
                )
                LabDivider(colors)
                LabRadioPreference("标准模式", radioIndex == 0, colors) { radioIndex = 0 }
                LabDivider(colors)
                LabRadioPreference("增强模式", radioIndex == 1, colors) { radioIndex = 1 }
                LabDivider(colors)
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    label = { Text("输入内容") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.primaryText,
                        unfocusedTextColor = colors.primaryText,
                        focusedBorderColor = HyperBlue,
                        unfocusedBorderColor = colors.divider,
                        focusedLabelColor = HyperBlue,
                        unfocusedLabelColor = colors.secondaryText,
                        cursorColor = HyperBlue,
                    ),
                )
                LabDivider(colors)
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("提醒强度", color = colors.primaryText, fontSize = 17.sp)
                        Text("${sliderValue.toInt()}%", color = HyperBlue, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    }
                    Slider(
                        value = sliderValue,
                        onValueChange = { sliderValue = it },
                        valueRange = 0f..100f,
                        colors = SliderDefaults.colors(
                            thumbColor = HyperBlue,
                            activeTrackColor = HyperBlue,
                            inactiveTrackColor = colors.inactive,
                        ),
                    )
                }
            }

            LabSectionTitle("弹窗与操作", colors)
            LabCard(colors) {
                LabActionPreference("普通确认弹窗", "居中显示", colors) { showDialog = true }
                LabDivider(colors)
                LabActionPreference("底部确认弹窗", "贴近底部显示", colors) { showBottomDialog = true }
                LabDivider(colors)
                LabActionPreference("可拖拽底部弹窗", "用于承载连续设置内容", colors) { showBottomSheet = true }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LabButton("次要操作", colors, primary = false, modifier = Modifier.weight(1f)) {}
                LabButton("主要操作", colors, primary = true, modifier = Modifier.weight(1f)) {}
            }
        }
    }

    if (showDialog) {
        LabConfirmDialog(colors = colors, bottom = false, onDismiss = { showDialog = false })
    }
    if (showBottomDialog) {
        LabConfirmDialog(colors = colors, bottom = true, onDismiss = { showBottomDialog = false })
    }
    if (showBottomSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            containerColor = colors.popup,
            contentColor = colors.primaryText,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 6.dp)
                        .size(width = 38.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(colors.secondaryText.copy(alpha = 0.45f)),
                )
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = bottomInset + 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text("底部设置", color = colors.primaryText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("拖动顶部把手可调整或关闭弹窗。", color = colors.secondaryText, fontSize = 15.sp)
                LabSwitchPreference(
                    title = "保持实况提醒",
                    summary = "关闭弹窗后继续保留当前状态",
                    checked = switchChecked,
                    colors = colors,
                    onCheckedChange = { switchChecked = it },
                )
                LabButton("完成", colors, primary = true, modifier = Modifier.fillMaxWidth()) {
                    showBottomSheet = false
                }
            }
        }
    }
}

@Composable
private fun LabSectionTitle(text: String, colors: LabColors) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 6.dp),
        color = colors.secondaryText,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun LabCard(colors: LabColors, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface),
        content = content,
    )
}

@Composable
private fun LabDivider(colors: LabColors) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp)
            .height(1.dp)
            .background(colors.divider),
    )
}

@Composable
private fun LabStatusCard(colors: LabColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(HyperBlue.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Check, null, tint = HyperBlue, modifier = Modifier.size(22.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("设置已保存", color = colors.primaryText, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            Text("新的组件样式已应用", color = colors.secondaryText, fontSize = 14.sp)
        }
    }
}

@Composable
private fun LabThemeSelector(darkMode: Boolean, colors: LabColors, onDarkModeChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.background),
    ) {
        listOf(false to "浅色", true to "深色").forEach { (dark, label) ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(3.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (darkMode == dark) colors.surface else Color.Transparent)
                    .clickable { onDarkModeChange(dark) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (darkMode == dark) HyperBlue else colors.secondaryText, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun HyperosDropdownPreference(
    title: String,
    items: List<String>,
    selectedIndex: Int,
    colors: LabColors,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var anchorPosition by remember { mutableStateOf(Offset.Zero) }
    var anchorSize by remember { mutableStateOf(IntSize.Zero) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned {
                anchorPosition = it.positionInWindow()
                anchorSize = it.size
            }
            .clickable { expanded = true }
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            color = colors.primaryText,
            fontSize = 17.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(items[selectedIndex], color = colors.secondaryText, fontSize = 15.sp)
        Icon(
            imageVector = Icons.Rounded.UnfoldMore,
            contentDescription = null,
            tint = colors.secondaryText,
            modifier = Modifier.size(18.dp),
        )
    }

    if (expanded) {
        val density = LocalDensity.current
        val popupTop = with(density) {
            (anchorPosition.y + anchorSize.height * 0.55f).toDp()
        }
        Dialog(
            onDismissRequest = { expanded = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { expanded = false },
                contentAlignment = Alignment.TopEnd,
            ) {
                Column(
                    modifier = Modifier
                        .padding(top = popupTop, end = 20.dp)
                        .width(208.dp)
                        .heightIn(max = 440.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.popup)
                        .verticalScroll(rememberScrollState())
                        .clickable(enabled = false) {},
                ) {
                    items.forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelected(index)
                                    expanded = false
                                }
                                .padding(horizontal = 20.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = item,
                                modifier = Modifier.weight(1f),
                                color = if (index == selectedIndex) HyperBlue else colors.primaryText,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            if (index == selectedIndex) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = HyperBlue,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LabSwitchPreference(
    title: String,
    summary: String,
    checked: Boolean,
    colors: LabColors,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = colors.primaryText, fontSize = 17.sp)
            Text(summary, color = colors.secondaryText, fontSize = 14.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = HyperBlue,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = colors.inactive,
                uncheckedBorderColor = Color.Transparent,
            ),
        )
    }
}

@Composable
private fun LabActionPreference(title: String, summary: String, colors: LabColors, onClick: () -> Unit = {}) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = colors.primaryText, fontSize = 17.sp)
            Text(summary, color = colors.secondaryText, fontSize = 14.sp)
        }
        Icon(Icons.Rounded.ChevronRight, null, tint = colors.secondaryText, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun LabCheckPreference(title: String, checked: Boolean, colors: LabColors, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), color = colors.primaryText, fontSize = 17.sp)
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = CheckboxDefaults.colors(checkedColor = HyperBlue, uncheckedColor = colors.secondaryText),
        )
    }
}

@Composable
private fun LabRadioPreference(title: String, selected: Boolean, colors: LabColors, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, modifier = Modifier.weight(1f), color = colors.primaryText, fontSize = 17.sp)
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = HyperBlue, unselectedColor = colors.secondaryText),
        )
    }
}

@Composable
private fun LabButton(text: String, colors: LabColors, primary: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (primary) HyperBlue else colors.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (primary) Color.White else colors.primaryText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LabConfirmDialog(colors: LabColors, bottom: Boolean, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = !bottom),
    ) {
        Box(
            modifier = if (bottom) Modifier.fillMaxSize().padding(16.dp) else Modifier,
            contentAlignment = if (bottom) Alignment.BottomCenter else Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .then(if (bottom) Modifier.fillMaxWidth() else Modifier.width(320.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.popup)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("确认操作", color = colors.primaryText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("继续后将应用当前实验设置。此弹窗用于校准 HyperOS 的间距、圆角和按钮样式。", color = colors.secondaryText, fontSize = 15.sp, lineHeight = 22.sp)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LabButton("取消", colors, false, Modifier.weight(1f), onDismiss)
                    LabButton("确认", colors, true, Modifier.weight(1f), onDismiss)
                }
            }
        }
    }
}
