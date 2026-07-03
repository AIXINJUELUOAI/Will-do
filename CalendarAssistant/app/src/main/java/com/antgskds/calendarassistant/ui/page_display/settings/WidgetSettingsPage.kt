package com.antgskds.calendarassistant.ui.page_display.settings

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.data.model.WeatherDailyForecast
import com.antgskds.calendarassistant.data.model.WeatherData
import com.antgskds.calendarassistant.data.model.WeatherHourlyForecast
import com.antgskds.calendarassistant.data.model.WidgetScheduleEntry
import com.antgskds.calendarassistant.data.model.WidgetScheduleSnapshot
import com.antgskds.calendarassistant.data.model.WidgetThemeMode
import com.antgskds.calendarassistant.data.model.displayLocationName
import com.antgskds.calendarassistant.data.query.LocalWidgetScheduleQueryApi
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import com.antgskds.calendarassistant.platform.widget.CourseWidgetProvider
import com.antgskds.calendarassistant.platform.widget.CourseWidgetSnapshot
import com.antgskds.calendarassistant.platform.widget.CourseWidgetSnapshotBuilder
import com.antgskds.calendarassistant.platform.widget.ScheduleWidgetProvider
import com.antgskds.calendarassistant.platform.widget.WeatherWidgetProvider
import com.antgskds.calendarassistant.platform.widget.WidgetAppearanceConfig
import com.antgskds.calendarassistant.platform.widget.WidgetInstanceConfigStore
import com.antgskds.calendarassistant.platform.widget.WidgetType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun WidgetSettingsPage(
    settingsViewModel: SettingsViewModel,
    rawEvents: List<Event>,
    uiSize: Int = 2
) {
    val context = LocalContext.current
    val app = context.applicationContext as? App
    val settings by settingsViewModel.settings.collectAsState()
    val weatherData by app?.weatherQueryApi?.weatherData?.collectAsState() ?: remember { mutableStateOf<WeatherData?>(null) }
    val scrollState = rememberScrollState()
    val haptics = rememberAppHaptics(settings.hapticFeedbackEnabled)
    var selectedType by remember { mutableStateOf(WidgetType.SCHEDULE) }
    var selectedSize by remember { mutableStateOf(WidgetPreviewSize.FourByTwo) }
    var appearanceRefreshKey by remember { mutableStateOf(0) }
    val queryApi = remember { LocalWidgetScheduleQueryApi() }
    val snapshot = remember(rawEvents) { queryApi.buildSnapshot(rawEvents) }
    val courseSnapshot = remember(rawEvents, settings) { CourseWidgetSnapshotBuilder.build(rawEvents, settings) }
    val configStore = remember(context) { WidgetInstanceConfigStore(context) }
    val selectedAppearance = remember(context, settings, selectedType, appearanceRefreshKey) {
        configStore.defaultAppearance(selectedType, settings)
    }
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 24.dp

    fun updateSelectedAppearance(appearance: WidgetAppearanceConfig) {
        saveWidgetTypeAppearance(context, selectedType, settings, appearance)
        appearanceRefreshKey++
    }

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
                WidgetTypeSelector(
                    selectedType = selectedType,
                    onSelected = {
                        haptics.selection()
                        selectedType = it
                    }
                )
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
                        WidgetTypePreview(
                            type = selectedType,
                            size = selectedSize,
                            snapshot = snapshot,
                            courseSnapshot = courseSnapshot,
                            weatherData = weatherData,
                            settings = settings,
                            appearance = selectedAppearance,
                            modifier = Modifier.width(previewWidth)
                        )
                    }
                }
            }
        }

        Text("默认显示设置", style = sectionTitleStyle)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.padding(vertical = 8.dp)) {
                WidgetThemeModeSetting(
                    widgetType = selectedType,
                    selectedMode = selectedAppearance.themeMode,
                    onSelected = { mode ->
                        haptics.selection()
                        updateSelectedAppearance(selectedAppearance.copy(themeMode = mode))
                    }
                )
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                )
                WidgetAlphaSetting(
                    widgetType = selectedType,
                    alpha = selectedAppearance.backgroundAlpha,
                    onAlphaChange = { value ->
                        updateSelectedAppearance(selectedAppearance.copy(backgroundAlpha = value))
                    }
                )
            }
        }
    }
}

private fun saveWidgetTypeAppearance(
    context: Context,
    type: WidgetType,
    settings: MySettings,
    appearance: WidgetAppearanceConfig
) {
    val store = WidgetInstanceConfigStore(context)
    val normalized = appearance.normalized()
    store.saveDefaultAppearance(type, normalized)
    val manager = AppWidgetManager.getInstance(context)
    val appWidgetIds = manager.getAppWidgetIds(ComponentName(context, widgetProviderClass(type)))
    appWidgetIds.forEach { appWidgetId ->
        val current = store.getConfig(appWidgetId, type, settings)
        store.saveConfig(appWidgetId, current.copy(appearance = normalized))
    }
    (context.applicationContext as? App)?.widgetCenter?.refreshWidgets(manager, appWidgetIds, type)
}

private fun widgetProviderClass(type: WidgetType): Class<*> {
    return when (type) {
        WidgetType.SCHEDULE -> ScheduleWidgetProvider::class.java
        WidgetType.WEATHER -> WeatherWidgetProvider::class.java
        WidgetType.COURSE -> CourseWidgetProvider::class.java
    }
}

private fun widgetTypeShortLabel(type: WidgetType): String {
    return when (type) {
        WidgetType.SCHEDULE -> "日程"
        WidgetType.WEATHER -> "天气"
        WidgetType.COURSE -> "课表"
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
private fun WidgetTypeSelector(
    selectedType: WidgetType,
    onSelected: (WidgetType) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        WidgetType.entries.forEach { type ->
            val selected = selectedType == type
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { onSelected(type) }
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = widgetTypeShortLabel(type),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
    widgetType: WidgetType,
    selectedMode: Int,
    onSelected: (Int) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("${widgetTypeShortLabel(widgetType)}主题", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
        Text(
            text = "作为新添加${widgetType.displayName}的默认主题，并同步到已添加的同类小组件。",
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
    widgetType: WidgetType,
    alpha: Float,
    onAlphaChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${widgetTypeShortLabel(widgetType)}背景透明度", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                Text(
                    text = "作为新添加${widgetType.displayName}的默认不透明度，并同步到已添加的同类小组件。",
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
private fun WidgetTypePreview(
    type: WidgetType,
    size: WidgetPreviewSize,
    snapshot: WidgetScheduleSnapshot,
    courseSnapshot: CourseWidgetSnapshot,
    weatherData: WeatherData?,
    settings: MySettings,
    appearance: WidgetAppearanceConfig,
    modifier: Modifier = Modifier
) {
    val previewSettings = settings.copy(
        widgetThemeMode = appearance.themeMode,
        widgetBackgroundAlpha = appearance.backgroundAlpha
    )
    when (type) {
        WidgetType.SCHEDULE -> WidgetPreview(size, snapshot, previewSettings, modifier)
        WidgetType.WEATHER -> WeatherPreview(size, weatherData ?: sampleWeatherData(), previewSettings, modifier)
        WidgetType.COURSE -> CoursePreview(size, courseSnapshot, previewSettings, modifier)
    }
}

@Composable
private fun WeatherPreview(
    size: WidgetPreviewSize,
    data: WeatherData,
    settings: MySettings,
    modifier: Modifier = Modifier
) {
    val palette = rememberWidgetPreviewPalette(settings)
    val daily = data.dailyForecast.firstOrNull()
    val range = daily?.let { "${it.tempMin.ifBlank { "--" }}° / ${it.tempMax.ifBlank { "--" }}°" } ?: "16° / 24°"
    val location = data.displayLocationName(short = true)
    WidgetPreviewShell(palette = palette, modifier = modifier.aspectRatio(size.aspectRatio)) {
        when (size) {
            WidgetPreviewSize.TwoByOne -> Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("${data.temperature.ifBlank { "20" }}° ${data.text.ifBlank { "雾" }}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = palette.primaryText, maxLines = 1)
                    Text(range, style = MaterialTheme.typography.labelMedium, color = palette.secondaryText, maxLines = 1)
                }
                Text(data.text.ifBlank { "天气" }.take(2), style = MaterialTheme.typography.titleMedium, color = palette.primary)
            }

            WidgetPreviewSize.TwoByTwo -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(location, style = MaterialTheme.typography.titleMedium, color = palette.primaryText, maxLines = 1)
                    Text(data.text.ifBlank { "天气" }.take(2), style = MaterialTheme.typography.titleMedium, color = palette.primary)
                }
                Text("${data.temperature.ifBlank { "20" }}°", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Black, color = palette.primaryText)
                Text("${data.text.ifBlank { "雾" }} $range", style = MaterialTheme.typography.bodyLarge, color = palette.secondaryText, maxLines = 1)
            }

            WidgetPreviewSize.FourByTwo -> WeatherWidePreview(data, palette, range, compact = true)
            WidgetPreviewSize.FourByFour -> WeatherWidePreview(data, palette, range, compact = false)
        }
    }
}

@Composable
private fun WeatherWidePreview(
    data: WeatherData,
    palette: WidgetPreviewPalette,
    range: String,
    compact: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(data.displayLocationName(short = true), style = MaterialTheme.typography.labelLarge, color = palette.primaryText, maxLines = 1)
                Text("${data.temperature.ifBlank { "20" }}°", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = palette.primaryText)
            }
            Text("${data.text.ifBlank { "雾" }} $range", style = MaterialTheme.typography.bodyMedium, color = palette.secondaryText, maxLines = 1)
        }
        WeatherHourlyRow(data.hourlyForecast.ifEmpty { sampleWeatherData().hourlyForecast }, palette)
        if (!compact) {
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(palette.secondaryText.copy(alpha = 0.18f)))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                data.dailyForecast.ifEmpty { sampleWeatherData().dailyForecast }.take(5).forEachIndexed { index, daily ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(dailyLabel(index, daily.fxDate), style = MaterialTheme.typography.bodyMedium, color = palette.primaryText)
                        Text(daily.textDay.ifBlank { "多云" }, style = MaterialTheme.typography.bodyMedium, color = palette.secondaryText)
                        Text("${daily.tempMin.ifBlank { "--" }}° / ${daily.tempMax.ifBlank { "--" }}°", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = palette.primaryText)
                    }
                }
            }
        }
    }
}

@Composable
private fun WeatherHourlyRow(hours: List<WeatherHourlyForecast>, palette: WidgetPreviewPalette) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        hours.take(6).forEachIndexed { index, hour ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (index == 0) "现在" else hourLabel(hour.fxTime), style = MaterialTheme.typography.labelSmall, color = palette.secondaryText, maxLines = 1)
                Text(hour.text.ifBlank { "多云" }.take(2), style = MaterialTheme.typography.labelMedium, color = palette.primary)
                Text("${hour.temp.ifBlank { "20" }}°", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = palette.primaryText)
            }
        }
    }
}

@Composable
private fun CoursePreview(
    size: WidgetPreviewSize,
    snapshot: CourseWidgetSnapshot,
    settings: MySettings,
    modifier: Modifier = Modifier
) {
    val palette = rememberWidgetPreviewPalette(settings)
    WidgetPreviewShell(palette = palette, modifier = modifier.aspectRatio(size.aspectRatio)) {
        if (size == WidgetPreviewSize.FourByFour) {
            CourseGridPreview(snapshot, palette)
        } else {
            CourseSummaryPreview(size, snapshot, palette)
        }
    }
}

@Composable
private fun CourseSummaryPreview(size: WidgetPreviewSize, snapshot: CourseWidgetSnapshot, palette: WidgetPreviewPalette) {
    val items = snapshot.todayItems.ifEmpty { snapshot.upcomingItems }.take(if (size == WidgetPreviewSize.FourByTwo) 3 else 2)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (size == WidgetPreviewSize.TwoByOne) 12.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("第 ${snapshot.weekNumber} 周", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.primaryText, maxLines = 1)
        if (items.isEmpty()) {
            Text("今日暂无课程", style = MaterialTheme.typography.bodyLarge, color = palette.secondaryText, maxLines = 1)
        } else {
            items.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(palette.card)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.width(3.dp).height(28.dp).clip(RoundedCornerShape(2.dp)).background(item.safeComposeColor(palette.primary)))
                    Spacer(modifier = Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.title.ifBlank { "课程" }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = palette.primaryText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${item.startTime} · ${item.nodeText}", style = MaterialTheme.typography.labelSmall, color = palette.secondaryText, maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseGridPreview(snapshot: CourseWidgetSnapshot, palette: WidgetPreviewPalette) {
    val section = snapshot.sections.firstOrNull() ?: return
    val nodes = section.range.toList().take(4).ifEmpty { listOf(1, 2, 3, 4) }
    val hasItems = snapshot.items.any { it.startNode <= section.range.last && it.endNode >= section.range.first }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("第 ${snapshot.weekNumber} 周 · ${section.segment.displayName}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = palette.primaryText, maxLines = 1)
        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(modifier = Modifier.width(22.dp))
            (1..7).forEach { day ->
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(weekdayText(snapshot.weekStart.plusDays((day - 1).toLong())).removePrefix("周"), style = MaterialTheme.typography.labelSmall, color = palette.secondaryText)
                    Text(snapshot.weekStart.plusDays((day - 1).toLong()).dayOfMonth.toString(), style = MaterialTheme.typography.labelSmall, color = if (day == snapshot.today.dayOfWeek.value) palette.primary else palette.secondaryText)
                }
            }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                nodes.forEach { node ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(node.toString(), modifier = Modifier.width(22.dp), style = MaterialTheme.typography.labelMedium, color = palette.secondaryText)
                        Box(modifier = Modifier.weight(1f).height(1.dp).background(palette.secondaryText.copy(alpha = 0.18f)))
                    }
                }
            }
            if (!hasItems) {
                Text("本时段暂无课程", modifier = Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium, color = palette.secondaryText)
            }
        }
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

private fun com.antgskds.calendarassistant.platform.widget.CourseWidgetItem.safeComposeColor(fallback: Color): Color {
    return if (color == 0) fallback else Color(color)
}

private fun sampleWeatherData(): WeatherData {
    val today = LocalDate.now()
    val now = OffsetDateTime.now()
    return WeatherData(
        temperature = "20",
        feelsLike = "20",
        text = "雾",
        locationName = "上虞区",
        adm2 = "绍兴市",
        hourlyForecast = List(6) { index ->
            WeatherHourlyForecast(
                fxTime = now.plusHours(index.toLong()).toString(),
                temp = if (index < 2) "20" else "19",
                text = if (index < 2) "雾" else "多云"
            )
        },
        dailyForecast = List(5) { index ->
            WeatherDailyForecast(
                fxDate = today.plusDays(index.toLong()).toString(),
                tempMin = listOf("16", "19", "21", "21", "20").getOrElse(index) { "20" },
                tempMax = listOf("24", "29", "31", "31", "25").getOrElse(index) { "26" },
                textDay = listOf("雾", "多云", "多云", "小雨", "小雨").getOrElse(index) { "多云" }
            )
        }
    )
}

private fun hourLabel(value: String): String {
    val parsed = runCatching { OffsetDateTime.parse(value) }.getOrNull() ?: return "--:00"
    return parsed.toLocalTime().format(TimeFormatter)
}

private fun dailyLabel(index: Int, value: String): String {
    val date = runCatching { LocalDate.parse(value) }.getOrNull()
    val prefix = when (index) {
        0 -> "今天"
        1 -> "明天"
        else -> date?.let { weekdayText(it) } ?: "第${index + 1}天"
    }
    return date?.let { "${it.monthValue}月${it.dayOfMonth}日 $prefix" } ?: prefix
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
