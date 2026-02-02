package com.antgskds.calendarassistant.core.util

import java.time.LocalDate
import java.time.temporal.ChronoUnit

object LunarCalendarUtils {

    // 农历数据 1900-2100 年 (数据源保持 Claude 提供的标准表)
    private val lunarInfo = longArrayOf(
        0x04bd8, 0x04ae0, 0x0a570, 0x054d5, 0x0d260, 0x0d950, 0x16554, 0x056a0, 0x09ad0, 0x055d2,
        0x04ae0, 0x0a5b6, 0x0a4d0, 0x0d250, 0x1d255, 0x0b540, 0x0d6a0, 0x0ada2, 0x095b0, 0x14977,
        0x04970, 0x0a4b0, 0x0b4b5, 0x06a50, 0x06d40, 0x1ab54, 0x02b60, 0x09570, 0x052f2, 0x04970,
        0x06566, 0x0d4a0, 0x0ea50, 0x06e95, 0x05ad0, 0x02b60, 0x186e3, 0x092e0, 0x1c8d7, 0x0c950,
        0x0d4a0, 0x1d8a6, 0x0b550, 0x056a0, 0x1a5b4, 0x025d0, 0x092d0, 0x0d2b2, 0x0a950, 0x0b557,
        0x06ca0, 0x0b550, 0x15355, 0x04da0, 0x0a5b0, 0x14573, 0x052b0, 0x0a9a8, 0x0e950, 0x06aa0,
        0x0aea6, 0x0ab50, 0x04b60, 0x0aae4, 0x0a570, 0x05260, 0x0f263, 0x0d950, 0x05b57, 0x056a0,
        0x096d0, 0x04dd5, 0x04ad0, 0x0a4d0, 0x0d4d4, 0x0d250, 0x0d558, 0x0b540, 0x0b6a0, 0x195a6,
        0x095b0, 0x049b0, 0x0a974, 0x0a4b0, 0x0b27a, 0x06a50, 0x06d40, 0x0af46, 0x0ab60, 0x09570,
        0x04af5, 0x04970, 0x064b0, 0x074a3, 0x0ea50, 0x06b58, 0x05ac0, 0x0ab60, 0x096d5, 0x092e0,
        0x0c960, 0x0d954, 0x0d4a0, 0x0da50, 0x07552, 0x056a0, 0x0abb7, 0x025d0, 0x092d0, 0x0cab5,
        0x0a950, 0x0b4a0, 0x0baa4, 0x0ad50, 0x055d9, 0x04ba0, 0x0a5b0, 0x15176, 0x052b0, 0x0a930,
        0x07954, 0x06aa0, 0x0ad50, 0x05b52, 0x04b60, 0x0a6e6, 0x0a4e0, 0x0d260, 0x0ea65, 0x0d530,
        0x05aa0, 0x076a3, 0x096d0, 0x04afb, 0x04ad0, 0x0a4d0, 0x1d0b6, 0x0d250, 0x0d520, 0x0dd45,
        0x0b5a0, 0x056d0, 0x055b2, 0x049b0, 0x0a577, 0x0a4b0, 0x0aa50, 0x1b255, 0x06d20, 0x0ada0,
        0x14b63, 0x09370, 0x049f8, 0x04970, 0x064b0, 0x168a6, 0x0ea50, 0x06b20, 0x1a6c4, 0x0aae0,
        0x0a2e0, 0x0d2e3, 0x0c960, 0x0d557, 0x0d4a0, 0x0da50, 0x05d55, 0x056a0, 0x0a6d0, 0x055d4,
        0x052d0, 0x0a9b8, 0x0a950, 0x0b4a0, 0x0b6a6, 0x0ad50, 0x055a0, 0x0aba4, 0x0a5b0, 0x052b0,
        0x0b273, 0x06930, 0x07337, 0x06aa0, 0x0ad50, 0x14b55, 0x04b60, 0x0a570, 0x054e4, 0x0d160,
        0x0e968, 0x0d520, 0x0daa0, 0x16aa6, 0x056d0, 0x04ae0, 0x0a9d4, 0x0a2d0, 0x0d150, 0x0f252,
        0x0d520
    )

    // 基准日期：1900年1月31日（农历1900年正月初一）
    private val baseDate = LocalDate.of(1900, 1, 31)

    private val monthNames = listOf("正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊")
    private val dayNames = listOf(
        "初一", "初二", "初三", "初四", "初五", "初六", "初七", "初八", "初九", "初十",
        "十一", "十二", "十三", "十四", "十五", "十六", "十七", "十八", "十九", "二十",
        "廿一", "廿二", "廿三", "廿四", "廿五", "廿六", "廿七", "廿八", "廿九", "三十"
    )

    fun getLunarDate(date: LocalDate): String {
        // 0. 计算天数差
        var offsetDays = ChronoUnit.DAYS.between(baseDate, date).toInt()

        if (offsetDays < 0) return "" // 不支持1900前

        var year = 0
        var daysInYear = 0

        // 1. 递减年份
        while (year < lunarInfo.size) {
            daysInYear = getDaysInLunarYear(year)
            if (offsetDays < daysInYear) {
                break
            }
            offsetDays -= daysInYear
            year++
        }

        if (year >= lunarInfo.size) return ""

        // 2. 递减月份（修正版：线性化处理闰月逻辑）
        val leapMonth = getLeapMonth(year)
        var tempDays = offsetDays
        var month = 0
        var isLeapMonth = false

        while (month < 12) {
            // 2.1 获取普通月天数
            val daysInNormal = getDaysInLunarMonth(year, month)

            // 2.2 检查是否就在当前普通月
            if (tempDays < daysInNormal) {
                break
            }
            tempDays -= daysInNormal

            // 2.3 检查当前月之后是否有闰月
            if (leapMonth > 0 && (month + 1) == leapMonth) {
                val daysInLeap = getDaysInLeapMonth(year)
                if (tempDays < daysInLeap) {
                    isLeapMonth = true
                    break
                }
                tempDays -= daysInLeap
            }
            month++
        }

        val day = tempDays + 1
        val monthStr = if (isLeapMonth) "闰${monthNames[month]}" else monthNames[month]

        return "${monthStr}月${dayNames[day - 1]}"
    }

    /**
     * 获取农历年总天数 (348 + 大月数 + 闰月数)
     * 修正：使用正确的位运算循环
     */
    private fun getDaysInLunarYear(year: Int): Int {
        var days = 348 // 29天 * 12个月
        val info = lunarInfo[year]

        // 计算12个月中有多少个大月 (位 4 到 15)
        // 0x8000 (1000...0) 是第15位，对应正月
        // 0x8 (1000) 是第4位，对应腊月
        for (i in 0..11) {
            // 检查第 (15 - i) 位
            if ((info and (0x8000L shr i)) != 0L) {
                days++ // 大月加1天 (变30)
            }
        }

        // 如果有闰月，加上闰月天数
        val leapMonth = getLeapMonth(year)
        if (leapMonth > 0) {
            days += getDaysInLeapMonth(year)
        }
        return days
    }

    /**
     * 获取闰月天数
     * 修正：位 16 (0x10000) 存放闰月大小
     */
    private fun getDaysInLeapMonth(year: Int): Int {
        return if ((lunarInfo[year] and 0x10000L) != 0L) 30 else 29
    }

    /**
     * 获取普通月天数
     * 修正：位 15 到 4 存放 1-12月大小
     * @param month 0-11
     */
    private fun getDaysInLunarMonth(year: Int, month: Int): Int {
        // 0x8000 (第15位) 对应 month 0
        return if ((lunarInfo[year] and (0x8000L shr month)) != 0L) 30 else 29
    }

    /**
     * 获取闰哪个月 (0-12, 0代表无闰月)
     * 位 0-3
     */
    private fun getLeapMonth(year: Int): Int {
        return (lunarInfo[year] and 0xfL).toInt()
    }
}
