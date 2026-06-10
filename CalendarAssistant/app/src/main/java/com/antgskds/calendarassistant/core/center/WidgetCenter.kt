package com.antgskds.calendarassistant.core.center

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import com.antgskds.calendarassistant.core.query.CalendarQueryApi
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.core.query.WidgetScheduleQueryApi
import com.antgskds.calendarassistant.core.query.WeatherQueryApi
import com.antgskds.calendarassistant.core.weather.hasWeatherConfig
import com.antgskds.calendarassistant.widget.ScheduleWidgetProvider
import com.antgskds.calendarassistant.widget.ScheduleWidgetRenderer
import com.antgskds.calendarassistant.widget.WeatherWidgetProvider
import com.antgskds.calendarassistant.widget.WeatherWidgetRenderer
import com.antgskds.calendarassistant.widget.CourseWidgetProvider
import com.antgskds.calendarassistant.widget.CourseWidgetRenderer
import com.antgskds.calendarassistant.widget.WidgetType
import com.antgskds.calendarassistant.widget.WidgetInstanceConfigStore
import com.antgskds.calendarassistant.widget.WeatherWidgetSnapshot
import com.antgskds.calendarassistant.widget.CourseWidgetSnapshotBuilder
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
    private val weatherQueryApi: WeatherQueryApi,
    private val appScope: CoroutineScope
) {
    private val scheduleRenderer = ScheduleWidgetRenderer(appContext)
    private val weatherRenderer = WeatherWidgetRenderer(appContext)
    private val courseRenderer = CourseWidgetRenderer(appContext)
    private val configStore = WidgetInstanceConfigStore(appContext)
    private var refreshJob: Job? = null
    private val typedRefreshJobs = mutableMapOf<WidgetType, Job>()
    private var subscriptionsStarted = false

    fun startSubscriptions() {
        if (subscriptionsStarted) return
        subscriptionsStarted = true

        appScope.launch {
            settingsQueryApi.settings.collectLatest {
                requestRefresh()
            }
        }
        appScope.launch {
            weatherQueryApi.weatherData.collectLatest {
                requestRefresh(WidgetType.WEATHER)
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

    fun requestRefresh(type: WidgetType) {
        typedRefreshJobs[type]?.cancel()
        typedRefreshJobs[type] = appScope.launch(Dispatchers.IO) {
            delay(200)
            refreshWidgetsOfType(type)
        }
    }

    fun refreshAllWidgets() {
        WidgetType.entries.forEach { refreshWidgetsOfType(it) }
    }

    fun refreshWidgetsOfType(type: WidgetType) {
        val manager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, providerClass(type))
        val ids = manager.getAppWidgetIds(component)
        refreshWidgets(manager, ids, type)
    }

    fun refreshWidgets(manager: AppWidgetManager, appWidgetIds: IntArray, type: WidgetType = WidgetType.SCHEDULE) {
        if (appWidgetIds.isEmpty()) return
        appScope.launch(Dispatchers.IO) {
            val settings = settingsQueryApi.settings.value
            val events by lazy { calendarQueryApi.getEvents() }
            val scheduleSnapshot by lazy { widgetScheduleQueryApi.buildSnapshot(events) }
            val weatherSnapshot by lazy { WeatherWidgetSnapshot(if (settings.hasWeatherConfig()) weatherQueryApi.weatherData.value else null) }
            val courseSnapshot by lazy { CourseWidgetSnapshotBuilder.build(events, settings) }
            appWidgetIds.forEach { widgetId ->
                val config = configStore.ensureConfig(widgetId, type, settings)
                val options = manager.getAppWidgetOptions(widgetId)
                val views = when (type) {
                    WidgetType.SCHEDULE -> scheduleRenderer.render(widgetId, options, scheduleSnapshot, settings, config)
                    WidgetType.WEATHER -> weatherRenderer.render(widgetId, options, weatherSnapshot, settings, config)
                    WidgetType.COURSE -> courseRenderer.render(widgetId, options, courseSnapshot, settings, config)
                }
                withContext(Dispatchers.Main) {
                    manager.updateAppWidget(widgetId, views)
                }
            }
        }
    }

    fun deleteWidgetConfig(appWidgetIds: IntArray) {
        appWidgetIds.forEach(configStore::deleteConfig)
    }

    fun shiftCourseSegment(appWidgetId: Int, delta: Int) {
        val settings = settingsQueryApi.settings.value
        val config = configStore.getConfig(appWidgetId, WidgetType.COURSE, settings)
        val sections = CourseWidgetSnapshotBuilder.buildSections(settings).map { it.segment }
        if (sections.isEmpty()) return
        val currentIndex = sections.indexOf(config.courseSegment).takeIf { it >= 0 } ?: 0
        val nextIndex = (currentIndex + delta).coerceIn(0, sections.lastIndex)
        if (nextIndex == currentIndex) return
        configStore.saveConfig(appWidgetId, config.copy(courseSegment = sections[nextIndex]))
        refreshWidgets(AppWidgetManager.getInstance(appContext), intArrayOf(appWidgetId), WidgetType.COURSE)
    }

    private fun providerClass(type: WidgetType): Class<*> = when (type) {
        WidgetType.SCHEDULE -> ScheduleWidgetProvider::class.java
        WidgetType.WEATHER -> WeatherWidgetProvider::class.java
        WidgetType.COURSE -> CourseWidgetProvider::class.java
    }
}
