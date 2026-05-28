package com.antgskds.calendarassistant.core.center

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.antgskds.calendarassistant.core.query.CalendarQueryApi
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.core.query.WidgetScheduleQueryApi
import com.antgskds.calendarassistant.widget.ScheduleWidgetProvider
import com.antgskds.calendarassistant.widget.ScheduleWidgetRenderer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WidgetCenter(
    private val appContext: Context,
    private val calendarQueryApi: CalendarQueryApi,
    private val settingsQueryApi: SettingsQueryApi,
    private val widgetScheduleQueryApi: WidgetScheduleQueryApi,
    private val appScope: CoroutineScope
) {
    private val renderer = ScheduleWidgetRenderer(appContext)
    private var refreshJob: Job? = null
    private var subscriptionsStarted = false

    fun startSubscriptions() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true

        appScope.launch {
            settingsQueryApi.settings.collectLatest {
                requestRefresh()
            }
        }
    }

    fun requestRefresh() {
        refreshJob?.cancel()
        refreshJob = appScope.launch(Dispatchers.IO) {
            delay(200)
            refreshAllWidgets()
        }
    }

    fun refreshAllWidgets() {
        val manager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, ScheduleWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        refreshWidgets(manager, ids)
    }

    fun refreshWidgets(manager: AppWidgetManager, appWidgetIds: IntArray) {
        if (appWidgetIds.isEmpty()) return
        appScope.launch(Dispatchers.IO) {
            val snapshot = widgetScheduleQueryApi.buildSnapshot(calendarQueryApi.getEvents())
            val settings = settingsQueryApi.settings.value
            appWidgetIds.forEach { widgetId ->
                val options = manager.getAppWidgetOptions(widgetId)
                val views = renderer.render(widgetId, options, snapshot, settings)
                withContext(Dispatchers.Main) {
                    manager.updateAppWidget(widgetId, views)
                }
            }
        }
    }
}
