package com.antgskds.calendarassistant.feature.weather.ui.connector

import com.antgskds.calendarassistant.shared.ui.material.component.AppPageScaffold
import com.antgskds.calendarassistant.shared.ui.material.component.AppTopBar
import com.antgskds.calendarassistant.shared.ui.material.component.AppTopBarBackButton

import com.antgskds.calendarassistant.shared.ui.material.component.LocalAppPageBottomPadding
import com.antgskds.calendarassistant.shared.ui.edition.EditionIconButton

import com.antgskds.calendarassistant.app.ui.theme.material.background.AppBackgroundStyleTheme
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.feature.weather.domain.WeatherForecastIconMapper
import com.antgskds.calendarassistant.feature.weather.domain.WeatherIconMapper
import com.antgskds.calendarassistant.feature.weather.domain.WeatherWarningText
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherAlertData
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherDailyForecast
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherData
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherHourlyForecast
import com.antgskds.calendarassistant.feature.weather.domain.model.WeatherRiskAlert
import com.antgskds.calendarassistant.feature.weather.domain.model.displayLocationName
import com.antgskds.calendarassistant.feature.settings.developer.application.DemoModeDataFactory
import com.antgskds.calendarassistant.shared.ui.material.component.AppCard
import com.antgskds.calendarassistant.feature.weather.ui.contract.WeatherDetailUiAction
import com.antgskds.calendarassistant.feature.weather.ui.contract.WeatherDetailUiState
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import com.antgskds.calendarassistant.shared.ui.adaptive.AdaptiveTwoPaneLayout
import com.antgskds.calendarassistant.shared.ui.adaptive.AdaptiveTwoPaneTopBar
import com.antgskds.calendarassistant.shared.ui.adaptive.LocalAdaptiveLayoutInfo
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WeatherDetailPage(uiSize: Int = 2) {
    val app = LocalContext.current.applicationContext as App
    val weatherData by app.weatherQueryApi.weatherData.collectAsState()
    val settings by app.settingsQueryApi.settings.collectAsState()
    val displayedWeather = if (settings.developerOptionsEnabled && settings.developerDemoModeEnabled) {
        DemoModeDataFactory.weather(LocalDate.now())
    } else weatherData
    MaterialWeatherDetailPage(
        state = WeatherDetailUiState(
            weatherData = displayedWeather,
            hasAppBackground = false,
            miuiBlurEnabled = false,
            cardAlphaPercent = 100
        ),
        uiSize = uiSize
    )
}

@Composable
fun MaterialWeatherDetailPage(state: WeatherDetailUiState, uiSize: Int = 2) {
    val weatherData = state.weatherData
    val bottomInset = LocalAppPageBottomPadding.current
    val useTwoPane = LocalAdaptiveLayoutInfo.current.useTwoPaneContent

    if (useTwoPane) {
        AdaptiveTwoPaneLayout(
            primaryWidth = ConfigCatalog.ADAPTIVE_COMPACT_PANE_WIDTH_DP.dp,
            primary = {
                WeatherScrollablePane(bottomInset) {
                    WeatherCurrentContent(weatherData, expanded = true)
                }
            },
            secondary = {
                WeatherScrollablePane(bottomInset, horizontalPadding = ConfigCatalog.ADAPTIVE_CONTENT_PADDING_DP.dp) {
                    WeatherForecastContent(weatherData, expanded = true)
                }
            },
        )
    } else Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 960.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            WeatherCurrentContent(weatherData, expanded = false)
            WeatherForecastContent(weatherData, expanded = false)
            Spacer(modifier = Modifier.height(bottomInset + 24.dp))
        }
    }
}

@Composable
private fun WeatherScrollablePane(
    bottomInset: Dp,
    horizontalPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
        Spacer(modifier = Modifier.height(bottomInset + 24.dp))
    }
}

@Composable
private fun ColumnScope.WeatherCurrentContent(weatherData: WeatherData?, expanded: Boolean) {
    WeatherDetailCurrentCard(weatherData)
    if (expanded && weatherData != null) {
        SectionTitle("今日概况")
        WeatherTodayOverview(weatherData)
    }
    if (weatherData?.alerts?.isNotEmpty() == true || weatherData?.riskAlerts?.isNotEmpty() == true) {
        SectionTitle("预警与风险")
        WeatherWarningsCard(
            alerts = weatherData?.alerts.orEmpty(),
            risks = weatherData?.riskAlerts.orEmpty(),
        )
    }
}

@Composable
private fun ColumnScope.WeatherForecastContent(weatherData: WeatherData?, expanded: Boolean) {
    if (expanded) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("未来24小时")
            WeatherHourlySummary(weatherData?.hourlyForecast.orEmpty())
            HourlyTemperatureChart(weatherData?.hourlyForecast.orEmpty())
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionTitle("未来一周")
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                DailyForecastList(
                    weatherData?.dailyForecast.orEmpty(),
                    showDetails = maxWidth >= ConfigCatalog.ADAPTIVE_WEATHER_TABLE_MIN_WIDTH_DP.dp,
                )
            }
        }
    } else {
        SectionTitle("未来24小时")
        HourlyTemperatureChart(weatherData?.hourlyForecast.orEmpty())
        SectionTitle("未来一周")
        DailyForecastList(weatherData?.dailyForecast.orEmpty())
    }
    if (weatherData == null) {
        Text(
            text = "暂无天气缓存，请先在天气设置页保存并刷新。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherDetailScreen(
    uiSize: Int = 2,
    showBack: Boolean = true,
    showSettings: Boolean = false,
    onOpenSettings: () -> Unit = {},
    onBack: () -> Unit
) {
    val app = LocalContext.current.applicationContext as App
    val weatherData by app.weatherQueryApi.weatherData.collectAsState()
    val settings by app.settingsQueryApi.settings.collectAsState()
    val displayedWeather = if (settings.developerOptionsEnabled && settings.developerDemoModeEnabled) {
        DemoModeDataFactory.weather(LocalDate.now())
    } else weatherData
    MaterialWeatherDetailScreen(
        state = WeatherDetailUiState(
            weatherData = displayedWeather,
            hasAppBackground = settings.appBackgroundImagePath.isNotBlank(),
            miuiBlurEnabled = settings.appBackgroundMiuiBlurTestEnabled,
            cardAlphaPercent = settings.appBackgroundCardAlphaPercent
        ),
        uiSize = uiSize,
        showBack = showBack,
        showSettings = showSettings,
        onAction = { action ->
            when (action) {
                WeatherDetailUiAction.NavigateBack -> onBack()
                WeatherDetailUiAction.OpenSettings -> onOpenSettings()
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialWeatherDetailScreen(
    state: WeatherDetailUiState,
    uiSize: Int = 2,
    showBack: Boolean = true,
    showSettings: Boolean = false,
    onAction: (WeatherDetailUiAction) -> Unit
) {
    val pageContainerColor = if (state.hasAppBackground) Color.Transparent else MaterialTheme.colorScheme.background
    val haptics = rememberAppHaptics()
    val useTwoPane = LocalAdaptiveLayoutInfo.current.useTwoPaneContent
    val settingsAction: @Composable RowScope.() -> Unit = {
        if (showSettings) {
            IconButton(onClick = {
                haptics.click()
                onAction(WeatherDetailUiAction.OpenSettings)
            }) {
                Icon(Icons.Default.Settings, contentDescription = "天气设置")
            }
        }
    }
    AppBackgroundStyleTheme(
        enabled = state.hasAppBackground,
        miuiBlurEnabled = state.miuiBlurEnabled,
        cardAlphaPercent = state.cardAlphaPercent
    ) {
    AppPageScaffold(
        edgeToEdgeContent = true,
        contentMaxWidth = if (useTwoPane) Dp.Unspecified else 960.dp,
        containerColor = pageContainerColor,
        topBar = {
            if (useTwoPane) {
                AdaptiveTwoPaneTopBar(
                    primaryTitle = "天气",
                    secondaryTitle = "天气预报",
                    primaryWidth = ConfigCatalog.ADAPTIVE_COMPACT_PANE_WIDTH_DP.dp,
                    secondaryActions = settingsAction,
                )
            } else {
                AppTopBar(
                    title = "天气详情",
                    containerColor = pageContainerColor,
                    navigationIcon = {
                        if (showBack) {
                            AppTopBarBackButton(onClick = { haptics.click(); onAction(WeatherDetailUiAction.NavigateBack) })
                        }
                    },
                    actions = settingsAction,
                )
            }
        },
    ) {
        MaterialWeatherDetailPage(state = state, uiSize = uiSize)
    }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold),
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun WeatherDetailCurrentCard(data: WeatherData?) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        if (data == null) {
            Text(
                text = "暂无天气数据",
                modifier = Modifier.padding(18.dp),
                style = MaterialTheme.typography.titleMedium
            )
            return@AppCard
        }

        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(WeatherIconMapper.iconRes(data)),
                    contentDescription = data.text.ifBlank { "天气" },
                    modifier = Modifier.size(44.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${data.temperature.ifBlank { "--" }}°C · ${data.text.ifBlank { "天气" }}",
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = data.displayLocationName(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CurrentMetric("体感", "${data.feelsLike.ifBlank { "--" }}°", Modifier.weight(1f))
                CurrentMetric("湿度", "${data.humidity.ifBlank { "--" }}%", Modifier.weight(1f))
                CurrentMetric("风力", "${data.windDir.ifBlank { "--" }}${data.windScale.ifBlank { "--" }}级", Modifier.weight(1f))
                CurrentMetric("能见度", "${data.vis.ifBlank { "--" }}km", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CurrentMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WeatherTodayOverview(data: WeatherData) {
    val today = data.dailyForecast.firstOrNull()
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WeatherOverviewMetric("降水", data.precip.takeIf { it.isNotBlank() }?.let { "${it}mm" } ?: "--", Modifier.weight(1f))
                WeatherOverviewMetric("气压", data.pressure.takeIf { it.isNotBlank() }?.let { "${it}hPa" } ?: "--", Modifier.weight(1f))
                WeatherOverviewMetric("风速", data.windSpeed.takeIf { it.isNotBlank() }?.let { "${it}km/h" } ?: "--", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WeatherOverviewMetric("紫外线", today?.uvIndex.orEmpty().ifBlank { "--" }, Modifier.weight(1f))
                WeatherOverviewMetric("日出", today?.sunrise.orEmpty().ifBlank { "--" }, Modifier.weight(1f))
                WeatherOverviewMetric("日落", today?.sunset.orEmpty().ifBlank { "--" }, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WeatherHourlySummary(hours: List<WeatherHourlyForecast>) {
    if (hours.isEmpty()) return
    val values = hours.take(24)
    val temperatures = values.mapNotNull { it.temp.toIntOrNull() }
    val precipitation = values.mapNotNull { it.pop.toIntOrNull() }.maxOrNull()
    val humidity = values.mapNotNull { it.humidity.toIntOrNull() }.let { list ->
        if (list.isEmpty()) null else list.average().toInt()
    }
    val windSpeed = values.mapNotNull { it.windSpeed.toIntOrNull() }.maxOrNull()
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
    ) {
        Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WeatherOverviewMetric(
                "温度范围",
                if (temperatures.isEmpty()) "--" else "${temperatures.min()}° / ${temperatures.max()}°",
                Modifier.weight(1f).fillMaxHeight(),
            )
            WeatherOverviewMetric("最高降水", precipitation?.let { "$it%" } ?: "--", Modifier.weight(1f).fillMaxHeight())
            WeatherOverviewMetric("平均湿度", humidity?.let { "$it%" } ?: "--", Modifier.weight(1f).fillMaxHeight())
            WeatherOverviewMetric("最大风速", windSpeed?.let { "${it}km/h" } ?: "--", Modifier.weight(1f).fillMaxHeight())
        }
    }
}

@Composable
private fun WeatherOverviewMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f))
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeatherWarningsCard(alerts: List<WeatherAlertData>, risks: List<WeatherRiskAlert>) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            alerts.take(3).forEachIndexed { index, alert ->
                ExpandableWarningRow(
                    title = WeatherWarningText.officialTitle(alert),
                    meta = listOfNotBlank(formatDateTime(alert.effectiveTime.ifBlank { alert.issuedTime }), alert.senderName).joinToString(" · "),
                    summary = compactSummary(alert.description.ifBlank { alert.headline.ifBlank { alert.instruction } }),
                    tag = "官方",
                    color = MaterialTheme.colorScheme.error
                ) {
                    WarningDetailLine("详细说明", alert.description)
                    WarningDetailLine("防御指南", alert.instruction)
                    WarningDetailLine("生效时间", formatDateTime(alert.effectiveTime.ifBlank { alert.onsetTime }))
                    WarningDetailLine("过期时间", formatDateTime(alert.expireTime))
                }
                if (index != alerts.take(3).lastIndex || risks.isNotEmpty()) CompactDivider()
            }
            risks.take(3).forEachIndexed { index, risk ->
                ExpandableWarningRow(
                    title = risk.title.removePrefix("天气风险提醒："),
                    meta = listOfNotBlank(formatDateTime(risk.fxTime), risk.level.toRiskLevelText()).joinToString(" · "),
                    summary = compactSummary(risk.message),
                    tag = "风险推断",
                    color = MaterialTheme.colorScheme.tertiary
                ) {
                    WarningDetailLine("提醒内容", risk.message)
                    WarningDetailLine("预报时间", formatDateTime(risk.fxTime))
                    WarningDetailLine("风险等级", risk.level.toRiskLevelText())
                    WarningDetailLine("天气现象", risk.weatherText)
                }
                if (index != risks.take(3).lastIndex) CompactDivider()
            }
        }
    }
}

@Composable
private fun ExpandableWarningRow(
    title: String,
    meta: String,
    summary: String,
    tag: String,
    color: Color,
    detailContent: @Composable () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = rememberAppHaptics()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(14.dp))
            .clickable { haptics.click(); expanded = !expanded }
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title.ifBlank { "天气提醒" },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                WarningTag(level = tag, color = color)
            }
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (summary.isNotBlank()) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                detailContent()
            }
        }
    }
}

@Composable
private fun WarningDetailLine(label: String, value: String) {
    if (value.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun WarningTag(level: String, color: Color) {
    val text = level.ifBlank { "提醒" }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = color,
            maxLines = 1
        )
    }
}

@Composable
private fun CompactDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
}

@Composable
private fun HourlyTemperatureChart(hours: List<WeatherHourlyForecast>) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        if (hours.isEmpty()) {
            Text(
                text = "暂无逐小时预报",
                modifier = Modifier.padding(18.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@AppCard
        }

        val points = hours.take(24)
        val chartWidth = (points.size * 54).coerceAtLeast(360).dp
        val chartHeight = 190.dp
        val temps = points.mapNotNull { it.temp.toFloatOrNull() }
        val minTemp = temps.minOrNull() ?: 0f
        val maxTemp = temps.maxOrNull() ?: minTemp
        val lineColor = MaterialTheme.colorScheme.primary
        val guideColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        val pointColor = MaterialTheme.colorScheme.surface
        val markIndices = rememberMarkIndices(points, minTemp, maxTemp)

        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Text(
                text = "温度趋势",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 18.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(chartWidth)
                        .height(chartHeight)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val topPadding = 48.dp.toPx()
                        val bottomPadding = 42.dp.toPx()
                        val leftPadding = 16.dp.toPx()
                        val rightPadding = 16.dp.toPx()
                        val usableWidth = size.width - leftPadding - rightPadding
                        val usableHeight = size.height - topPadding - bottomPadding
                        val tempRange = (maxTemp - minTemp).takeIf { it > 0f } ?: 1f
                        val offsets = points.mapIndexed { index, hour ->
                            val temp = hour.temp.toFloatOrNull() ?: minTemp
                            val x = leftPadding + usableWidth * index / (points.lastIndex.coerceAtLeast(1).toFloat())
                            val y = topPadding + usableHeight * (1f - (temp - minTemp) / tempRange)
                            Offset(x, y)
                        }

                        for (lineIndex in 0..2) {
                            val y = topPadding + usableHeight * lineIndex / 2f
                            drawLine(
                                color = guideColor,
                                start = Offset(leftPadding, y),
                                end = Offset(size.width - rightPadding, y),
                                strokeWidth = 1.dp.toPx()
                            )
                        }

                        val path = Path()
                        offsets.forEachIndexed { index, offset ->
                            if (index == 0) path.moveTo(offset.x, offset.y) else path.lineTo(offset.x, offset.y)
                        }
                        drawPath(path = path, color = lineColor, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
                        offsets.forEachIndexed { index, offset ->
                            if (index in markIndices) {
                                drawCircle(color = lineColor, radius = 4.dp.toPx(), center = offset)
                                drawCircle(color = pointColor, radius = 2.dp.toPx(), center = offset)
                            }
                        }
                    }

                    points.forEachIndexed { index, hour ->
                        HourlyChartMarker(
                            hour = hour,
                            index = index,
                            total = points.size,
                            minTemp = minTemp,
                            maxTemp = maxTemp,
                            chartWidth = chartWidth,
                            chartHeight = chartHeight,
                            showWeather = index in markIndices
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HourlyChartMarker(
    hour: WeatherHourlyForecast,
    index: Int,
    total: Int,
    minTemp: Float,
    maxTemp: Float,
    chartWidth: Dp,
    chartHeight: Dp,
    showWeather: Boolean
) {
    val temp = hour.temp.toFloatOrNull() ?: minTemp
    val tempRange = (maxTemp - minTemp).takeIf { it > 0f } ?: 1f
    val topPadding = 48.dp
    val bottomPadding = 42.dp
    val leftPadding = 16.dp
    val rightPadding = 16.dp
    val usableWidth = chartWidth - leftPadding - rightPadding
    val usableHeight = chartHeight - topPadding - bottomPadding
    val x = leftPadding + usableWidth * (index / (total - 1).coerceAtLeast(1).toFloat())
    val y = topPadding + usableHeight * (1f - (temp - minTemp) / tempRange)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
    ) {
        if (showWeather) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(
                        x = (x - 23.dp).coerceIn(0.dp, chartWidth - 46.dp),
                        y = (y - 42.dp).coerceAtLeast(4.dp)
                    )
                    .width(46.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "${hour.temp.ifBlank { "--" }}°",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    painter = painterResource(WeatherForecastIconMapper.iconRes(hour.text, hour.icon)),
                    contentDescription = hour.text,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        if (index % 3 == 0 || index == total - 1) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = (x - 27.dp).coerceIn(0.dp, chartWidth - 54.dp))
                    .width(54.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = formatHourShort(hour.fxTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (hour.pop.isNotBlank()) {
                    Text(
                        text = "降水${hour.pop}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyForecastList(days: List<WeatherDailyForecast>, showDetails: Boolean = false) {
    AppCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        if (days.isEmpty()) {
            Text(
                text = "暂无一周预报",
                modifier = Modifier.padding(18.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@AppCard
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            days.take(7).forEachIndexed { index, day ->
                DailyForecastRow(day = day, showDetails = showDetails)
                if (index != days.take(7).lastIndex) CompactDivider()
            }
        }
    }
}

@Composable
private fun DailyForecastRow(day: WeatherDailyForecast, showDetails: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    val haptics = rememberAppHaptics()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clip(RoundedCornerShape(12.dp))
            .clickable { haptics.click(); expanded = !expanded }
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.width(62.dp)) {
                Text(formatDay(day.fxDate), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                Text(formatMonthDay(day.fxDate), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                painter = painterResource(WeatherForecastIconMapper.iconRes(day.textDay, day.iconDay)),
                contentDescription = day.textDay,
                modifier = Modifier.size(28.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = compactDayWeather(day),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(10.dp))
            if (showDetails) {
                Column(Modifier.width(88.dp), horizontalAlignment = Alignment.End) {
                    Text(day.precip.takeIf { it.isNotBlank() }?.let { "${it}mm" } ?: "--",
                        style = MaterialTheme.typography.bodyMedium)
                    Text("降水", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.width(116.dp), horizontalAlignment = Alignment.End) {
                    Text("${day.windDirDay.ifBlank { "--" }} ${day.windScaleDay.ifBlank { "--" }}级",
                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("风力", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(16.dp))
            }
            Text(
                modifier = if (showDetails) Modifier.width(100.dp) else Modifier,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                text = "${day.tempMin.ifBlank { "--" }}°/${day.tempMax.ifBlank { "--" }}°",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DailyDetailMetric("降水", day.precip.takeIf { it.isNotBlank() }?.let { "${it}mm" } ?: "--")
                DailyDetailMetric("风力", "${day.windDirDay.ifBlank { "--" }} ${day.windScaleDay.ifBlank { "--" }}级")
                DailyDetailMetric("紫外线", day.uvIndex.ifBlank { "--" })
                DailyDetailMetric("日落", day.sunset.ifBlank { "--" })
            }
        }
    }
}

@Composable
private fun DailyDetailMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun rememberMarkIndices(points: List<WeatherHourlyForecast>, minTemp: Float, maxTemp: Float): Set<Int> {
    if (points.isEmpty()) return emptySet()
    val indices = mutableSetOf(0, points.lastIndex)
    points.indexOfFirst { it.temp.toFloatOrNull() == maxTemp }.takeIf { it >= 0 }?.let(indices::add)
    points.indexOfFirst { it.temp.toFloatOrNull() == minTemp }.takeIf { it >= 0 }?.let(indices::add)
    points.forEachIndexed { index, hour ->
        if (index % 3 == 0) indices.add(index)
        if (index > 0 && hour.text != points[index - 1].text) indices.add(index)
    }
    return indices
}

private fun compactAlertTitle(alert: WeatherAlertData): String {
    return WeatherWarningText.officialTitle(alert)
}

private fun compactSummary(value: String): String {
    return value
        .replace("\n", " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(72)
}

private fun compactDayWeather(day: WeatherDailyForecast): String {
    val dayText = day.textDay.ifBlank { "--" }
    val nightText = day.textNight.ifBlank { "--" }
    return if (dayText == nightText) dayText else "${dayText}转$nightText"
}

private fun String.toRiskLevelText(): String {
    return when (this) {
        "high" -> "高风险"
        "medium" -> "中风险"
        "low" -> "低风险"
        else -> this
    }
}

private fun listOfNotBlank(vararg values: String): List<String> {
    return values.filter { it.isNotBlank() }
}

private fun formatHourShort(value: String): String {
    return runCatching { OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("H时")) }.getOrDefault("--")
}

private fun formatDateTime(value: String): String {
    return runCatching { OffsetDateTime.parse(value).format(DateTimeFormatter.ofPattern("M-d HH:mm")) }.getOrDefault("")
}

private fun formatDay(value: String): String {
    return runCatching {
        val date = LocalDate.parse(value)
        date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.CHINESE)
    }.getOrDefault("--")
}

private fun formatMonthDay(value: String): String {
    return runCatching { LocalDate.parse(value).format(DateTimeFormatter.ofPattern("MM-dd")) }.getOrDefault(value)
}
