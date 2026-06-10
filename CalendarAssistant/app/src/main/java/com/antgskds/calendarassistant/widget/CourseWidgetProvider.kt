package com.antgskds.calendarassistant.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.antgskds.calendarassistant.App

class CourseWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        widgetCenter(context)?.refreshWidgets(appWidgetManager, appWidgetIds, WidgetType.COURSE)
    }

    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: android.os.Bundle) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        widgetCenter(context)?.refreshWidgets(appWidgetManager, intArrayOf(appWidgetId), WidgetType.COURSE)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        widgetCenter(context)?.deleteWidgetConfig(appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WidgetActions.ACTION_COURSE_SEGMENT_UP -> {
                val appWidgetId = intent.getIntExtra(WidgetActions.EXTRA_APP_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) widgetCenter(context)?.shiftCourseSegment(appWidgetId, -1)
                return
            }
            WidgetActions.ACTION_COURSE_SEGMENT_DOWN -> {
                val appWidgetId = intent.getIntExtra(WidgetActions.EXTRA_APP_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) widgetCenter(context)?.shiftCourseSegment(appWidgetId, 1)
                return
            }
        }
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> widgetCenter(context)?.requestRefresh(WidgetType.COURSE)
        }
    }

    private fun widgetCenter(context: Context) = (context.applicationContext as? App)?.widgetCenter
}
