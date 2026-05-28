package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.antgskds.calendarassistant.core.weather.WeatherForecastIconMapper
import com.antgskds.calendarassistant.core.weather.WeatherIconMapper
import com.antgskds.calendarassistant.core.weather.WeatherWarningText
import com.antgskds.calendarassistant.data.model.WeatherAlertData
import com.antgskds.calendarassistant.data.model.WeatherDailyForecast
import com.antgskds.calendarassistant.data.model.WeatherData
import com.antgskds.calendarassistant.data.model.WeatherHourlyForecast
import com.antgskds.calendarassistant.data.model.WeatherRiskAlert
import com.antgskds.calendarassistant.data.model.displayLocationName
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun WeatherDetailPage(uiSize: Int = 2) {
    val app = LocalContext.current.applicationContext as App
    val weatherData by app.weatherQueryApi.weatherData.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        WeatherDetailCurrentCard(weatherData)

        if (weatherData?.alerts?.isNotEmpty() == true || weatherData?.riskAlerts?.isNotEmpty() == true) {
            SectionTitle("预警与风险")
            WeatherWarningsCard(
                alerts = weatherData?.alerts.orEmpty(),
                risks = weatherData?.riskAlerts.orEmpty()
            )
        }

        SectionTitle("未来24小时")
        HourlyTemperatureChart(weatherData?.hourlyForecast.orEmpty())

        SectionTitle("未来一周")
        DailyForecastList(weatherData?.dailyForecast.orEmpty())

        if (weatherData == null) {
            Text(
                text = "暂无天气缓存，请先在天气设置页保存并刷新。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeatherDetailScreen(
    uiSize: Int = 2,
    onBack: () -> Unit
) {
    val haptics = rememberAppHaptics()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("天气详情") },
                navigationIcon = {
                    IconButton(onClick = { haptics.click(); onBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            WeatherDetailPage(uiSize = uiSize)
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        if (data == null) {
            Text(
                text = "暂无天气数据",
                modifier = Modifier.padding(18.dp),
                style = MaterialTheme.typography.titleMedium
            )
            return@Card
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
private fun WeatherWarningsCard(alerts: List<WeatherAlertData>, risks: List<WeatherRiskAlert>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        if (hours.isEmpty()) {
            Text(
                text = "暂无逐小时预报",
                modifier = Modifier.padding(18.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Card
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
private fun DailyForecastList(days: List<WeatherDailyForecast>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        if (days.isEmpty()) {
            Text(
                text = "暂无一周预报",
                modifier = Modifier.padding(18.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Card
        }

        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            days.take(7).forEachIndexed { index, day ->
                DailyForecastRow(day = day)
                if (index != days.take(7).lastIndex) CompactDivider()
            }
        }
    }
}

@Composable
private fun DailyForecastRow(day: WeatherDailyForecast) {
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
            Text(
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
