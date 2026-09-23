package com.antgskds.calendarassistant.feature.schedule.ui.render.material

import kotlin.math.roundToInt

/** 表头、网格和日程共用边界；用相邻边界之差计算宽度，不逐列累积取整误差。 */
internal fun calendarGridBoundary(sizePx: Int, index: Int, count: Int): Int {
    require(sizePx >= 0 && count > 0 && index in 0..count)
    return ((sizePx.toLong() * index + count / 2) / count).toInt()
}

internal fun calendarMinuteOffset(hourHeightPx: Int, minute: Int): Int =
    (hourHeightPx * (minute / 60.0)).roundToInt()
