package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.data.model.WidgetScheduleEntry
import com.antgskds.calendarassistant.data.model.WidgetScheduleSnapshot
import com.antgskds.calendarassistant.data.model.WidgetThemeMode
import com.antgskds.calendarassistant.data.query.LocalWidgetScheduleQueryApi
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun WidgetSettingsPage(
    settingsViewModel: SettingsViewModel,
    rawEvents: List<Event>,
    uiSize: Int = 2
) {
    val settings by settingsViewModel.settings.collectAsState()
    val scrollState = rememberScrollState()
    val haptics = rememberAppHaptics(settings.hapticFeedbackEnabled)
    var selectedSize by remember { mutableStateOf(WidgetPreviewSize.FourByTwo) }
    val queryApi = remember { LocalWidgetScheduleQueryApi() }
    val snapshot = remember(rawEvents) { queryApi.buildSnapshot(rawEvents) }
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp

    val sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .padding(bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("实时预览", style = sectionTitleStyle)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                WidgetPreviewSizeSelector(
                    selectedSize = selectedSize,
                    onSelected = {
                        haptics.selection()
                        selectedSize = it
                    }
                )
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                ) {
                    val previewWidth = maxWidth * selectedSize.widthFraction
                    val targetHeight = selectedSize.previewHeight(previewWidth)
                    val animatedHeight by animateDpAsState(
                        targetValue = targetHeight,
                        label = "widget-preview-height"
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(animatedHeight + 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        DiagonalStripePreviewBackground(modifier = Modifier.fillMaxSize())
                        WidgetPreview(
                            size = selectedSize,
                            snapshot = snapshot,
                            settings = settings,
                            modifier = Modifier.width(previewWidth)
                        )
                    }
                }
            }
        }

        Text("显示设置", style = sectionTitleStyle)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                WidgetThemeModeSetting(
                    selectedMode = settings.widgetThemeMode,
                    onSelected = { mode ->
                        haptics.selection()
                        settingsViewModel.updatePreference(widgetThemeMode = mode)
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                WidgetAlphaSetting(
                    alpha = settings.widgetBackgroundAlpha,
                    onAlphaChange = { value ->
                        settingsViewModel.updatePreference(widgetBackgroundAlpha = value)
                    }
                )
            }
        }
    }
}

@Composable
private fun DiagonalStripePreviewBackground(modifier: Modifier = Modifier) {
    val light = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f)
    val dark = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)
    Canvas(modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
        drawRect(light)
        val stripeWidth = 34.dp.toPx()
        val step = stripeWidth * 2f
        var startX = -size.height
        while (startX < size.width + size.height) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(startX, size.height)
                lineTo(startX + stripeWidth, size.height)
                lineTo(startX + stripeWidth + size.height, 0f)
                lineTo(startX + size.height, 0f)
                close()
            }
            drawPath(path, dark)
            startX += step
        }
    }
}

@Composable
private fun WidgetPreviewSizeSelector(
    selectedSize: WidgetPreviewSize,
    onSelected: (WidgetPreviewSize) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        WidgetPreviewSize.entries.forEach { size ->
            val selected = selectedSize == size
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { onSelected(size) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = size.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WidgetThemeModeSetting(
    selectedMode: Int,
    onSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("小组件主题", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
        Text(
            text = "默认跟随软件主题，也可以单独固定为浅色或深色。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            listOf(
                WidgetThemeMode.FOLLOW_APP to "跟随软件",
                WidgetThemeMode.LIGHT to "浅色",
                WidgetThemeMode.DARK to "深色"
            ).forEach { (mode, label) ->
                val selected = selectedMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onSelected(mode) }
                        .padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetAlphaSetting(
    alpha: Float,
    onAlphaChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("背景透明度", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                Text(
                    text = "调整桌面小组件背景不透明度。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                )
            }
            Text(
                text = "${(alpha * 100f).roundToInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Slider(
            value = alpha,
            onValueChange = { onAlphaChange(it.coerceIn(0.6f, 1f)) },
            valueRange = 0.6f..1f
        )
    }
}

@Composable
private fun WidgetPreview(
    size: WidgetPreviewSize,
    snapshot: WidgetScheduleSnapshot,
    settings: MySettings,
    modifier: Modifier = Modifier
) {
    val palette = rememberWidgetPreviewPalette(settings)
    when (size) {
        WidgetPreviewSize.TwoByOne -> WidgetPreviewShell(
            palette = palette,
            modifier = modifier
                .aspectRatio(2.35f)
        ) {
            val item = snapshot.visibleEntries.firstOrNull { it.date == snapshot.today }?.item
            PreviewEventRow(
                item = item,
                palette = palette,
                emptyTitle = "无日程",
                emptyTime = "今日空闲",
                modifier = Modifier.fillMaxSize(),
                showCard = false,
                large = true
            )
        }

        WidgetPreviewSize.TwoByTwo -> WidgetPreviewShell(
            palette = palette,
            modifier = modifier
                .aspectRatio(1f)
        ) {
            val todayItems = snapshot.visibleEntries.filter { it.date == snapshot.today }.map { it.item }.take(2)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                PreviewDateHeader(snapshot.today, palette, compact = true)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PreviewEventRow(
                        item = todayItems.getOrNull(0),
                        palette = palette,
                        emptyTitle = "无日程",
                        emptyTime = "今日空闲",
                        modifier = Modifier.weight(1f)
                    )
                    if (todayItems.getOrNull(1) != null) {
                        PreviewEventRow(
                            item = todayItems[1],
                            palette = palette,
                            modifier = Modifier.weight(1f),
                            includeDate = true
                        )
                    }
                }
            }
        }

        WidgetPreviewSize.FourByTwo -> WidgetPreviewShell(
            palette = palette,
            modifier = modifier.aspectRatio(2.05f)
        ) {
            val todayItems = snapshot.visibleEntries.filter { it.date == snapshot.today }
            val leftItem = todayItems.firstOrNull()?.item
            val sideItems = snapshot.visibleEntries
                .drop(if (snapshot.visibleEntries.firstOrNull()?.date == snapshot.today) 1 else 0)
                .take(2)
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    PreviewDateHeader(snapshot.today, palette)
                    PreviewEventRow(
                        item = leftItem,
                        palette = palette,
                        emptyTitle = "暂无日程安排",
                        emptyTime = null,
                        modifier = Modifier.fillMaxWidth(),
                        includeDate = true,
                        plainEmpty = true
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    sideItems.forEach { entry ->
                        if (entry.date != snapshot.today) {
                            Text(
                                text = groupLabel(entry.date),
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.secondaryText,
                                maxLines = 1
                            )
                        }
                        PreviewEventRow(
                            item = entry.item,
                            palette = palette,
                            modifier = Modifier.fillMaxWidth(),
                            includeDate = false
                        )
                    }
                }
            }
        }

        WidgetPreviewSize.FourByFour -> WidgetPreviewShell(
            palette = palette,
            modifier = modifier
                .aspectRatio(1f)
        ) {
            val entries = snapshot.visibleEntries.take(4)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                PreviewDateHeader(snapshot.today, palette)
                var previousDate: LocalDate? = null
                if (entries.isEmpty()) {
                    PreviewEventRow(
                        item = null,
                        palette = palette,
                        emptyTitle = "无日程",
                        emptyTime = "今日空闲",
                        modifier = Modifier.fillMaxWidth(),
                        showCard = false,
                        large = true
                    )
                } else {
                    entries.forEach { entry ->
                        if (entry.date != previousDate) {
                            Text(
                                text = groupLabel(entry.date),
                                style = MaterialTheme.typography.labelMedium,
                                color = palette.secondaryText,
                                maxLines = 1
                            )
                        }
                        PreviewEventRow(
                            item = entry.item,
                            palette = palette,
                            modifier = Modifier.fillMaxWidth(),
                            stackedTime = true
                        )
                        previousDate = entry.date
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPreviewShell(
    palette: WidgetPreviewPalette,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(palette.background),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun PreviewDateHeader(
    date: LocalDate,
    palette: WidgetPreviewPalette,
    compact: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = date.dayOfMonth.toString(),
            style = if (compact) MaterialTheme.typography.displaySmall else MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            color = palette.primaryText
        )
        Spacer(modifier = Modifier.width(if (compact) 8.dp else 10.dp))
        Text(
            text = weekdayText(date),
            style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = palette.primary
        )
    }
}

@Composable
private fun PreviewEventRow(
    item: ScheduleDisplayItem?,
    palette: WidgetPreviewPalette,
    modifier: Modifier = Modifier,
    emptyTitle: String = "暂无安排",
    emptyTime: String? = null,
    includeDate: Boolean = false,
    stackedTime: Boolean = false,
    showCard: Boolean = true,
    plainEmpty: Boolean = false,
    large: Boolean = false
) {
    val hasItem = item != null
    val background = when {
        !showCard -> Color.Transparent
        !hasItem && plainEmpty -> Color.Transparent
        else -> palette.card
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .padding(horizontal = if (large) 14.dp else 10.dp, vertical = if (large) 12.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (hasItem || !plainEmpty) {
            Box(
                modifier = Modifier
                    .width(if (large) 4.dp else 3.dp)
                    .height(if (large) 42.dp else 30.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(item?.safeComposeColor(palette.primary) ?: palette.secondaryText.copy(alpha = 0.32f))
            )
            Spacer(modifier = Modifier.width(if (large) 12.dp else 9.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item?.title?.takeIf { it.isNotBlank() } ?: emptyTitle,
                style = if (large) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (hasItem || !plainEmpty) palette.primaryText else palette.secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val time = item?.let { formatPreviewTime(it, includeDate) } ?: emptyTime
            if (!time.isNullOrBlank() && !stackedTime) {
                Text(
                    text = time,
                    style = if (large) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelSmall,
                    color = palette.secondaryText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (stackedTime && item != null) {
            val stacked = formatStackedPreviewTime(item)
            Column(horizontalAlignment = Alignment.End) {
                Text(stacked.first, style = MaterialTheme.typography.labelMedium, color = palette.secondaryText)
                if (stacked.second.isNotBlank()) {
                    Text(stacked.second, style = MaterialTheme.typography.labelSmall, color = palette.secondaryText)
                }
            }
        }
    }
}

@Composable
private fun rememberWidgetPreviewPalette(settings: MySettings): WidgetPreviewPalette {
    val scheme = MaterialTheme.colorScheme
    val dark = when (settings.widgetThemeMode) {
        WidgetThemeMode.LIGHT -> false
        WidgetThemeMode.DARK -> true
        else -> null
    }
    val alpha = settings.widgetBackgroundAlpha.coerceIn(0.6f, 1f)
    return when (dark) {
        false -> WidgetPreviewPalette(
            background = Color(0xFFF9F8FA).copy(alpha = alpha),
            card = Color(0xFFF0EDF3),
            primaryText = Color(0xFF1E1D22),
            secondaryText = Color(0xFF6D6873),
            primary = scheme.primary
        )
        true -> WidgetPreviewPalette(
            background = Color(0xFF1F2024).copy(alpha = alpha),
            card = Color(0xFF2B2D33),
            primaryText = Color(0xFFF5F3F7),
            secondaryText = Color(0xFFBCB7C4),
            primary = scheme.primary
        )
        null -> WidgetPreviewPalette(
            background = scheme.background.copy(alpha = alpha),
            card = scheme.surfaceContainerLow,
            primaryText = scheme.onBackground,
            secondaryText = scheme.onSurfaceVariant,
            primary = scheme.primary
        )
    }
}

private fun ScheduleDisplayItem.safeComposeColor(fallback: Color): Color {
    return if (color == 0) fallback else Color(color)
}

private fun formatPreviewTime(item: ScheduleDisplayItem, includeDate: Boolean): String {
    if (item.isAllDay) return "全天"
    val start = runCatching { LocalDateTime.of(item.startDate, item.startLocalTime) }.getOrNull() ?: return item.startTime
    val end = runCatching { LocalDateTime.of(item.endDate, item.endLocalTime) }.getOrNull()
    val startText = start.toLocalTime().format(TimeFormatter)
    val endText = end?.takeIf { it.toLocalDate() == start.toLocalDate() }?.toLocalTime()?.format(TimeFormatter)
    val range = if (endText.isNullOrBlank()) startText else "$startText - $endText"
    if (!includeDate) return range
    val prefix = when (val date = start.toLocalDate()) {
        LocalDate.now() -> "今天"
        LocalDate.now().plusDays(1) -> "明天"
        else -> date.format(ShortDateFormatter)
    }
    return "$prefix $range"
}

private fun formatStackedPreviewTime(item: ScheduleDisplayItem): Pair<String, String> {
    if (item.isAllDay) return "全天" to ""
    val start = runCatching { LocalDateTime.of(item.startDate, item.startLocalTime) }.getOrNull() ?: return item.startTime to ""
    val end = runCatching { LocalDateTime.of(item.endDate, item.endLocalTime) }.getOrNull()
    val startText = start.toLocalTime().format(TimeFormatter)
    val endText = end?.takeIf { it.toLocalDate() == start.toLocalDate() }?.toLocalTime()?.format(TimeFormatter).orEmpty()
    return startText to endText
}

private fun weekdayText(date: LocalDate): String {
    return when (date.dayOfWeek.value) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }
}

private fun groupLabel(date: LocalDate): String {
    return "${date.format(ShortDateFormatter)} ${weekdayText(date)}"
}

private data class WidgetPreviewPalette(
    val background: Color,
    val card: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val primary: Color
)

private enum class WidgetPreviewSize(val label: String, val widthFraction: Float, val aspectRatio: Float) {
    TwoByOne("2x1", 0.72f, 2.35f),
    TwoByTwo("2x2", 0.62f, 1f),
    FourByTwo("4x2", 0.92f, 2.05f),
    FourByFour("4x4", 0.86f, 1f)
}

private fun WidgetPreviewSize.previewHeight(width: Dp): Dp = width / aspectRatio

private val TimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val ShortDateFormatter = DateTimeFormatter.ofPattern("M月d日")
