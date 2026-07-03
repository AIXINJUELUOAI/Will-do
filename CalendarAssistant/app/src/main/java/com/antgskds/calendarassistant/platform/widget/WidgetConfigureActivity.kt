package com.antgskds.calendarassistant.platform.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.data.model.WidgetThemeMode
import kotlin.math.roundToInt

open class WidgetConfigureActivity : Activity() {
    protected open val widgetType: WidgetType = WidgetType.SCHEDULE

    private var appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var store: WidgetInstanceConfigStore
    private var selectedThemeMode: Int = WidgetThemeMode.FOLLOW_APP
    private var selectedAlpha: Float = 0.9f

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
        selectedThemeMode = config.appearance.themeMode
        selectedAlpha = config.appearance.backgroundAlpha

        setContentView(buildContentView())
    }

    private fun buildContentView(): LinearLayout {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).roundToInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        root.addView(TextView(this).apply {
            text = widgetType.displayName
            textSize = 22f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = "这些设置只作用于当前桌面小组件。"
            textSize = 14f
            setPadding(0, dp(8), 0, dp(20))
        })

        root.addView(TextView(this).apply {
            text = "主题"
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })
        val radioGroup = RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(0, dp(8), 0, dp(16))
        }
        listOf(
            WidgetThemeMode.FOLLOW_APP to "跟随软件",
            WidgetThemeMode.LIGHT to "浅色",
            WidgetThemeMode.DARK to "深色"
        ).forEach { (mode, label) ->
            radioGroup.addView(RadioButton(this).apply {
                id = mode + 100
                text = label
                textSize = 15f
                isChecked = selectedThemeMode == mode
            })
        }
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedThemeMode = (checkedId - 100).coerceIn(WidgetThemeMode.FOLLOW_APP, WidgetThemeMode.DARK)
        }
        root.addView(radioGroup)

        val alphaLabel = TextView(this).apply {
            text = alphaText(selectedAlpha)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        root.addView(alphaLabel)
        root.addView(SeekBar(this).apply {
            max = 40
            progress = ((selectedAlpha - 0.6f) * 100f).roundToInt().coerceIn(0, 40)
            setPadding(0, dp(8), 0, dp(20))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    selectedAlpha = (0.6f + progress / 100f).coerceIn(0.6f, 1f)
                    alphaLabel.text = alphaText(selectedAlpha)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        })

        root.addView(Button(this).apply {
            text = "保存"
            textSize = 16f
            gravity = Gravity.CENTER
            setOnClickListener { saveAndFinish() }
        })
        return root
    }

    private fun saveAndFinish() {
        val app = applicationContext as App
        store.saveConfig(
            appWidgetId,
            store.getConfig(appWidgetId, widgetType, app.settingsQueryApi.settings.value).copy(
                appearance = WidgetAppearanceConfig(selectedThemeMode, selectedAlpha)
            )
        )
        val manager = AppWidgetManager.getInstance(applicationContext)
        app.widgetCenter.refreshWidgets(manager, intArrayOf(appWidgetId), widgetType)
        val result = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        setResult(RESULT_OK, result)
        finish()
    }

    private fun alphaText(value: Float): String = "背景不透明度 ${(value * 100f).roundToInt()}%"
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
