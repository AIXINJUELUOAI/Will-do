package com.antgskds.calendarassistant.ui.page_display.settings

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.UiStyle
import com.antgskds.calendarassistant.ui.haptic.HapticValueChangeEffect
import com.antgskds.calendarassistant.ui.haptic.LocalAppHapticsEnabled
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import com.antgskds.calendarassistant.ui.haptic.sliderHapticBucket
import com.antgskds.calendarassistant.ui.theme.ThemeColorScheme
import com.antgskds.calendarassistant.ui.theme.normalizeThemeHexColor
import com.antgskds.calendarassistant.ui.theme.parseThemeHexColor
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun ThemeSettingsPage(
    viewModel: SettingsViewModel,
    uiSize: Int = 2
) {
    val settings by viewModel.settings.collectAsState()
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val isCustomTheme = settings.themeColorScheme == ThemeColorScheme.CUSTOM.name
    val selectedUiStyle = UiStyle.fromName(settings.uiStyle)
    val hasAppBackground = settings.appBackgroundImagePath.isNotBlank()
    var isHexFocused by remember { mutableStateOf(false) }
    var isBackgroundImporting by remember { mutableStateOf(false) }
    val backgroundImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri == null) {
            isBackgroundImporting = false
            return@rememberLauncherForActivityResult
        }
        viewModel.importAppBackground(uri) { _, message ->
            isBackgroundImporting = false
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    val navigationBottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val imeBottomPadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()
    val bottomPadding = when {
        imeBottomPadding > 0.dp -> imeBottomPadding + 96.dp
        isHexFocused -> 320.dp
        else -> navigationBottomPadding + 32.dp
    }

    LaunchedEffect(isCustomTheme, isHexFocused, imeBottomPadding) {
        if (isCustomTheme && isHexFocused) {
            delay(300)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary
    )
    val cardTitleStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
    )
    val cardSubtitleStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    )
    val cardValueStyle = MaterialTheme.typography.bodyMedium.copy(
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    CompositionLocalProvider(LocalAppHapticsEnabled provides settings.hapticFeedbackEnabled) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .padding(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 主题模式
        Text("外观", style = sectionTitleStyle)
        AppBackgroundGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                ThemeModeSliderSettingItem(
                    title = "主题模式",
                    subtitle = "选择主题模式",
                    value = settings.themeMode,
                    onValueChange = { viewModel.updateThemeMode(it) },
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle,
                    cardValueStyle = cardValueStyle
                )
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (hasAppBackground) 0.25f else 0.5f)
                )
                UiStyleSettingItem(
                    selectedStyle = selectedUiStyle,
                    onStyleSelected = { viewModel.updateUiStyle(it.name) },
                    cardTitleStyle = cardTitleStyle,
                    cardSubtitleStyle = cardSubtitleStyle
                )
            }
        }

        Text("背景", style = sectionTitleStyle)
        AppBackgroundSettingsCard(
            settings = settings,
            isImporting = isBackgroundImporting,
            onPickImage = {
                isBackgroundImporting = true
                backgroundImagePicker.launch("image/*")
            },
            onClearImage = {
                viewModel.clearAppBackground()
                Toast.makeText(context, "主界面壁纸已清除", Toast.LENGTH_SHORT).show()
            },
            glassMode = hasAppBackground,
            cardTitleStyle = cardTitleStyle,
            cardSubtitleStyle = cardSubtitleStyle
        )
        AppBackgroundImageColorSwitchCard(
            settings = settings,
            onImageColorEnabledChange = { enabled ->
                viewModel.updateAppBackgroundImageColorEnabled(enabled) { _, message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            },
            glassMode = hasAppBackground,
            cardTitleStyle = cardTitleStyle,
            cardSubtitleStyle = cardSubtitleStyle
        )
        AppBackgroundWallpaperBlurSwitchCard(
            settings = settings,
            onWallpaperBlurEnabledChange = viewModel::updateAppBackgroundWallpaperBlurEnabled,
            glassMode = hasAppBackground,
            cardTitleStyle = cardTitleStyle,
            cardSubtitleStyle = cardSubtitleStyle
        )
        // 主题颜色
        Text("主题颜色", style = sectionTitleStyle)
        AppBackgroundGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "选择应用主题颜色",
                    style = cardTitleStyle,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = "选择固定配色方案，或使用跟随系统的动态配色",
                    style = cardSubtitleStyle,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                ThemeColorModeSwitch(
                    customSelected = isCustomTheme,
                    onPresetClick = {
                        if (isCustomTheme) viewModel.updateThemeColorScheme(ThemeColorScheme.DEFAULT.name)
                    },
                    onCustomClick = {
                        if (!isCustomTheme) viewModel.updateThemeColorScheme(ThemeColorScheme.CUSTOM.name)
                    }
                )

                AnimatedVisibility(
                    visible = !isCustomTheme,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(4),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.height(320.dp).padding(top = 16.dp)
                    ) {
                        items(ThemeColorScheme.entries.filter { it != ThemeColorScheme.CUSTOM }) { scheme ->
                            ThemeColorSchemeItem(
                                scheme = scheme,
                                displayColor = getDisplayColor(scheme, context),
                                isSelected = settings.themeColorScheme == scheme.name,
                                onClick = { viewModel.updateThemeColorScheme(scheme.name) }
                            )
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isCustomTheme,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    CustomThemeColorEditor(
                        hex = settings.customThemeColorHex,
                        onColorChange = viewModel::updateCustomThemeColorHex,
                        onHexFocusChange = { isHexFocused = it },
                        cardTitleStyle = cardTitleStyle,
                        cardSubtitleStyle = cardSubtitleStyle,
                        cardValueStyle = cardValueStyle
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun UiStyleSettingItem(
    selectedStyle: UiStyle,
    onStyleSelected: (UiStyle) -> Unit,
    cardTitleStyle: androidx.compose.ui.text.TextStyle,
    cardSubtitleStyle: androidx.compose.ui.text.TextStyle
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("界面风格", style = cardTitleStyle)
        Text("选择主界面和悬浮窗的前端样式", style = cardSubtitleStyle)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            UiStyle.entries.forEach { style ->
                UiStyleOptionChip(
                    style = style,
                    selected = selectedStyle == style,
                    onClick = { onStyleSelected(style) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun UiStyleOptionChip(
    style: UiStyle,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAppHaptics()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable { haptics.selection(); onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = style.label,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun AppBackgroundSettingsCard(
    settings: MySettings,
    isImporting: Boolean,
    onPickImage: () -> Unit,
    onClearImage: () -> Unit,
    glassMode: Boolean,
    cardTitleStyle: androidx.compose.ui.text.TextStyle,
    cardSubtitleStyle: androidx.compose.ui.text.TextStyle
) {
    val hasImage = settings.appBackgroundImagePath.isNotBlank()
    AppBackgroundGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("主界面壁纸", style = cardTitleStyle)
                Text(
                    text = if (hasImage) "已设置图片壁纸" else "选择一张图片作为软件背景",
                    style = cardSubtitleStyle
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Button(
                    onClick = onPickImage,
                    enabled = !isImporting
                ) {
                    Text(
                        text = when {
                            isImporting -> "导入中..."
                            hasImage -> "更换图片"
                            else -> "选择图片"
                        }
                    )
                }
                if (hasImage) {
                    TextButton(
                        onClick = onClearImage,
                        enabled = !isImporting,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Text(
                            text = "清除图片",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppBackgroundImageColorSwitchCard(
    settings: MySettings,
    onImageColorEnabledChange: (Boolean) -> Unit,
    glassMode: Boolean,
    cardTitleStyle: androidx.compose.ui.text.TextStyle,
    cardSubtitleStyle: androidx.compose.ui.text.TextStyle
) {
    val hasImage = settings.appBackgroundImagePath.isNotBlank()
    AppBackgroundGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("图片取色", style = cardTitleStyle)
                Text(
                    text = if (hasImage) "从当前壁纸提取主题色；关闭后恢复系统取色" else "请先选择主界面壁纸",
                    style = cardSubtitleStyle
                )
            }
            Switch(
                checked = settings.appBackgroundImageColorEnabled && hasImage,
                enabled = hasImage,
                onCheckedChange = onImageColorEnabledChange
            )
        }
    }
}

@Composable
private fun AppBackgroundWallpaperBlurSwitchCard(
    settings: MySettings,
    onWallpaperBlurEnabledChange: (Boolean) -> Unit,
    glassMode: Boolean,
    cardTitleStyle: androidx.compose.ui.text.TextStyle,
    cardSubtitleStyle: androidx.compose.ui.text.TextStyle
) {
    val hasImage = settings.appBackgroundImagePath.isNotBlank()
    AppBackgroundGlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("壁纸模糊", style = cardTitleStyle)
                Text(
                    text = if (hasImage) "单独控制背景图片层模糊；不影响玻璃组件的 MIUI 风格测试" else "请先选择主界面壁纸",
                    style = cardSubtitleStyle
                )
            }
            Switch(
                checked = settings.appBackgroundWallpaperBlurEnabled && hasImage,
                enabled = hasImage,
                onCheckedChange = onWallpaperBlurEnabledChange
            )
        }
    }
}

@Composable
private fun ThemeModeSliderSettingItem(
    title: String,
    subtitle: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    cardTitleStyle: androidx.compose.ui.text.TextStyle,
    cardSubtitleStyle: androidx.compose.ui.text.TextStyle,
    cardValueStyle: androidx.compose.ui.text.TextStyle
) {
    HapticValueChangeEffect(valueKey = value)
    val modeLabels = mapOf(1 to "跟随系统", 2 to "浅色模式", 3 to "深色模式")
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = cardTitleStyle)
                Text(subtitle, style = cardSubtitleStyle)
            }
            Text(
                text = modeLabels[value] ?: "",
                style = cardValueStyle
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = 1f..3f,
            steps = 1
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "跟随系统", style = cardSubtitleStyle)
            Text(text = "浅色模式", style = cardSubtitleStyle)
            Text(text = "深色模式", style = cardSubtitleStyle)
        }
    }
}

@Composable
private fun getDisplayColor(scheme: ThemeColorScheme, context: android.content.Context): Color {
    return when (scheme) {
        ThemeColorScheme.DEFAULT -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val systemColorScheme = androidx.compose.material3.dynamicLightColorScheme(context)
                systemColorScheme.primary
            } else {
                Color(0xFF6750A4)
            }
        }
        else -> scheme.primaryColor
    }
}

@Composable
private fun ThemeColorModeSwitch(
    customSelected: Boolean,
    onPresetClick: () -> Unit,
    onCustomClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ThemeColorModeChip(
            text = "预设",
            selected = !customSelected,
            onClick = onPresetClick,
            modifier = Modifier.weight(1f)
        )
        ThemeColorModeChip(
            text = "自定义",
            selected = customSelected,
            onClick = onCustomClick,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ThemeColorModeChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAppHaptics()
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable { haptics.selection(); onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CustomThemeColorEditor(
    hex: String,
    onColorChange: (String) -> Unit,
    onHexFocusChange: (Boolean) -> Unit,
    cardTitleStyle: androidx.compose.ui.text.TextStyle,
    cardSubtitleStyle: androidx.compose.ui.text.TextStyle,
    cardValueStyle: androidx.compose.ui.text.TextStyle
) {
    val normalizedHex = normalizeThemeHexColor(hex) ?: "#6750A4"
    val color = parseThemeHexColor(normalizedHex)
    val red = ((color.red * 255f).roundToInt()).coerceIn(0, 255)
    val green = ((color.green * 255f).roundToInt()).coerceIn(0, 255)
    val blue = ((color.blue * 255f).roundToInt()).coerceIn(0, 255)
    var hexInput by remember(normalizedHex) { mutableStateOf(normalizedHex) }
    var inputError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("自定义颜色", style = cardTitleStyle)
                Text("通过 RGB 或 Hex 设置主题色", style = cardSubtitleStyle)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(normalizedHex, style = cardValueStyle)
                Spacer(modifier = Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f), CircleShape)
                )
            }
        }

        RgbSliderItem("R", red, cardTitleStyle, cardValueStyle) { value ->
            onColorChange(rgbToHex(value, green, blue))
        }
        RgbSliderItem("G", green, cardTitleStyle, cardValueStyle) { value ->
            onColorChange(rgbToHex(red, value, blue))
        }
        RgbSliderItem("B", blue, cardTitleStyle, cardValueStyle) { value ->
            onColorChange(rgbToHex(red, green, value))
        }

        HexColorInputItem(
            value = hexInput,
            isError = inputError,
            onValueChange = { input ->
                hexInput = input
                val normalized = normalizeThemeHexColor(input)
                inputError = input.isNotBlank() && normalized == null
                if (normalized != null) {
                    onColorChange(normalized)
                }
            },
            onFocusChange = onHexFocusChange,
            cardTitleStyle = cardTitleStyle,
            cardValueStyle = cardValueStyle,
            cardSubtitleStyle = cardSubtitleStyle
        )
    }
}

@Composable
private fun RgbSliderItem(
    label: String,
    value: Int,
    cardTitleStyle: androidx.compose.ui.text.TextStyle,
    cardValueStyle: androidx.compose.ui.text.TextStyle,
    onValueChange: (Int) -> Unit
) {
    HapticValueChangeEffect(valueKey = sliderHapticBucket(value.toFloat(), 0f..255f, 0, continuousBucketCount = 16))
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = cardTitleStyle)
            Text(value.toString(), style = cardValueStyle)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.roundToInt().coerceIn(0, 255)) },
            valueRange = 0f..255f,
            steps = 0
        )
    }
}

@Composable
private fun HexColorInputItem(
    value: String,
    isError: Boolean,
    onValueChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    cardTitleStyle: androidx.compose.ui.text.TextStyle,
    cardValueStyle: androidx.compose.ui.text.TextStyle,
    cardSubtitleStyle: androidx.compose.ui.text.TextStyle
) {
    var isFocused by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    var fieldValue by remember(value) {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(text = value, selection = TextRange(value.length))
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Hex", style = cardTitleStyle, modifier = Modifier.width(100.dp))
            BasicTextField(
                value = fieldValue,
                onValueChange = { newValue ->
                    val previousText = fieldValue.text
                    val filtered = filterThemeHexInput(newValue.text)
                    val selection = remapThemeHexSelection(newValue.text, newValue.selection)
                    fieldValue = TextFieldValue(text = filtered, selection = selection)
                    if (filtered != previousText) {
                        onValueChange(filtered)
                    }
                },
                textStyle = cardValueStyle.copy(
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.End
                ),
                singleLine = true,
                interactionSource = interactionSource,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        isFocused = focusState.isFocused
                        onFocusChange(focusState.isFocused)
                    },
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 24.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        if (fieldValue.text.isEmpty()) {
                            Text(
                                "#6750A4",
                                style = cardSubtitleStyle,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                textAlign = TextAlign.End,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        innerTextField()
                    }
                }
            )
        }
        if (isError) {
            Text(
                "请输入 6 位十六进制颜色",
                style = cardSubtitleStyle,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

private fun filterThemeHexInput(text: String): String {
    val builder = StringBuilder(7)
    for (char in text) {
        if (builder.length >= 7) break
        if (isThemeHexInputChar(char)) builder.append(char)
    }
    return builder.toString()
}

private fun remapThemeHexSelection(text: String, selection: TextRange): TextRange {
    val start = filteredThemeHexOffset(text, selection.start)
    val end = filteredThemeHexOffset(text, selection.end)
    return TextRange(start, end)
}

private fun filteredThemeHexOffset(text: String, offset: Int): Int {
    var filteredOffset = 0
    val safeOffset = offset.coerceIn(0, text.length)
    for (index in 0 until safeOffset) {
        if (filteredOffset >= 7) break
        if (isThemeHexInputChar(text[index])) filteredOffset++
    }
    return filteredOffset
}

private fun isThemeHexInputChar(char: Char): Boolean {
    return char == '#' || char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'
}

private fun rgbToHex(red: Int, green: Int, blue: Int): String {
    return "#%02X%02X%02X".format(red.coerceIn(0, 255), green.coerceIn(0, 255), blue.coerceIn(0, 255))
}

@Composable
private fun ThemeColorSchemeItem(
    scheme: ThemeColorScheme,
    displayColor: Color,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val haptics = rememberAppHaptics()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { haptics.selection(); onClick() }
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(displayColor)
                .then(
                    if (isSelected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    } else {
                        Modifier.border(1.dp, Color.Gray.copy(alpha = 0.3f), CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "已选择",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = scheme.displayName,
            style = MaterialTheme.typography.bodySmall,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
