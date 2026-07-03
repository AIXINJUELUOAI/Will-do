package com.antgskds.calendarassistant.platform.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import androidx.compose.ui.graphics.toArgb
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.core.util.DensityConfigManager
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.WidgetThemeMode
import com.antgskds.calendarassistant.ui.theme.ThemeColorGenerator
import com.antgskds.calendarassistant.ui.theme.ThemeColorScheme
import com.antgskds.calendarassistant.ui.theme.parseThemeHexColor
import kotlin.math.roundToInt

object WidgetActions {
    const val EXTRA_WIDGET_ACTION = "widget_action"
    const val EXTRA_APP_WIDGET_ID = "app_widget_id"
    const val ACTION_OPEN_HOME = "open_home"
    const val ACTION_OPEN_WEATHER = "open_weather"
    const val ACTION_OPEN_COURSE = "open_course"
    const val ACTION_COURSE_SEGMENT_UP = "com.antgskds.calendarassistant.widget.COURSE_SEGMENT_UP"
    const val ACTION_COURSE_SEGMENT_DOWN = "com.antgskds.calendarassistant.widget.COURSE_SEGMENT_DOWN"
}

class WidgetRenderingSupport(private val context: Context) {
    val density: Float = context.resources.displayMetrics.density

    fun resolveSize(options: Bundle): WidgetSize {
        val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0)
        val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
        return when {
            minWidth >= 220 && minHeight >= 180 -> WidgetSize.CELL_4X4
            minWidth >= 220 -> WidgetSize.CELL_4X2
            minHeight >= 95 -> WidgetSize.CELL_2X2
            else -> WidgetSize.CELL_2X1
        }
    }

    fun resolveTextSizes(size: WidgetSize, settings: MySettings): WidgetTextSizes {
        val uiScale = DensityConfigManager.getScaleFactor(settings.uiSize) / DensityConfigManager.getScaleFactor(2)
        val fontScale = Resources.getSystem().configuration.fontScale.coerceIn(0.9f, 1.12f)
        val scale = (uiScale * fontScale).coerceIn(0.84f, 0.98f)

        fun sp(value: Float): Float = value * context.resources.displayMetrics.scaledDensity * scale

        return when (size) {
            WidgetSize.CELL_2X1 -> WidgetTextSizes(
                dayPx = sp(0f), weekdayPx = sp(0f), lunarPx = sp(0f), groupLabelPx = sp(0f),
                titlePx = sp(15.5f), timePx = sp(13.5f), endTimePx = sp(13f)
            )
            WidgetSize.CELL_2X2 -> WidgetTextSizes(
                dayPx = sp(34f), weekdayPx = sp(14f), lunarPx = sp(0f), groupLabelPx = sp(11f),
                titlePx = sp(12.5f), timePx = sp(10.5f), endTimePx = sp(12.5f)
            )
            WidgetSize.CELL_4X2 -> WidgetTextSizes(
                dayPx = sp(44f), weekdayPx = sp(17f), lunarPx = sp(11f), groupLabelPx = sp(12f),
                titlePx = sp(14f), timePx = sp(12f), endTimePx = sp(12f)
            )
            WidgetSize.CELL_4X4 -> WidgetTextSizes(
                dayPx = sp(48f), weekdayPx = sp(18f), lunarPx = sp(0f), groupLabelPx = sp(13f),
                titlePx = sp(15f), timePx = sp(14.5f), endTimePx = sp(13.5f)
            )
        }
    }

    fun resolveColors(settings: MySettings, appearance: WidgetAppearanceConfig): WidgetColors {
        val dark = resolveDarkTheme(settings, appearance)
        val alpha = (appearance.backgroundAlpha.coerceIn(0.6f, 1f) * 255f).roundToInt()
        val schemeName = settings.themeColorScheme
        val colorScheme = if (ThemeColorScheme.fromName(schemeName) == ThemeColorScheme.CUSTOM) {
            ThemeColorGenerator.generateCustomColorScheme(parseThemeHexColor(settings.customThemeColorHex), dark)
        } else {
            ThemeColorGenerator.generateColorScheme(ThemeColorScheme.fromName(schemeName).primaryColor, dark, schemeName)
        }

        val background = if (schemeName == ThemeColorScheme.DEFAULT.name && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            dynamicSystemColor(if (dark) android.R.color.system_neutral1_900 else android.R.color.system_neutral1_10, colorScheme.background.toArgb())
        } else {
            colorScheme.background.toArgb()
        }
        return WidgetColors(
            background = withAlpha(background, alpha),
            card = withAlpha(colorScheme.surfaceContainerLow.toArgb(), if (dark) 210 else 225),
            primaryText = colorScheme.onBackground.toArgb(),
            secondaryText = colorScheme.onSurfaceVariant.toArgb(),
            primary = colorScheme.primary.toArgb()
        )
    }

    fun roundedBitmap(widthDp: Int, heightDp: Int, radiusDp: Int, color: Int): Bitmap {
        val widthPx = (widthDp.coerceAtLeast(1) * density).roundToInt().coerceAtLeast(1)
        val heightPx = (heightDp.coerceAtLeast(1) * density).roundToInt().coerceAtLeast(1)
        val radiusPx = radiusDp * density
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawRoundRect(0f, 0f, widthPx.toFloat(), heightPx.toFloat(), radiusPx, radiusPx, paint)
        return bitmap
    }

    fun solidBitmap(color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(color)
        return bitmap
    }

    fun openAppIntent(appWidgetId: Int, action: String = WidgetActions.ACTION_OPEN_HOME): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(WidgetActions.EXTRA_WIDGET_ACTION, action)
        }
        return PendingIntent.getActivity(
            context,
            41000 + appWidgetId + action.hashCode().let { it and 0x0FFF },
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun withAlpha(color: Int, alpha: Int): Int {
        return Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun resolveDarkTheme(settings: MySettings, appearance: WidgetAppearanceConfig): Boolean {
        return when (appearance.themeMode) {
            WidgetThemeMode.LIGHT -> false
            WidgetThemeMode.DARK -> true
            else -> when (settings.themeMode) {
                2 -> false
                3 -> true
                else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    private fun dynamicSystemColor(resId: Int, fallback: Int): Int {
        return runCatching { context.getColor(resId) }.getOrDefault(fallback)
    }
}
