package com.antgskds.calendarassistant.core.util

import android.content.Context
import android.graphics.Point
import android.os.Build
import android.view.WindowManager

object ScreenMetrics {

    fun getScreenSize(context: Context): Pair<Int, Int> {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            metrics.bounds.width() to metrics.bounds.height()
        } else {
            val point = Point()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealSize(point)
            point.x to point.y
        }
    }

    fun getStatusBarHeight(context: Context): Int {
        val res = context.resources
        return res.getDimensionPixelSize(
            res.getIdentifier("status_bar_height", "dimen", "android")
        )
    }

    fun getNavigationBarHeight(context: Context): Int {
        val res = context.resources
        return res.getDimensionPixelSize(
            res.getIdentifier("navigation_bar_height", "dimen", "android")
        )
    }
}
