package com.antgskds.calendarassistant.platform.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.platform.widget.model.WidgetThemeMode
import com.antgskds.calendarassistant.platform.widget.ui.contract.WidgetConfigureThemeOption
import com.antgskds.calendarassistant.platform.widget.ui.contract.WidgetConfigureUiAction
import com.antgskds.calendarassistant.platform.widget.ui.contract.WidgetConfigureUiState
import com.antgskds.calendarassistant.platform.widget.ui.render.WidgetConfigureScreen
import com.antgskds.calendarassistant.app.ui.theme.CalendarAssistantStyleTheme
import com.antgskds.calendarassistant.app.ui.theme.ThemeColorScheme
import kotlin.math.roundToInt

open class WidgetConfigureActivity : ComponentActivity() {
    protected open val widgetType: WidgetType = WidgetType.SCHEDULE

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var store: WidgetInstanceConfigStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val app = applicationContext as App
        store = WidgetInstanceConfigStore(applicationContext)
        val settings = app.settingsQueryApi.settings.value
        val config = store.ensureConfig(appWidgetId, widgetType, settings)
        val initialState = WidgetConfigureUiState(
            widgetName = widgetType.displayName,
            selectedTheme = config.appearance.themeMode.toConfigureThemeOption(),
            backgroundAlphaPercent = (config.appearance.backgroundAlpha * 100f)
                .roundToInt()
                .coerceIn(60, 100)
        )
        val themeColorScheme = ThemeColorScheme.fromName(settings.themeColorScheme)
        val isDarkTheme = when (settings.themeMode) {
            1 -> resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            2 -> false
            3 -> true
            else -> false
        }

        setContent {
            var state by remember(initialState) { mutableStateOf(initialState) }
            CalendarAssistantStyleTheme(
                darkTheme = isDarkTheme,
                dynamicColor = themeColorScheme == ThemeColorScheme.DEFAULT,
                themeColorScheme = themeColorScheme,
                customThemeColorHex = settings.customThemeColorHex
            ) {
                WidgetConfigureScreen(
                    state = state,
                    onAction = { action ->
                        when (action) {
                            is WidgetConfigureUiAction.SelectTheme -> {
                                state = state.copy(selectedTheme = action.theme)
                            }

                            is WidgetConfigureUiAction.ChangeBackgroundAlpha -> {
                                state = state.copy(
                                    backgroundAlphaPercent = action.percent.coerceIn(60, 100)
                                )
                            }

                            WidgetConfigureUiAction.Save -> saveAndFinish(state)
                            WidgetConfigureUiAction.Exit -> finish()
                        }
                    }
                )
            }
        }
    }

    private fun saveAndFinish(state: WidgetConfigureUiState) {
        val app = applicationContext as App
        store.saveConfig(
            appWidgetId,
            store.getConfig(appWidgetId, widgetType, app.settingsQueryApi.settings.value).copy(
                appearance = WidgetAppearanceConfig(
                    themeMode = state.selectedTheme.toWidgetThemeMode(),
                    backgroundAlpha = (state.backgroundAlphaPercent / 100f).coerceIn(0.6f, 1f)
                )
            )
        )
        val manager = AppWidgetManager.getInstance(applicationContext)
        app.widgetCenter.refreshWidgets(manager, intArrayOf(appWidgetId), widgetType)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, result)
        finish()
    }

    private fun Int.toConfigureThemeOption(): WidgetConfigureThemeOption = when (this) {
        WidgetThemeMode.LIGHT -> WidgetConfigureThemeOption.LIGHT
        WidgetThemeMode.DARK -> WidgetConfigureThemeOption.DARK
        else -> WidgetConfigureThemeOption.FOLLOW_APP
    }

    private fun WidgetConfigureThemeOption.toWidgetThemeMode(): Int = when (this) {
        WidgetConfigureThemeOption.FOLLOW_APP -> WidgetThemeMode.FOLLOW_APP
        WidgetConfigureThemeOption.LIGHT -> WidgetThemeMode.LIGHT
        WidgetConfigureThemeOption.DARK -> WidgetThemeMode.DARK
    }
}

class ScheduleWidgetConfigureActivity : WidgetConfigureActivity() {
    override val widgetType: WidgetType = WidgetType.SCHEDULE
}

class WeatherWidgetConfigureActivity : WidgetConfigureActivity() {
    override val widgetType: WidgetType = WidgetType.WEATHER
}

class CourseWidgetConfigureActivity : WidgetConfigureActivity() {
    override val widgetType: WidgetType = WidgetType.COURSE
}
