package com.antgskds.calendarassistant.widget

import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.core.weather.WeatherForecastIconMapper
import com.antgskds.calendarassistant.core.weather.WeatherIconMapper
import com.antgskds.calendarassistant.core.weather.WeatherWarningText
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.WeatherAlertData
import com.antgskds.calendarassistant.data.model.WeatherDailyForecast
import com.antgskds.calendarassistant.data.model.WeatherData
import com.antgskds.calendarassistant.data.model.WeatherHourlyForecast
import com.antgskds.calendarassistant.data.model.WeatherRiskAlert
import com.antgskds.calendarassistant.data.model.displayLocationName
import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime

class WeatherWidgetRenderer(private val context: android.content.Context) {
    private val support = WidgetRenderingSupport(context)

    fun render(
        appWidgetId: Int,
        options: Bundle,
        snapshot: WeatherWidgetSnapshot,
        settings: MySettings,
        config: WidgetInstanceConfig
    ): RemoteViews {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return RemoteViews(
                mapOf(
                    SizeF(110f, 60f) to renderSize(appWidgetId, WidgetSize.CELL_2X1, snapshot.weather, settings, config),
                    SizeF(110f, 110f) to renderSize(appWidgetId, WidgetSize.CELL_2X2, snapshot.weather, settings, config),
                    SizeF(230f, 110f) to renderSize(appWidgetId, WidgetSize.CELL_4X2, snapshot.weather, settings, config),
                    SizeF(230f, 230f) to renderSize(appWidgetId, WidgetSize.CELL_4X4, snapshot.weather, settings, config)
                )
            )
        }
        return renderSize(appWidgetId, support.resolveSize(options), snapshot.weather, settings, config)
    }

    private fun renderSize(
        appWidgetId: Int,
        size: WidgetSize,
        data: WeatherData?,
        settings: MySettings,
        config: WidgetInstanceConfig
    ): RemoteViews {
        val layout = when (size) {
            WidgetSize.CELL_2X1 -> R.layout.widget_weather_2x1
            WidgetSize.CELL_2X2 -> R.layout.widget_weather_2x2
            WidgetSize.CELL_4X2 -> R.layout.widget_weather_4x2
            WidgetSize.CELL_4X4 -> R.layout.widget_weather_4x4
        }
        val colors = support.resolveColors(settings, config.appearance)
        val text = support.resolveTextSizes(size, settings)
        val views = RemoteViews(context.packageName, layout)
        bindWeatherBackground(views, colors)
        views.setOnClickPendingIntent(R.id.widget_root, support.openAppIntent(appWidgetId, WidgetActions.ACTION_OPEN_WEATHER))
        bindWeatherIcon(views, R.id.widget_weather_icon, data?.let { WeatherIconMapper.iconRes(it) } ?: R.drawable.ic_weather_sunny, colors)

        if (data == null) {
            bindEmpty(views, size, colors, text)
            return views
        }

        when (size) {
            WidgetSize.CELL_2X1 -> bind2x1(views, data, colors, text)
            WidgetSize.CELL_2X2 -> bind2x2(views, data, colors, text)
            WidgetSize.CELL_4X2 -> bind4x2(views, data, colors, text)
            WidgetSize.CELL_4X4 -> bind4x4(views, data, colors, text)
        }
        return views
    }

    private fun bindEmpty(views: RemoteViews, size: WidgetSize, colors: WidgetColors, text: WidgetTextSizes) {
        views.bindText(R.id.widget_weather_title, "--°", colors.primaryText)
        views.bindText(R.id.widget_weather_subtitle, "暂无天气数据", colors.secondaryText)
        when (size) {
            WidgetSize.CELL_2X1 -> Unit
            WidgetSize.CELL_2X2 -> {
                views.bindText(R.id.widget_weather_location, "未知位置", colors.primaryText)
                bindWeatherStatusChip(views, "配置天气后显示预报", colors, highlighted = false)
            }
            WidgetSize.CELL_4X2 -> {
                views.bindText(R.id.widget_weather_location, "未知位置", colors.primaryText)
                bindHourlyForecast(views, emptyList(), colors)
            }
            WidgetSize.CELL_4X4 -> {
                views.bindText(R.id.widget_weather_location, "未知位置", colors.primaryText)
                bindHourlyForecast(views, emptyList(), colors)
                bind5DailyForecast(views, emptyList(), colors)
                bindDailyDivider(views, colors)
            }
        }
    }

    private fun bind2x1(views: RemoteViews, data: WeatherData, colors: WidgetColors, text: WidgetTextSizes) {
        views.bindText(R.id.widget_weather_title, "${data.temperature.ifBlank { "--" }}° ${data.text.ifBlank { "天气" }}", colors.primaryText)
        views.bindText(R.id.widget_weather_subtitle, tempRangeText(data), colors.secondaryText)
    }

    private fun bind2x2(views: RemoteViews, data: WeatherData, colors: WidgetColors, text: WidgetTextSizes) {
        bindCurrentHeader(views, data, colors)
        val status = weatherStatusText(data)
        if (status != null) {
            bindWeatherStatusChip(views, status, colors, highlighted = true)
        } else {
            hideWeatherStatusChip(views)
        }
    }

    private fun bind4x2(views: RemoteViews, data: WeatherData, colors: WidgetColors, text: WidgetTextSizes) {
        bindCurrentHeader(views, data, colors)
        bindHourlyForecast(views, data.hourlyForecast.take(6), colors)
    }

    private fun bind4x4(views: RemoteViews, data: WeatherData, colors: WidgetColors, text: WidgetTextSizes) {
        bindCurrentHeader(views, data, colors)
        bindHourlyForecast(views, data.hourlyForecast.take(6), colors)
        bind5DailyForecast(views, data.dailyForecast.take(5), colors)
        bindDailyDivider(views, colors)
    }

    private fun bindWeatherBackground(views: RemoteViews, colors: WidgetColors) {
        views.setImageViewBitmap(R.id.widget_background, support.solidBitmap(colors.background))
    }

    private fun bindCurrentHeader(views: RemoteViews, data: WeatherData, colors: WidgetColors) {
        views.bindText(R.id.widget_weather_location, data.displayLocationName(short = true), colors.primaryText)
        views.bindText(R.id.widget_weather_title, "${data.temperature.ifBlank { "--" }}°", colors.primaryText)
        views.bindText(R.id.widget_weather_subtitle, "${data.text.ifBlank { "天气" }} ${tempRangeText(data)}", colors.secondaryText)
    }

    private fun bindHourlyForecast(views: RemoteViews, hours: List<WeatherHourlyForecast>, colors: WidgetColors) {
        val slots = listOf(
            Triple(R.id.h1_time, R.id.h1_icon, R.id.h1_temp),
            Triple(R.id.h2_time, R.id.h2_icon, R.id.h2_temp),
            Triple(R.id.h3_time, R.id.h3_icon, R.id.h3_temp),
            Triple(R.id.h4_time, R.id.h4_icon, R.id.h4_temp),
            Triple(R.id.h5_time, R.id.h5_icon, R.id.h5_temp),
            Triple(R.id.h6_time, R.id.h6_icon, R.id.h6_temp)
        )
        slots.forEachIndexed { index, slot ->
            val hour = hours.getOrNull(index)
            if (hour != null) {
                views.bindText(slot.first, if (index == 0) "现在" else timeLabelOPPO(hour.fxTime), colors.secondaryText)
                views.bindText(slot.third, "${hour.temp.ifBlank { "--" }}°", colors.primaryText)
                bindWeatherIcon(views, slot.second, WeatherForecastIconMapper.iconRes(hour.text, hour.icon), colors)
            } else {
                views.bindText(slot.first, "--", colors.secondaryText)
                views.bindText(slot.third, "--°", colors.primaryText)
                views.setViewVisibility(slot.second, View.INVISIBLE)
            }
        }
    }

    private fun bindWeatherStatusChip(views: RemoteViews, value: String, colors: WidgetColors, highlighted: Boolean) {
        val backgroundColor = if (highlighted) support.withAlpha(colors.primary, 52) else colors.card
        views.setViewVisibility(R.id.widget_weather_alert_container, View.VISIBLE)
        views.setImageViewBitmap(R.id.widget_weather_alert_bg, support.roundedBitmap(128, 30, 15, backgroundColor))
        views.bindText(R.id.widget_weather_alert, value, colors.primaryText)
    }

    private fun hideWeatherStatusChip(views: RemoteViews) {
        views.setViewVisibility(R.id.widget_weather_alert_container, View.GONE)
    }

    private fun bindDailyDivider(views: RemoteViews, colors: WidgetColors) {
        views.setInt(R.id.weather_daily_divider, "setBackgroundColor", support.withAlpha(colors.secondaryText, 36))
    }

    private fun bind5DailyForecast(views: RemoteViews, days: List<WeatherDailyForecast>, colors: WidgetColors) {
        val slots = listOf(
            Triple(R.id.d1_date, R.id.d1_icon, R.id.d1_temp),
            Triple(R.id.d2_date, R.id.d2_icon, R.id.d2_temp),
            Triple(R.id.d3_date, R.id.d3_icon, R.id.d3_temp),
            Triple(R.id.d4_date, R.id.d4_icon, R.id.d4_temp),
            Triple(R.id.d5_date, R.id.d5_icon, R.id.d5_temp)
        )
        slots.forEachIndexed { index, slot ->
            val day = days.getOrNull(index)
            if (day != null) {
                views.bindText(slot.first, dateLabelOPPO(index, day.fxDate), colors.primaryText)
                views.bindText(slot.third, dailyRangeText(day), colors.primaryText)
                bindWeatherIcon(views, slot.second, WeatherForecastIconMapper.iconRes(day.textDay, day.iconDay), colors)
            } else {
                views.bindText(slot.first, "--", colors.primaryText)
                views.bindText(slot.third, "--° / --°", colors.primaryText)
                views.setViewVisibility(slot.second, View.INVISIBLE)
            }
        }
    }

    private fun bindWeatherIcon(views: RemoteViews, id: Int, iconRes: Int, colors: WidgetColors) {
        views.setViewVisibility(id, View.VISIBLE)
        views.setImageViewResource(id, iconRes)
        views.setInt(id, "setColorFilter", colors.primary)
    }

    private fun tempRangeText(data: WeatherData): String {
        return data.dailyForecast.firstOrNull()?.let { dailyRangeText(it) } ?: "--° / --°"
    }

    private fun weatherStatusText(data: WeatherData): String? {
        data.alerts.firstOrNull()?.let { alert ->
            return officialAlertText(alert)
        }
        data.riskAlerts.firstOrNull()?.let { risk ->
            return riskAlertText(risk)
        }
        return null
    }

    private fun officialAlertText(alert: WeatherAlertData): String {
        val title = WeatherWarningText.officialTitle(alert).ifBlank { alert.headline }.ifBlank { "天气预警" }
        if (title.length <= 8) return title
        val event = alert.eventName.ifBlank { alert.headline.extractWeatherEvent() }.ifBlank { "天气" }
        return "$event 预警".replace(" ", "")
    }

    private fun riskAlertText(risk: WeatherRiskAlert): String {
        val event = riskPhenomenonText(risk)
        return "${compactLeadTime(risk.fxTime)}$event"
    }

    private fun riskPhenomenonText(risk: WeatherRiskAlert): String {
        val value = listOf(risk.title, risk.weatherText, risk.message).joinToString(" ")
        return when {
            value.contains("雷") || value.contains("强对流") -> "雷雨"
            value.contains("雨") || value.contains("降水") -> "下雨"
            value.contains("雪") || value.contains("结冰") -> "降雪"
            value.contains("风") || value.contains("台风") -> "大风"
            value.contains("高温") -> "高温"
            value.contains("雾") || value.contains("霾") -> "雾天"
            else -> risk.title.ifBlank { "天气" }
        }
    }

    private fun compactLeadTime(value: String): String {
        val parsed = runCatching { OffsetDateTime.parse(value) }.getOrNull() ?: return "即将"
        val minutes = Duration.between(OffsetDateTime.now(parsed.offset), parsed).toMinutes()
        return when {
            minutes <= 30 -> "即将"
            minutes < 90 -> "1小时后"
            else -> "${(minutes / 60).coerceAtLeast(1)}小时后"
        }
    }

    private fun String.extractWeatherEvent(): String {
        return listOf("雷暴大风", "暴雨", "大风", "台风", "暴雪", "高温", "寒潮", "冰雹", "道路结冰", "雷雨", "降雨", "雾", "霾")
            .firstOrNull { contains(it) }
            .orEmpty()
    }

    private fun dailyRangeText(forecast: WeatherDailyForecast): String {
        return "${forecast.tempMin.ifBlank { "--" }}° / ${forecast.tempMax.ifBlank { "--" }}°"
    }

    private fun timeLabelOPPO(value: String): String {
        val parsed = runCatching { OffsetDateTime.parse(value).toLocalTime() }.getOrNull()
        return parsed?.let { "%02d:00".format(it.hour) } ?: value.substringAfter('T', value).take(2).ifBlank { "--" } + ":00"
    }

    private fun dateLabelOPPO(index: Int, date: String): String {
        val parsed = runCatching { LocalDate.parse(date) }.getOrNull() ?: return "第${index + 1}天"
        val suffix = when (index) {
            0 -> "今天"
            1 -> "明天"
            else -> weekdayShort(parsed.dayOfWeek.value)
        }
        return "${parsed.monthValue}月${parsed.dayOfMonth}日 $suffix"
    }

}
