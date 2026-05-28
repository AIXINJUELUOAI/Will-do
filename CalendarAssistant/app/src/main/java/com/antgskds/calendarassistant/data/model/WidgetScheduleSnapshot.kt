package com.antgskds.calendarassistant.data.model

import java.time.LocalDate

data class WidgetScheduleSnapshot(
    val today: LocalDate,
    val visibleEntries: List<WidgetScheduleEntry>,
    val todayItems: List<ScheduleDisplayItem>,
    val upcomingItems: List<ScheduleDisplayItem>,
    val upcomingGroups: List<WidgetScheduleGroup>
) {
    val nextItem: ScheduleDisplayItem? get() = upcomingItems.firstOrNull()
}

data class WidgetScheduleEntry(
    val date: LocalDate,
    val item: ScheduleDisplayItem
)

data class WidgetScheduleGroup(
    val date: LocalDate,
    val items: List<ScheduleDisplayItem>
)

object WidgetThemeMode {
    const val FOLLOW_APP = 0
    const val LIGHT = 1
    const val DARK = 2
}
