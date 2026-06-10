package com.antgskds.calendarassistant.widget

import android.graphics.Color
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.antgskds.calendarassistant.R

fun RemoteViews.bindWidgetBackground(support: WidgetRenderingSupport, colors: WidgetColors, rootId: Int = R.id.widget_root) {
    setImageViewBitmap(R.id.widget_background, support.solidBitmap(colors.background))
}

fun RemoteViews.bindText(
    id: Int,
    value: String,
    color: Int,
    textSizePx: Float? = null,
    visible: Boolean = true
) {
    setViewVisibility(id, if (visible) View.VISIBLE else View.GONE)
    setTextColor(id, color)
    if (textSizePx != null) setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, textSizePx)
    setTextViewText(id, value)
}

fun safeWidgetColor(color: Int, fallback: Int): Int {
    if (color == 0) return fallback
    return if (Color.alpha(color) == 0) Color.rgb(Color.red(color), Color.green(color), Color.blue(color)) else color
}

fun weekdayShort(value: Int): String = when (value.coerceIn(1, 7)) {
    1 -> "周一"
    2 -> "周二"
    3 -> "周三"
    4 -> "周四"
    5 -> "周五"
    6 -> "周六"
    else -> "周日"
}
