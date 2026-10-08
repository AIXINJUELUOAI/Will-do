package com.antgskds.calendarassistant.feature.home.domain

/** 仅决定当前可显示模式；不把宽屏/课表停用的临时回退写入偏好。 */
object HomeCalendarViewPolicy {
    val modes = listOf("TODAY", "WEEK", "MONTH", "AGENDA", "COURSE")
    fun normalize(name: String): String = name.takeIf { it in modes } ?: "TODAY"
    fun resolve(name: String, twoPane: Boolean, courseEnabled: Boolean): String {
        val allowed = if (twoPane) listOf("WEEK", "MONTH") + if (courseEnabled) listOf("COURSE") else emptyList()
            else listOf("TODAY", "WEEK", "MONTH", "AGENDA")
        return normalize(name).takeIf { it in allowed } ?: if (twoPane) "WEEK" else "TODAY"
    }
}
