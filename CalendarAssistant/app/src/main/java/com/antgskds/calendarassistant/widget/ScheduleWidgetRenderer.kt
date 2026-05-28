package com.antgskds.calendarassistant.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import androidx.compose.ui.graphics.toArgb
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.core.util.DensityConfigManager
import com.antgskds.calendarassistant.core.util.LunarCalendarUtils
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.data.model.WidgetScheduleEntry
import com.antgskds.calendarassistant.data.model.WidgetScheduleSnapshot
import com.antgskds.calendarassistant.data.model.WidgetThemeMode
import com.antgskds.calendarassistant.ui.theme.ThemeColorGenerator
import com.antgskds.calendarassistant.ui.theme.ThemeColorScheme
import com.antgskds.calendarassistant.ui.theme.parseThemeHexColor
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

class ScheduleWidgetRenderer(private val context: Context) {
    private val density = context.resources.displayMetrics.density
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val shortDateFormatter = DateTimeFormatter.ofPattern("M月d日")

    fun render(
        appWidgetId: Int,
        options: Bundle,
        snapshot: WidgetScheduleSnapshot,
        settings: MySettings
    ): RemoteViews {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return RemoteViews(
                mapOf(
                    SizeF(110f, 60f) to renderSize(appWidgetId, WidgetSize.CELL_2X1, snapshot, settings),
                    SizeF(110f, 110f) to renderSize(appWidgetId, WidgetSize.CELL_2X2, snapshot, settings),
                    SizeF(230f, 110f) to renderSize(appWidgetId, WidgetSize.CELL_4X2, snapshot, settings),
                    SizeF(230f, 230f) to renderSize(appWidgetId, WidgetSize.CELL_4X4, snapshot, settings)
                )
            )
        }
        return renderSize(appWidgetId, resolveSize(options), snapshot, settings)
    }

    private fun renderSize(
        appWidgetId: Int,
        size: WidgetSize,
        snapshot: WidgetScheduleSnapshot,
        settings: MySettings
    ): RemoteViews {
        val layoutId = when (size) {
            WidgetSize.CELL_2X1 -> R.layout.widget_schedule_2x1
            WidgetSize.CELL_2X2 -> R.layout.widget_schedule_2x2
            WidgetSize.CELL_4X2 -> R.layout.widget_schedule_4x2
            WidgetSize.CELL_4X4 -> R.layout.widget_schedule_4x4
        }
        val colors = resolveColors(settings)
        val text = resolveTextSizes(size, settings)
        val views = RemoteViews(context.packageName, layoutId)
        bindCommon(views, appWidgetId, colors)

        when (size) {
            WidgetSize.CELL_2X1 -> bind2x1(views, snapshot, colors, text)
            WidgetSize.CELL_2X2 -> bind2x2(views, snapshot, colors, text)
            WidgetSize.CELL_4X2 -> bind4x2(views, snapshot, colors, text)
            WidgetSize.CELL_4X4 -> bind4x4(views, snapshot, colors, text)
        }
        return views
    }

    private fun bindCommon(
        views: RemoteViews,
        appWidgetId: Int,
        colors: WidgetColors
    ) {
        views.setImageViewBitmap(
            R.id.widget_background,
            solidBitmap(colors.background)
        )
        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent(appWidgetId))
    }

    private fun bind2x1(
        views: RemoteViews,
        snapshot: WidgetScheduleSnapshot,
        colors: WidgetColors,
        text: WidgetTextSizes
    ) {
        val todayEntry = snapshot.visibleEntries.firstOrNull { it.date == snapshot.today }
        bindEventSlot(
            views = views,
            containerId = R.id.widget_event_1,
            bgId = null,
            stripId = R.id.widget_event_1_strip,
            titleId = R.id.widget_event_1_title,
            timeId = R.id.widget_event_1_time,
            item = todayEntry?.item,
            colors = colors,
            text = text,
            emptyTitle = "无日程",
            emptyTime = "今日空闲",
            cardWidthDp = 0,
            cardHeightDp = 0,
            includeDateInTime = false
        )
    }

    private fun bind2x2(
        views: RemoteViews,
        snapshot: WidgetScheduleSnapshot,
        colors: WidgetColors,
        text: WidgetTextSizes
    ) {
        bindDateHeader(views, snapshot.today, colors, text)
        val entries = snapshot.visibleEntries.filter { it.date == snapshot.today }.take(2)
        bindEventSlot(
            views = views,
            containerId = R.id.widget_event_1,
            bgId = R.id.widget_event_1_bg,
            stripId = R.id.widget_event_1_strip,
            titleId = R.id.widget_event_1_title,
            timeId = R.id.widget_event_1_time,
            item = entries.getOrNull(0)?.item,
            colors = colors,
            text = text,
            emptyTitle = "无日程",
            emptyTime = "今日空闲",
            emptyAsPlainText = entries.isEmpty(),
            cardWidthDp = 180,
            cardHeightDp = 52,
            includeDateInTime = false
        )
        bindEventSlot(
            views = views,
            containerId = R.id.widget_event_2,
            bgId = R.id.widget_event_2_bg,
            stripId = R.id.widget_event_2_strip,
            titleId = R.id.widget_event_2_title,
            timeId = R.id.widget_event_2_time,
            item = entries.getOrNull(1)?.item,
            colors = colors,
            text = text,
            emptyTitle = "",
            hideIfEmpty = true,
            cardWidthDp = 180,
            cardHeightDp = 52,
            includeDateInTime = true
        )
    }

    private fun bind4x2(
        views: RemoteViews,
        snapshot: WidgetScheduleSnapshot,
        colors: WidgetColors,
        text: WidgetTextSizes
    ) {
        bindDateHeader(views, snapshot.today, colors, text)
        views.setTextColor(R.id.widget_lunar, colors.secondaryText)
        views.setTextViewTextSize(R.id.widget_lunar, TypedValue.COMPLEX_UNIT_PX, text.lunarPx)
        views.setTextViewText(R.id.widget_lunar, LunarCalendarUtils.getLunarDate(snapshot.today).ifBlank { "今日" })

        val todayEntries = snapshot.visibleEntries.filter { it.date == snapshot.today }
        val todayItem = todayEntries.firstOrNull()?.item
        bindEventSlot(
            views = views,
            containerId = R.id.widget_event_1,
            bgId = R.id.widget_event_1_bg,
            stripId = R.id.widget_event_1_strip,
            titleId = R.id.widget_event_1_title,
            timeId = R.id.widget_event_1_time,
            item = todayItem,
            colors = colors,
            text = text,
            emptyTitle = "暂无日程安排",
            emptyTime = null,
            emptyAsPlainText = todayItem == null,
            cardWidthDp = 190,
            cardHeightDp = 58,
            includeDateInTime = true
        )

        val sideItems = build4x2SideItems(snapshot)
        bindSideItemSlot(views, R.id.widget_group_1_label, R.id.widget_event_2, R.id.widget_event_2_bg, R.id.widget_event_2_strip, R.id.widget_event_2_title, R.id.widget_event_2_time, sideItems.getOrNull(0), snapshot.today, colors, text)
        bindSideItemSlot(views, R.id.widget_group_2_label, R.id.widget_event_3, R.id.widget_event_3_bg, R.id.widget_event_3_strip, R.id.widget_event_3_title, R.id.widget_event_3_time, sideItems.getOrNull(1), snapshot.today, colors, text)
    }

    private fun bind4x4(
        views: RemoteViews,
        snapshot: WidgetScheduleSnapshot,
        colors: WidgetColors,
        text: WidgetTextSizes
    ) {
        bindDateHeader(views, snapshot.today, colors, text)
        val items = snapshot.visibleEntries.take(4)
        val slots = listOf(
            Slot(R.id.widget_event_1, R.id.widget_event_1_bg, R.id.widget_event_1_strip, R.id.widget_event_1_title, R.id.widget_event_1_time, R.id.widget_event_1_end_time, R.id.widget_group_1_label),
            Slot(R.id.widget_event_2, R.id.widget_event_2_bg, R.id.widget_event_2_strip, R.id.widget_event_2_title, R.id.widget_event_2_time, R.id.widget_event_2_end_time, R.id.widget_group_2_label),
            Slot(R.id.widget_event_3, R.id.widget_event_3_bg, R.id.widget_event_3_strip, R.id.widget_event_3_title, R.id.widget_event_3_time, R.id.widget_event_3_end_time, R.id.widget_group_3_label),
            Slot(R.id.widget_event_4, R.id.widget_event_4_bg, R.id.widget_event_4_strip, R.id.widget_event_4_title, R.id.widget_event_4_time, R.id.widget_event_4_end_time, R.id.widget_group_4_label)
        )
        var previousDate: LocalDate? = null
        slots.forEachIndexed { index, slot ->
            val itemWithDate = items.getOrNull(index)
            val showLabel = itemWithDate != null && itemWithDate.date != previousDate
            bindGroupLabel(views, slot.labelId, itemWithDate?.date, colors, text, showLabel)
            bindEventSlot(
                views = views,
                containerId = slot.containerId,
                bgId = slot.bgId,
                stripId = slot.stripId,
                titleId = slot.titleId,
                timeId = slot.timeId,
                endTimeId = slot.endTimeId,
                item = itemWithDate?.item,
                colors = colors,
                text = text,
                emptyTitle = if (index == 0) "无日程" else "",
                emptyTime = if (index == 0) "今日空闲" else null,
                hideIfEmpty = index > 0,
                cardWidthDp = 290,
                cardHeightDp = 58
            )
            if (itemWithDate != null) previousDate = itemWithDate.date
        }
    }

    private fun bindDateHeader(
        views: RemoteViews,
        date: LocalDate,
        colors: WidgetColors,
        text: WidgetTextSizes
    ) {
        views.setTextColor(R.id.widget_day, colors.primaryText)
        views.setTextColor(R.id.widget_weekday, colors.primary)
        views.setTextViewTextSize(R.id.widget_day, TypedValue.COMPLEX_UNIT_PX, text.dayPx)
        views.setTextViewTextSize(R.id.widget_weekday, TypedValue.COMPLEX_UNIT_PX, text.weekdayPx)
        views.setTextViewText(R.id.widget_day, date.dayOfMonth.toString())
        views.setTextViewText(R.id.widget_weekday, weekdayText(date))
    }

    private fun bindSideItemSlot(
        views: RemoteViews,
        labelId: Int,
        containerId: Int,
        bgId: Int,
        stripId: Int,
        titleId: Int,
        timeId: Int,
        item: WidgetScheduleEntry?,
        today: LocalDate,
        colors: WidgetColors,
        text: WidgetTextSizes
    ) {
        views.setTextColor(labelId, colors.secondaryText)
        views.setTextViewTextSize(labelId, TypedValue.COMPLEX_UNIT_PX, text.groupLabelPx)
        if (item == null) {
            views.setTextViewText(labelId, "")
            views.setViewVisibility(labelId, View.GONE)
            views.setViewVisibility(containerId, View.GONE)
            return
        }
        val showDateLabel = item.date != today
        views.setViewVisibility(labelId, if (showDateLabel) View.VISIBLE else View.GONE)
        views.setTextViewText(labelId, if (showDateLabel) groupLabel(item.date, useRelativePrefix = false) else "")
        bindEventSlot(
            views = views,
            containerId = containerId,
            bgId = bgId,
            stripId = stripId,
            titleId = titleId,
            timeId = timeId,
            item = item.item,
            colors = colors,
            text = text,
            emptyTitle = "暂无安排",
            cardWidthDp = 210,
            cardHeightDp = 58,
            includeDateInTime = false
        )
    }

    private fun bindGroupLabel(
        views: RemoteViews,
        labelId: Int?,
        date: LocalDate?,
        colors: WidgetColors,
        text: WidgetTextSizes,
        visible: Boolean
    ) {
        if (labelId == null) return
        views.setTextColor(labelId, colors.secondaryText)
        views.setTextViewTextSize(labelId, TypedValue.COMPLEX_UNIT_PX, text.groupLabelPx)
        if (visible && date != null) {
            views.setViewVisibility(labelId, View.VISIBLE)
            views.setTextViewText(labelId, groupLabel(date, useRelativePrefix = false))
        } else {
            views.setViewVisibility(labelId, View.GONE)
            views.setTextViewText(labelId, "")
        }
    }

    private fun bindEventSlot(
        views: RemoteViews,
        containerId: Int,
        bgId: Int?,
        stripId: Int,
        titleId: Int,
        timeId: Int,
        endTimeId: Int? = null,
        item: ScheduleDisplayItem?,
        colors: WidgetColors,
        text: WidgetTextSizes,
        emptyTitle: String,
        emptyTime: String? = "点击进入应用添加",
        emptyAsPlainText: Boolean = false,
        hideIfEmpty: Boolean = false,
        cardWidthDp: Int = 220,
        cardHeightDp: Int = 50,
        includeDateInTime: Boolean = false
    ) {
        if (item == null && hideIfEmpty) {
            views.setViewVisibility(containerId, View.GONE)
            return
        }
        views.setViewVisibility(containerId, View.VISIBLE)
        val hasItem = item != null
        if (bgId != null && cardWidthDp > 0 && cardHeightDp > 0) {
            val cardColor = if (!hasItem && emptyAsPlainText) Color.TRANSPARENT else colors.card
            views.setImageViewBitmap(bgId, roundedBitmap(cardWidthDp, cardHeightDp, 12, cardColor))
        }
        views.setViewVisibility(stripId, if (!hasItem && emptyAsPlainText) View.GONE else View.VISIBLE)
        views.setInt(stripId, "setBackgroundColor", item?.safeColor(colors.primary) ?: colors.secondaryText)
        views.setTextColor(titleId, if (!hasItem && emptyAsPlainText) colors.secondaryText else colors.primaryText)
        views.setTextColor(timeId, colors.secondaryText)
        views.setTextViewTextSize(titleId, TypedValue.COMPLEX_UNIT_PX, text.titlePx)
        views.setTextViewTextSize(timeId, TypedValue.COMPLEX_UNIT_PX, text.timePx)
        views.setTextViewText(titleId, item?.title?.takeIf { it.isNotBlank() } ?: emptyTitle)
        if (endTimeId == null) {
            views.setTextViewText(timeId, item?.let { formatTimeText(it, includeDateInTime) } ?: emptyTime.orEmpty())
            views.setViewVisibility(timeId, if (item != null || !emptyTime.isNullOrBlank()) View.VISIBLE else View.GONE)
        } else {
            val range = item?.let { formatStackedTimeText(it) }
            views.setTextViewText(timeId, range?.first ?: "点击添加")
            views.setTextColor(endTimeId, colors.secondaryText)
            views.setTextViewTextSize(endTimeId, TypedValue.COMPLEX_UNIT_PX, text.endTimePx)
            if (range?.second.isNullOrBlank()) {
                views.setViewVisibility(endTimeId, View.GONE)
                views.setTextViewText(endTimeId, "")
            } else {
                views.setViewVisibility(endTimeId, View.VISIBLE)
                views.setTextViewText(endTimeId, range?.second.orEmpty())
            }
        }
    }

    private fun resolveSize(options: Bundle): WidgetSize {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        return when {
            minWidth >= 220 && minHeight >= 180 -> WidgetSize.CELL_4X4
            minWidth >= 220 -> WidgetSize.CELL_4X2
            minHeight >= 95 -> WidgetSize.CELL_2X2
            else -> WidgetSize.CELL_2X1
        }
    }

    private fun resolveTextSizes(size: WidgetSize, settings: MySettings): WidgetTextSizes {
        val uiScale = DensityConfigManager.getScaleFactor(settings.uiSize) / DensityConfigManager.getScaleFactor(2)
        val fontScale = Resources.getSystem().configuration.fontScale.coerceIn(0.9f, 1.12f)
        val scale = (uiScale * fontScale).coerceIn(0.84f, 0.98f)

        fun sp(value: Float): Float {
            return value * context.resources.displayMetrics.scaledDensity * scale
        }

        return when (size) {
            WidgetSize.CELL_2X1 -> WidgetTextSizes(
                dayPx = sp(0f),
                weekdayPx = sp(0f),
                lunarPx = sp(0f),
                groupLabelPx = sp(0f),
                titlePx = sp(15.5f),
                timePx = sp(13.5f),
                endTimePx = sp(13f)
            )
            WidgetSize.CELL_2X2 -> WidgetTextSizes(
                dayPx = sp(34f),
                weekdayPx = sp(14f),
                lunarPx = sp(0f),
                groupLabelPx = sp(0f),
                titlePx = sp(12.5f),
                timePx = sp(10.5f),
                endTimePx = sp(12.5f)
            )
            WidgetSize.CELL_4X2 -> WidgetTextSizes(
                dayPx = sp(44f),
                weekdayPx = sp(17f),
                lunarPx = sp(11f),
                groupLabelPx = sp(12f),
                titlePx = sp(14f),
                timePx = sp(12f),
                endTimePx = sp(12f)
            )
            WidgetSize.CELL_4X4 -> WidgetTextSizes(
                dayPx = sp(48f),
                weekdayPx = sp(18f),
                lunarPx = sp(0f),
                groupLabelPx = sp(13f),
                titlePx = sp(15f),
                timePx = sp(14.5f),
                endTimePx = sp(13.5f)
            )
        }
    }

    private fun resolveColors(settings: MySettings): WidgetColors {
        val dark = resolveDarkTheme(settings)
        val alpha = (settings.widgetBackgroundAlpha.coerceIn(0.6f, 1f) * 255f).roundToInt()
        val schemeName = settings.themeColorScheme
        val colorScheme = if (ThemeColorScheme.fromName(schemeName) == ThemeColorScheme.CUSTOM) {
            ThemeColorGenerator.generateCustomColorScheme(parseThemeHexColor(settings.customThemeColorHex), dark)
        } else {
            ThemeColorGenerator.generateColorScheme(ThemeColorScheme.fromName(schemeName).primaryColor, dark, schemeName)
        }

        val background = if (schemeName == ThemeColorScheme.DEFAULT.name && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            dynamicSystemColor(if (dark) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_10, colorScheme.background.toArgb())
        } else {
            colorScheme.background.toArgb()
        }
        return WidgetColors(
            background = withAlpha(background, alpha),
            card = withAlpha(colorScheme.surfaceContainerLow.toArgb(), if (dark) 210 else 225),
            primaryText = colorScheme.onBackground.toArgb(),
            secondaryText = colorScheme.onSurfaceVariant.toArgb(),
            primary = colorScheme.primary.toArgb()
        )
    }

    private fun resolveDarkTheme(settings: MySettings): Boolean {
        return when (settings.widgetThemeMode) {
            WidgetThemeMode.LIGHT -> false
            WidgetThemeMode.DARK -> true
            else -> when (settings.themeMode) {
                2 -> false
                3 -> true
                else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun dynamicSystemColor(resId: Int, fallback: Int): Int {
        return runCatching { context.getColor(resId) }.getOrDefault(fallback)
    }

    private fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun roundedBitmap(widthDp: Int, heightDp: Int, radiusDp: Int, color: Int): Bitmap {
        val widthPx = (widthDp.coerceAtLeast(1) * density).roundToInt().coerceAtLeast(1)
        val heightPx = (heightDp.coerceAtLeast(1) * density).roundToInt().coerceAtLeast(1)
        val radiusPx = radiusDp * density
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawRoundRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), radiusPx, radiusPx, paint)
        return bitmap
    }

    private fun solidBitmap(color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return bitmap
    }

    private fun openAppIntent(appWidgetId: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            41000 + appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ScheduleDisplayItem.safeColor(fallback: Int): Int {
        if (color == 0) return fallback
        return if (Color.alpha(color) == 0) Color.rgb(Color.red(color), Color.green(color), Color.blue(color)) else color
    }

    private fun formatTimeText(item: ScheduleDisplayItem, includeDate: Boolean): String {
        if (item.isAllDay) return "全天"
        val start = runCatching { LocalDateTime.of(item.startDate, item.startLocalTime) }.getOrNull() ?: return item.startTime
        val end = runCatching { LocalDateTime.of(item.endDate, item.endLocalTime) }.getOrNull()
        val startText = start.toLocalTime().format(timeFormatter)
        val endText = end?.takeIf { it.toLocalDate() == start.toLocalDate() }?.toLocalTime()?.format(timeFormatter)
        val range = if (endText.isNullOrBlank()) startText else "$startText - $endText"
        if (!includeDate) return range
        val prefix = when (val date = start.toLocalDate()) {
            LocalDate.now() -> "今天"
            LocalDate.now().plusDays(1) -> "明天"
            else -> date.format(shortDateFormatter)
        }
        return "$prefix $range"
    }

    private fun formatStackedTimeText(item: ScheduleDisplayItem): Pair<String, String> {
        if (item.isAllDay) return "全天" to ""
        val start = runCatching { LocalDateTime.of(item.startDate, item.startLocalTime) }.getOrNull()
            ?: return item.startTime to ""
        val end = runCatching { LocalDateTime.of(item.endDate, item.endLocalTime) }.getOrNull()
        val startText = start.toLocalTime().format(timeFormatter)
        val endText = end?.takeIf { it.toLocalDate() == start.toLocalDate() }?.toLocalTime()?.format(timeFormatter).orEmpty()
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

    private fun groupLabel(date: LocalDate, useRelativePrefix: Boolean = true): String {
        val today = LocalDate.now()
        val prefix = if (useRelativePrefix) {
            when (date) {
                today.plusDays(1) -> "明天"
                today.plusDays(2) -> "后天"
                else -> date.format(shortDateFormatter)
            }
        } else {
            date.format(shortDateFormatter)
        }
        return "$prefix ${weekdayText(date)}"
    }

    private fun build4x2SideItems(snapshot: WidgetScheduleSnapshot): List<WidgetScheduleEntry> {
        return snapshot.visibleEntries
            .drop(if (snapshot.visibleEntries.firstOrNull()?.date == snapshot.today) 1 else 0)
            .take(2)
    }

    private data class WidgetColors(
        val background: Int,
        val card: Int,
        val primaryText: Int,
        val secondaryText: Int,
        val primary: Int
    )

    private data class WidgetTextSizes(
        val dayPx: Float,
        val weekdayPx: Float,
        val lunarPx: Float,
        val groupLabelPx: Float,
        val titlePx: Float,
        val timePx: Float,
        val endTimePx: Float
    )

    private data class Slot(
        val containerId: Int,
        val bgId: Int,
        val stripId: Int,
        val titleId: Int,
        val timeId: Int,
        val endTimeId: Int? = null,
        val labelId: Int? = null
    )

    private enum class WidgetSize(val defaultWidthDp: Int, val defaultHeightDp: Int) {
        CELL_2X1(150, 70),
        CELL_2X2(150, 150),
        CELL_4X2(320, 150),
        CELL_4X4(320, 320)
    }
}
