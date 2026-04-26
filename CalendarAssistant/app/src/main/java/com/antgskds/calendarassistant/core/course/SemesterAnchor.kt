package com.antgskds.calendarassistant.core.course

import java.time.LocalDate

fun currentWeekMonday(date: LocalDate): LocalDate {
    return date.minusDays((date.dayOfWeek.value - 1).toLong())
}

fun resolveSemesterAnchor(semesterStartDate: String?, today: LocalDate = LocalDate.now()): LocalDate {
    if (semesterStartDate.isNullOrBlank()) return currentWeekMonday(today)
    return runCatching { LocalDate.parse(semesterStartDate) }.getOrElse { currentWeekMonday(today) }
}

fun hasConfiguredSemesterAnchor(semesterStartDate: String?): Boolean {
    if (semesterStartDate.isNullOrBlank()) return false
    return runCatching { LocalDate.parse(semesterStartDate) }.isSuccess
}

fun calculateSemesterWeek(semesterStartDate: String?, targetDate: LocalDate): Int {
    val start = resolveSemesterAnchor(semesterStartDate, targetDate)
    val days = java.time.temporal.ChronoUnit.DAYS.between(start, targetDate)
    return (days / 7).toInt() + 1
}
