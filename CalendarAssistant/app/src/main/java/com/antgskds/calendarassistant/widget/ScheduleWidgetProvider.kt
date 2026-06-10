package com.antgskds.calendarassistant.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import com.antgskds.calendarassistant.App

class ScheduleWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        widgetCenter(context)?.refreshWidgets(appWidgetManager, appWidgetIds, WidgetType.SCHEDULE)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        widgetCenter(context)?.refreshWidgets(appWidgetManager, intArrayOf(appWidgetId), WidgetType.SCHEDULE)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        widgetCenter(context)?.deleteWidgetConfig(appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> widgetCenter(context)?.requestRefresh(WidgetType.SCHEDULE)
        }
    }

    private fun widgetCenter(context: Context) = (context.applicationContext as? App)?.widgetCenter
}
