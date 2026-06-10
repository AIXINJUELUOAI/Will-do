package com.antgskds.calendarassistant.widget

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.data.model.MySettings
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.roundToInt

class CourseWidgetRenderer(private val context: android.content.Context) {
    private val support = WidgetRenderingSupport(context)
    private val dateFormatter = DateTimeFormatter.ofPattern("M月d日")

    fun render(
        appWidgetId: Int,
        options: Bundle,
        snapshot: CourseWidgetSnapshot,
        settings: MySettings,
        config: WidgetInstanceConfig
    ): RemoteViews {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return RemoteViews(
                mapOf(
                    SizeF(110f, 60f) to renderSize(appWidgetId, WidgetSize.CELL_2X1, snapshot, settings, config),
                    SizeF(110f, 110f) to renderSize(appWidgetId, WidgetSize.CELL_2X2, snapshot, settings, config),
                    SizeF(230f, 110f) to renderSize(appWidgetId, WidgetSize.CELL_4X2, snapshot, settings, config),
                    SizeF(230f, 230f) to renderSize(appWidgetId, WidgetSize.CELL_4X4, snapshot, settings, config)
                )
            )
        }
        return renderSize(appWidgetId, support.resolveSize(options), snapshot, settings, config)
    }

    private fun renderSize(
        appWidgetId: Int,
        size: WidgetSize,
        snapshot: CourseWidgetSnapshot,
        settings: MySettings,
        config: WidgetInstanceConfig
    ): RemoteViews {
        val colors = support.resolveColors(settings, config.appearance)
        val text = support.resolveTextSizes(size, settings)
        return if (size == WidgetSize.CELL_4X4) {
            renderGrid(appWidgetId, snapshot, colors, text, config)
        } else {
            renderSummary(appWidgetId, size, snapshot, colors, text)
        }
    }

    private fun renderSummary(
        appWidgetId: Int,
        size: WidgetSize,
        snapshot: CourseWidgetSnapshot,
        colors: WidgetColors,
        text: WidgetTextSizes
    ): RemoteViews {
        val layout = when (size) {
            WidgetSize.CELL_2X1 -> R.layout.widget_schedule_2x1
            WidgetSize.CELL_2X2 -> R.layout.widget_schedule_2x2
            WidgetSize.CELL_4X2 -> R.layout.widget_schedule_4x2
            WidgetSize.CELL_4X4 -> R.layout.widget_schedule_4x4
        }
        val views = RemoteViews(context.packageName, layout)
        views.bindWidgetBackground(support, colors)
        views.setOnClickPendingIntent(R.id.widget_root, support.openAppIntent(appWidgetId, WidgetActions.ACTION_OPEN_COURSE))

        val next = nextCourse(snapshot)
        if (snapshot.items.isEmpty()) {
            bindEmptySummary(views, size, snapshot, colors, text)
            return views
        }

        when (size) {
            WidgetSize.CELL_2X1 -> {
                bindCourseSlot(
                    views = views,
                    containerId = R.id.widget_event_1,
                    bgId = null,
                    stripId = R.id.widget_event_1_strip,
                    titleId = R.id.widget_event_1_title,
                    timeId = R.id.widget_event_1_time,
                    item = next,
                    colors = colors,
                    text = text,
                    emptyTitle = "今日无课",
                    emptyTime = "第 ${snapshot.weekNumber} 周"
                )
            }
            WidgetSize.CELL_2X2 -> {
                val today = snapshot.todayItems.take(2)
                bindDateHeader(views, snapshot, colors, text, showWeekNumber = false)
                bindCourseSlot(views, R.id.widget_event_1, R.id.widget_event_1_bg, R.id.widget_event_1_strip, R.id.widget_event_1_title, R.id.widget_event_1_time, today.getOrNull(0), colors, text, "今天没有课程", "点击进入应用管理课表", cardWidthDp = 132, cardHeightDp = 46)
                bindCourseSlot(views, R.id.widget_event_2, R.id.widget_event_2_bg, R.id.widget_event_2_strip, R.id.widget_event_2_title, R.id.widget_event_2_time, today.getOrNull(1), colors, text, "", null, hideIfEmpty = true, cardWidthDp = 132, cardHeightDp = 46)
            }
            WidgetSize.CELL_4X2 -> {
                val todayItem = snapshot.todayItems.firstOrNull()
                bindDateHeader(views, snapshot, colors, text, showWeekNumber = true)
                bindCourseSlot(views, R.id.widget_event_1, R.id.widget_event_1_bg, R.id.widget_event_1_strip, R.id.widget_event_1_title, R.id.widget_event_1_time, todayItem, colors, text, "今天没有课程", "点击进入应用管理课表", cardWidthDp = 140, cardHeightDp = 58)
                val compactItems = build4x2CompactItems(snapshot, todayItem)
                bindCompactCourseSideItemSlot(
                    views = views,
                    labelId = R.id.widget_group_1_label,
                    containerId = R.id.widget_event_2,
                    bgId = R.id.widget_event_2_bg,
                    stripId = R.id.widget_event_2_strip,
                    titleId = R.id.widget_event_2_title,
                    timeId = R.id.widget_event_2_time,
                    item = compactItems.firstOrNull(),
                    today = snapshot.today,
                    colors = colors,
                    text = text
                )
                bindGroupLabel(views, R.id.widget_group_2_label, null, colors, text)
                bindCourseSummaryChip(
                    views = views,
                    containerId = R.id.widget_event_3,
                    bgId = R.id.widget_event_3_bg,
                    stripId = R.id.widget_event_3_strip,
                    titleId = R.id.widget_event_3_title,
                    timeId = R.id.widget_event_3_time,
                    count = compactItems.drop(1).size,
                    colors = colors,
                    text = text
                )
            }
            WidgetSize.CELL_4X4 -> Unit
        }
        return views
    }

    private fun renderGrid(
        appWidgetId: Int,
        snapshot: CourseWidgetSnapshot,
        colors: WidgetColors,
        text: WidgetTextSizes,
        config: WidgetInstanceConfig
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_course_grid)
        views.bindWidgetBackground(support, colors)
        views.setOnClickPendingIntent(R.id.widget_root, support.openAppIntent(appWidgetId, WidgetActions.ACTION_OPEN_COURSE))
        val section = selectedSection(snapshot, config.courseSegment)
        val sectionIndex = snapshot.sections.indexOfFirst { it.segment == section.segment }.coerceAtLeast(0)
        val canMoveUp = sectionIndex > 0
        val canMoveDown = sectionIndex in 0 until snapshot.sections.lastIndex
        views.bindText(R.id.widget_title, "第 ${snapshot.weekNumber} 周 · ${section.segment.displayName}", colors.primaryText, text.titlePx)
        bindSegmentButton(
            views = views,
            containerId = R.id.widget_course_up,
            backgroundId = R.id.widget_course_up_bg,
            labelId = R.id.widget_course_up_text,
            enabled = canMoveUp,
            action = WidgetActions.ACTION_COURSE_SEGMENT_UP,
            appWidgetId = appWidgetId,
            colors = colors
        )
        bindSegmentButton(
            views = views,
            containerId = R.id.widget_course_down,
            backgroundId = R.id.widget_course_down_bg,
            labelId = R.id.widget_course_down_text,
            enabled = canMoveDown,
            action = WidgetActions.ACTION_COURSE_SEGMENT_DOWN,
            appWidgetId = appWidgetId,
            colors = colors
        )
        val sectionItems = visibleSectionItems(snapshot, section)
        val isEmptySection = sectionItems.isEmpty()
        views.setImageViewBitmap(R.id.widget_course_board, drawCourseBoard(snapshot, section, sectionItems, colors))
        views.bindText(
            id = R.id.widget_course_empty_text,
            value = if (isEmptySection) "本时段暂无课程" else "",
            color = colors.secondaryText,
            textSizePx = text.titlePx,
            visible = isEmptySection
        )
        return views
    }

    private fun bindSegmentButton(
        views: RemoteViews,
        containerId: Int,
        backgroundId: Int,
        labelId: Int,
        enabled: Boolean,
        action: String,
        appWidgetId: Int,
        colors: WidgetColors
    ) {
        views.setImageViewBitmap(backgroundId, support.roundedBitmap(48, 30, 15, support.withAlpha(colors.card, if (enabled) 235 else 145)))
        views.setTextColor(labelId, if (enabled) colors.primaryText else support.withAlpha(colors.secondaryText, 90))
        val intent = courseSegmentIntent(appWidgetId, action)
        views.setOnClickPendingIntent(containerId, intent)
        views.setOnClickPendingIntent(backgroundId, intent)
        views.setOnClickPendingIntent(labelId, intent)
    }

    private fun courseSegmentIntent(appWidgetId: Int, action: String): PendingIntent {
        val intent = Intent(context, CourseWidgetProvider::class.java).apply {
            this.action = action
            data = Uri.parse("willdo://course-widget/$appWidgetId/$action")
            putExtra(WidgetActions.EXTRA_APP_WIDGET_ID, appWidgetId)
        }
        return PendingIntent.getBroadcast(
            context,
            61000 + appWidgetId + (action.hashCode() and 0x0FFF),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun selectedSection(snapshot: CourseWidgetSnapshot, segment: CourseWidgetSegment): CourseWidgetSection {
        val sections = snapshot.sections.ifEmpty {
            val maxNode = snapshot.nodes.maxOfOrNull { it.index } ?: 4
            listOf(CourseWidgetSection(CourseWidgetSegment.MORNING, 1..maxNode.coerceAtLeast(1)))
        }
        return sections.firstOrNull { it.segment == segment } ?: sections.first()
    }

    private fun visibleSectionItems(snapshot: CourseWidgetSnapshot, section: CourseWidgetSection): List<CourseWidgetItem> {
        return snapshot.items.filter { item ->
            item.dayOfWeek in 1..7 && item.startNode <= section.range.last && item.endNode >= section.range.first
        }
    }

    private fun drawCourseBoard(
        snapshot: CourseWidgetSnapshot,
        section: CourseWidgetSection,
        sectionItems: List<CourseWidgetItem>,
        colors: WidgetColors
    ): Bitmap {
        val density = support.density
        fun dp(value: Float): Float = value * density
        fun sp(value: Float): Float = value * context.resources.displayMetrics.scaledDensity

        val width = dp(296f).roundToInt().coerceAtLeast(1)
        val height = dp(252f).roundToInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val axisWidth = dp(25f)
        val headerHeight = dp(34f)
        val colWidth = (width - axisWidth) / 7f
        val nodeIndices = section.range.toList().ifEmpty { listOf(1) }
        val rowHeight = ((height - headerHeight) / nodeIndices.size).coerceAtLeast(dp(34f))
        val bottom = headerHeight + rowHeight * nodeIndices.size

        val headerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.secondaryText
            textAlign = Paint.Align.CENTER
            textSize = sp(9.5f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        }
        val todayPaint = Paint(headerPaint).apply {
            color = colors.primary
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colors.secondaryText
            textAlign = Paint.Align.CENTER
            textSize = sp(10f)
        }
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = support.withAlpha(colors.secondaryText, 28)
            strokeWidth = dp(0.7f)
        }
        val todayColumnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = support.withAlpha(colors.primary, 12) }

        val todayDay = snapshot.today.dayOfWeek.value
        if (todayDay in 1..7) {
            val x = axisWidth + (todayDay - 1) * colWidth
            canvas.drawRoundRect(RectF(x + dp(2f), 0f, x + colWidth - dp(2f), bottom), dp(8f), dp(8f), todayColumnPaint)
        }

        for (day in 1..7) {
            val date = snapshot.weekStart.plusDays((day - 1).toLong())
            val paint = if (day == todayDay) todayPaint else headerPaint
            val centerX = axisWidth + (day - 0.5f) * colWidth
            canvas.drawText(weekdayShort(day).removePrefix("周"), centerX, dp(13f), paint)
            canvas.drawText(date.dayOfMonth.toString(), centerX, dp(28f), paint)
        }

        nodeIndices.forEachIndexed { index, nodeIndex ->
            val top = headerHeight + rowHeight * index
            val centerY = top + rowHeight / 2f - (nodePaint.descent() + nodePaint.ascent()) / 2f
            canvas.drawText(nodeIndex.toString(), axisWidth / 2f, centerY, nodePaint)
            canvas.drawLine(axisWidth, top, width.toFloat(), top, gridPaint)
        }

        val nodeIndexMap = nodeIndices.withIndex().associate { it.value to it.index }
        sectionItems.forEach { item ->
            val visibleStart = max(item.startNode, section.range.first)
            val visibleEnd = minOf(item.endNode, section.range.last)
            val startRow = nodeIndexMap[visibleStart] ?: return@forEach
            val endRow = nodeIndexMap[visibleEnd] ?: return@forEach
            val x = axisWidth + (item.dayOfWeek - 1) * colWidth + dp(1f)
            val y = headerHeight + startRow * rowHeight + dp(2f)
            val w = colWidth - dp(2f)
            val h = (endRow - startRow + 1) * rowHeight - dp(4f)
            drawCourseBlock(canvas, item, x, y, w, h, colors, dp = ::dp, sp = ::sp)
        }

        return bitmap
    }

    private fun drawCourseBlock(
        canvas: Canvas,
        item: CourseWidgetItem,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        colors: WidgetColors,
        dp: (Float) -> Float,
        sp: (Float) -> Float
    ) {
        val baseColor = safeWidgetColor(item.color, colors.primary)
        val rect = RectF(x, y, x + width, y + height)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = baseColor }
        canvas.drawRoundRect(rect, dp(8f), dp(8f), fillPaint)

        val compact = width < dp(38f)
        val contentPadding = if (compact) dp(3f) else dp(4f)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = sp(if (compact) 9.2f else 10.5f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(230, 255, 255, 255)
            textSize = sp(if (compact) 7.8f else 8.6f)
        }
        var textTop = y + contentPadding
        val textX = x + contentPadding
        val textWidth = width - contentPadding * 2f
        val titleLines = when {
            height >= dp(92f) -> if (compact) 4 else 3
            height >= dp(56f) -> if (compact) 3 else 2
            else -> 1
        }
        textTop = drawWrappedText(canvas, item.title.ifBlank { "课程" }, textX, textTop, textWidth, titlePaint, titleLines, dp(1f))
        val place = item.location.ifBlank { item.teacher }
        if (place.isNotBlank() && !compact && height >= dp(68f)) {
            val placeTop = (y + height - dp(18f)).coerceAtLeast(textTop + dp(2f))
            drawWrappedText(canvas, "@$place", textX, placeTop, textWidth, detailPaint, 1, dp(0.5f))
        }
    }

    private fun drawWrappedText(
        canvas: Canvas,
        value: String,
        x: Float,
        top: Float,
        maxWidth: Float,
        paint: Paint,
        maxLines: Int,
        extraSpacing: Float
    ): Float {
        if (value.isBlank() || maxLines <= 0) return top
        val lines = mutableListOf<String>()
        var current = ""
        value.forEach { char ->
            val next = current + char
            if (paint.measureText(next) <= maxWidth || current.isEmpty()) {
                current = next
            } else {
                lines += current
                current = char.toString()
            }
        }
        if (current.isNotEmpty()) lines += current
        val visibleLines = lines.take(maxLines).toMutableList()
        if (lines.size > maxLines && visibleLines.isNotEmpty()) {
            var last = visibleLines.last()
            while (last.length > 1 && paint.measureText("$last...") > maxWidth) {
                last = last.dropLast(1)
            }
            visibleLines[visibleLines.lastIndex] = "$last..."
        }
        val lineHeight = paint.descent() - paint.ascent() + extraSpacing
        var baseline = top - paint.ascent()
        visibleLines.forEach { line ->
            canvas.drawText(line, x, baseline, paint)
            baseline += lineHeight
        }
        return top + visibleLines.size * lineHeight
    }

    private fun nextCourse(snapshot: CourseWidgetSnapshot): CourseWidgetItem? {
        val now = LocalTime.now()
        return snapshot.items.firstOrNull { item ->
            item.date.isAfter(snapshot.today) || (item.date == snapshot.today && parseTime(item.endTime)?.isAfter(now) != false)
        }
    }

    private fun bindEmptySummary(
        views: RemoteViews,
        size: WidgetSize,
        snapshot: CourseWidgetSnapshot,
        colors: WidgetColors,
        text: WidgetTextSizes
    ) {
        when (size) {
            WidgetSize.CELL_2X1 -> bindCourseSlot(views, R.id.widget_event_1, null, R.id.widget_event_1_strip, R.id.widget_event_1_title, R.id.widget_event_1_time, null, colors, text, "暂无课程", "导入课表后显示")
            WidgetSize.CELL_2X2 -> {
                bindDateHeader(views, snapshot, colors, text, showWeekNumber = false)
                bindCourseSlot(views, R.id.widget_event_1, R.id.widget_event_1_bg, R.id.widget_event_1_strip, R.id.widget_event_1_title, R.id.widget_event_1_time, null, colors, text, "暂无课程", "点击进入应用管理课表", cardWidthDp = 132, cardHeightDp = 46)
                views.setViewVisibility(R.id.widget_event_2, View.GONE)
            }
            WidgetSize.CELL_4X2 -> {
                bindDateHeader(views, snapshot, colors, text, showWeekNumber = true)
                bindCourseSlot(views, R.id.widget_event_1, R.id.widget_event_1_bg, R.id.widget_event_1_strip, R.id.widget_event_1_title, R.id.widget_event_1_time, null, colors, text, "暂无课程", "点击进入应用管理课表", cardWidthDp = 140, cardHeightDp = 58)
                bindGroupLabel(views, R.id.widget_group_1_label, null, colors, text)
                views.setViewVisibility(R.id.widget_event_2, View.GONE)
                bindGroupLabel(views, R.id.widget_group_2_label, null, colors, text)
                views.setViewVisibility(R.id.widget_event_3, View.GONE)
            }
            WidgetSize.CELL_4X4 -> Unit
        }
    }

    private fun bindDateHeader(
        views: RemoteViews,
        snapshot: CourseWidgetSnapshot,
        colors: WidgetColors,
        text: WidgetTextSizes,
        showWeekNumber: Boolean
    ) {
        views.setTextColor(R.id.widget_day, colors.primaryText)
        views.setTextColor(R.id.widget_weekday, colors.secondaryText)
        views.setTextViewTextSize(R.id.widget_day, TypedValue.COMPLEX_UNIT_PX, text.dayPx)
        views.setTextViewTextSize(R.id.widget_weekday, TypedValue.COMPLEX_UNIT_PX, text.weekdayPx)
        views.setTextViewText(R.id.widget_day, snapshot.today.dayOfMonth.toString())
        views.setTextViewText(R.id.widget_weekday, weekdayShort(snapshot.today.dayOfWeek.value))
        if (showWeekNumber) {
            views.setTextColor(R.id.widget_lunar, colors.secondaryText)
            views.setTextViewTextSize(R.id.widget_lunar, TypedValue.COMPLEX_UNIT_PX, text.lunarPx)
            views.setTextViewText(R.id.widget_lunar, "第 ${snapshot.weekNumber} 周")
        }
    }

    private fun bindCourseSlot(
        views: RemoteViews,
        containerId: Int,
        bgId: Int?,
        stripId: Int,
        titleId: Int,
        timeId: Int,
        item: CourseWidgetItem?,
        colors: WidgetColors,
        text: WidgetTextSizes,
        emptyTitle: String,
        emptyTime: String?,
        hideIfEmpty: Boolean = false,
        cardWidthDp: Int = 140,
        cardHeightDp: Int = 50
    ) {
        if (item == null && hideIfEmpty) {
            views.setViewVisibility(containerId, View.GONE)
            return
        }
        views.setViewVisibility(containerId, View.VISIBLE)
        val hasItem = item != null
        if (bgId != null) {
            views.setImageViewBitmap(bgId, support.roundedBitmap(cardWidthDp, cardHeightDp, 12, colors.card))
        }
        views.setViewVisibility(stripId, View.VISIBLE)
        bindEventStrip(
            views = views,
            stripId = stripId,
            color = item?.let { safeWidgetColor(it.color, colors.primary) } ?: colors.secondaryText,
            heightDp = eventStripHeight(cardHeightDp)
        )
        views.setTextColor(titleId, if (hasItem) colors.primaryText else colors.secondaryText)
        views.setTextColor(timeId, colors.secondaryText)
        views.setTextViewTextSize(titleId, TypedValue.COMPLEX_UNIT_PX, text.titlePx)
        views.setTextViewTextSize(timeId, TypedValue.COMPLEX_UNIT_PX, text.timePx)
        views.setTextViewText(titleId, item?.title?.ifBlank { "课程" } ?: emptyTitle)
        views.setTextViewText(timeId, item?.let { courseTimeText(it) } ?: emptyTime.orEmpty())
        views.setViewVisibility(timeId, if (item != null || !emptyTime.isNullOrBlank()) View.VISIBLE else View.GONE)
    }

    private fun bindGroupLabel(
        views: RemoteViews,
        labelId: Int,
        date: LocalDate?,
        colors: WidgetColors,
        text: WidgetTextSizes
    ) {
        if (date == null) {
            views.setViewVisibility(labelId, View.GONE)
            views.setTextViewText(labelId, "")
        } else {
            views.setViewVisibility(labelId, View.VISIBLE)
            views.setTextColor(labelId, colors.secondaryText)
            views.setTextViewTextSize(labelId, TypedValue.COMPLEX_UNIT_PX, text.groupLabelPx)
            views.setTextViewText(labelId, "${date.format(dateFormatter)} ${weekdayShort(date.dayOfWeek.value)}")
        }
    }

    private fun bindCompactCourseSideItemSlot(
        views: RemoteViews,
        labelId: Int,
        containerId: Int,
        bgId: Int,
        stripId: Int,
        titleId: Int,
        timeId: Int,
        item: CourseWidgetItem?,
        today: LocalDate,
        colors: WidgetColors,
        text: WidgetTextSizes
    ) {
        if (item == null) {
            bindGroupLabel(views, labelId, null, colors, text)
            views.setViewVisibility(containerId, View.GONE)
            return
        }
        views.setViewVisibility(labelId, View.VISIBLE)
        views.setTextColor(labelId, colors.secondaryText)
        views.setTextViewTextSize(labelId, TypedValue.COMPLEX_UNIT_PX, text.groupLabelPx)
        views.setTextViewText(labelId, compactDayLabel(item.date, today))
        bindCourseSlot(views, containerId, bgId, stripId, titleId, timeId, item, colors, text, "后续暂无课程", null, hideIfEmpty = false, cardWidthDp = 140, cardHeightDp = 58)
    }

    private fun bindCourseSummaryChip(
        views: RemoteViews,
        containerId: Int,
        bgId: Int,
        stripId: Int,
        titleId: Int,
        timeId: Int,
        count: Int,
        colors: WidgetColors,
        text: WidgetTextSizes
    ) {
        if (count <= 0) {
            views.setViewVisibility(containerId, View.GONE)
            views.setTextViewText(titleId, "")
            views.setTextViewText(timeId, "")
            return
        }
        views.setViewVisibility(containerId, View.VISIBLE)
        views.setImageViewBitmap(bgId, support.roundedBitmap(140, 22, 8, colors.card))
        views.setViewVisibility(stripId, View.VISIBLE)
        bindEventStrip(views, stripId, colors.primary, heightDp = 12)
        views.setTextColor(titleId, colors.primaryText)
        views.setTextViewTextSize(titleId, TypedValue.COMPLEX_UNIT_PX, text.titlePx)
        views.setTextViewText(titleId, "其他 $count 门课")
        views.setViewVisibility(timeId, View.GONE)
        views.setTextViewText(timeId, "")
    }

    private fun bindEventStrip(views: RemoteViews, stripId: Int, color: Int, heightDp: Int) {
        views.setImageViewBitmap(stripId, support.roundedBitmap(4, heightDp, 2, color))
    }

    private fun eventStripHeight(cardHeightDp: Int): Int {
        return when {
            cardHeightDp <= 0 -> 38
            cardHeightDp <= 52 -> 34
            else -> 38
        }
    }

    private fun compactDayLabel(date: LocalDate, today: LocalDate): String {
        return when (date) {
            today -> "今天"
            today.plusDays(1) -> "明天"
            else -> "${date.format(dateFormatter)} ${weekdayShort(date.dayOfWeek.value)}"
        }
    }

    private fun build4x2CompactItems(snapshot: CourseWidgetSnapshot, displayedTodayItem: CourseWidgetItem?): List<CourseWidgetItem> {
        val tomorrow = snapshot.today.plusDays(1)
        return snapshot.items.filter { item ->
            (item.date == snapshot.today || item.date == tomorrow) && item !== displayedTodayItem
        }
    }

    private fun courseTimeText(item: CourseWidgetItem): String {
        val place = item.location.ifBlank { item.teacher }
        return buildString {
            append(item.startTime)
            if (item.endTime.isNotBlank()) append("-${item.endTime}")
            append(" · ")
            append(item.nodeText)
            if (place.isNotBlank()) append(" · $place")
        }
    }

    private fun parseTime(value: String): LocalTime? = runCatching { LocalTime.parse(value) }.getOrNull()
}
