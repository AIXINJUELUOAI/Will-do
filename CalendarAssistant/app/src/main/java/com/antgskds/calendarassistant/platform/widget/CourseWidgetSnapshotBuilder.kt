package com.antgskds.calendarassistant.platform.widget

import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.core.center.ScheduleDisplayHelper
import com.antgskds.calendarassistant.core.course.CourseEventMapper
import com.antgskds.calendarassistant.core.course.TimeTableLayoutUtils
import com.antgskds.calendarassistant.core.course.calculateSemesterWeek
import com.antgskds.calendarassistant.core.course.currentWeekMonday
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.TimeNode
import java.time.LocalDate

object CourseWidgetSnapshotBuilder {
    fun build(events: List<Event>, settings: MySettings, today: LocalDate = LocalDate.now()): CourseWidgetSnapshot {
        val weekStart = currentWeekMonday(today)
        val weekEnd = weekStart.plusDays(6)
        val nodes = resolveNodes(settings)
        val items = ScheduleDisplayHelper.buildDisplayItems(
            events = events.filter { it.archivedAt == null && it.tag == EventTags.COURSE },
            from = weekStart,
            to = weekEnd
        ).map { item ->
            val meta = CourseEventMapper.parseMeta(item.description)
            CourseWidgetItem(
                title = item.title,
                location = item.location,
                teacher = meta?.teacher.orEmpty(),
                date = item.startDate,
                dayOfWeek = item.startDate.dayOfWeek.value,
                startNode = meta?.startNode ?: inferNode(nodes, item.startTime, start = true),
                endNode = meta?.endNode ?: inferNode(nodes, item.endTime, start = false),
                startTime = item.startTime,
                endTime = item.endTime,
                color = item.color
            )
        }.sortedWith(compareBy<CourseWidgetItem> { it.date }.thenBy { it.startNode }.thenBy { it.startTime })

        return CourseWidgetSnapshot(
            today = today,
            weekStart = weekStart,
            weekNumber = calculateSemesterWeek(settings.semesterStartDate, today).coerceAtLeast(1),
            nodes = nodes,
            items = items,
            sections = buildSections(nodes)
        )
    }

    fun buildSections(settings: MySettings): List<CourseWidgetSection> {
        return buildSections(resolveNodes(settings))
    }

    private fun resolveNodes(settings: MySettings): List<TimeNode> {
        return TimeTableLayoutUtils.parseNodes(settings.timeTableJson).takeIf { it.isNotEmpty() }
            ?: TimeTableLayoutUtils.generateNodes(
                TimeTableLayoutUtils.resolveLayoutConfig(settings.timeTableConfigJson, settings.timeTableJson)
            )
    }

    private fun inferNode(nodes: List<TimeNode>, time: String, start: Boolean): Int {
        val matched = nodes.firstOrNull { node ->
            if (start) node.startTime == time else node.endTime == time
        }
        return matched?.index ?: 1
    }

    private fun buildSections(nodes: List<TimeNode>): List<CourseWidgetSection> {
        val fallback = TimeTableLayoutUtils.inferConfig(nodes)
        fun rangeForPeriod(period: String, fallbackRange: IntRange): IntRange? {
            val filtered = nodes.filter { it.period.equals(period, ignoreCase = true) }.map { it.index }
            return if (filtered.isNotEmpty()) filtered.minOrNull()!!..filtered.maxOrNull()!! else fallbackRange.takeIf { it.first <= it.last }
        }
        return listOfNotNull(
            rangeForPeriod("morning", 1..fallback.morningCount)?.let { CourseWidgetSection(CourseWidgetSegment.MORNING, it) },
            rangeForPeriod("afternoon", fallback.afternoonStartNode..fallback.dinnerBoundaryNode)?.let { CourseWidgetSection(CourseWidgetSegment.AFTERNOON, it) },
            rangeForPeriod("night", fallback.nightStartNode..fallback.totalNodes)?.let { CourseWidgetSection(CourseWidgetSegment.NIGHT, it) }
        )
    }
}
