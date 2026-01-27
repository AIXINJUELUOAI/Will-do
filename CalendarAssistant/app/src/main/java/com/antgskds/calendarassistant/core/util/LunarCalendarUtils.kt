package com.antgskds.calendarassistant.core.util

import java.time.LocalDate
import android.icu.util.ChineseCalendar
import android.icu.util.Calendar

object LunarCalendarUtils {
    fun getLunarDate(date: LocalDate): String {
        val chineseCalendar = ChineseCalendar()
        chineseCalendar.set(Calendar.YEAR, date.year)
        chineseCalendar.set(Calendar.MONTH, date.monthValue - 1)
        chineseCalendar.set(Calendar.DAY_OF_MONTH, date.dayOfMonth)
        val month = chineseCalendar.get(Calendar.MONTH) + 1
        val day = chineseCalendar.get(Calendar.DAY_OF_MONTH)
        val monthNames = listOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
        val dayNames = listOf(
            "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
            "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
            "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
        )
        return "${monthNames.getOrElse(month - 1) { "" }}月${dayNames.getOrElse(day - 1) { "" }}"
    }
}