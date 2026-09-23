package com.antgskds.calendarassistant.feature.home.domain

import com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem
import java.time.LocalDate
import kotlin.math.abs

data class HomeAgendaRow(val date: LocalDate, val item: ScheduleDisplayItem? = null, val startsDay: Boolean = true) {
    val key: String get() = item?.let { "agenda:" + it.stableKey } ?: ("agenda-date:" + date)
}

/** 日期组顺序与同日时间顺序分离；仅日程跳过空日期，完整模式补齐已加载范围。 */
object HomeAgendaMapper {
    /** 空窗口不等于没有后续；是否还能加载由重复展开结果决定。 */
    fun loadHint(
        canLoadMore: Boolean,
        loading: Boolean,
        emptyWindow: Boolean,
        reverse: Boolean,
        readyToRelease: Boolean,
    ): String {
        val days = com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog.HOME_AGENDA_PAGE_DAYS
        return when {
            loading -> "正在加载后续 " + days + " 天"
            !canLoadMore -> "没有更多日程了"
            readyToRelease -> "松手加载后续 " + days + " 天"
            emptyWindow -> "这 " + days + " 天暂无日程，" + (if (reverse) "下拉" else "上滑") + "继续加载"
            else -> (if (reverse) "下拉" else "上滑") + "加载后续 " + days + " 天"
        }
    }

    fun matches(item: ScheduleDisplayItem, query: String): Boolean = query.isBlank() ||
        listOf(item.title, item.description, item.location).any { it.contains(query.trim(), ignoreCase = true) }

    fun rows(
        items: List<ScheduleDisplayItem>,
        billDates: Set<LocalDate>,
        query: String,
        reverseDates: Boolean,
        onlyScheduled: Boolean = true,
        loadedRange: ClosedRange<LocalDate>? = null,
    ): List<HomeAgendaRow> {
        val groups = items.distinctBy { it.stableKey }.filter { matches(it, query) }.groupBy { it.startDate }
        val scheduledDates = groups.keys
        val dates = if (query.isNotBlank() || onlyScheduled) scheduledDates.sorted() else {
            val knownDates = scheduledDates + billDates
            val start = (knownDates + listOfNotNull(loadedRange?.start)).minOrNull()
            val end = (knownDates + listOfNotNull(loadedRange?.endInclusive)).maxOrNull()
            if (start == null || end == null) emptyList()
            else generateSequence(start) { if (it < end) it.plusDays(1) else null }.toList()
        }
        return (if (reverseDates) dates.reversed() else dates).flatMap { date ->
            val sorted = groups[date].orEmpty()
                .sortedWith(compareBy<ScheduleDisplayItem> { it.startTS }.thenBy { it.stableKey })
            if (sorted.isEmpty()) listOf(HomeAgendaRow(date))
            else sorted.mapIndexed { index, item -> HomeAgendaRow(date, item, startsDay = index == 0) }
        }
    }

    /** 已选日期仍可见时保持；离开后按实际离开边缘接力，与日期正反序无关。 */
    fun followSelection(selected: LocalDate, visibleDates: List<LocalDate>, towardTop: Boolean): LocalDate =
        if (selected in visibleDates || visibleDates.isEmpty()) selected
        else if (towardTop) visibleDates.first() else visibleDates.last()

    fun anchorIndex(rows: List<HomeAgendaRow>, date: LocalDate): Int {
        val nearest = rows.withIndex().filter { it.value.startsDay }
            .minWithOrNull(compareBy<IndexedValue<HomeAgendaRow>> { abs(it.value.date.toEpochDay() - date.toEpochDay()) }
                .thenBy { it.value.date })
        return nearest?.index ?: 0
    }
}
