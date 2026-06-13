package com.antgskds.calendarassistant.core.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.data.model.WeatherAlertData
import com.antgskds.calendarassistant.data.model.WeatherRiskAlert
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

class WeatherNotifier(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val app = appContext as? App

    fun notifyAlerts(
        locationName: String,
        alerts: List<WeatherAlertData>,
        risks: List<WeatherRiskAlert>,
        showLiveNotification: Boolean
    ) {
        if (!canNotify()) return

        alerts.forEach { alert ->
            val title = WeatherWarningText.officialTitle(alert)
            val content = alert.description.ifBlank { alert.instruction.ifBlank { alert.eventName } }
            notifyOfficialOnce(locationName, alert, title) {
                if (showLiveNotification) {
                    app?.capsuleCenter?.showWeatherAlert(locationName, alert)
                } else {
                    app?.notificationCenter?.showPlainNotification(
                        notificationId = OFFICIAL_BASE_ID + stableId(title),
                        title = title,
                        content = content.take(120),
                        channelId = App.CHANNEL_ID_WEATHER,
                        smallIcon = WeatherAlertIconMapper.officialIconRes(alert)
                    )
                }
            }
        }

        risks.forEach { risk ->
            val title = risk.title.ifBlank { "天气风险提醒" }
            val content = risk.message.ifBlank { risk.weatherText }
            notifyRiskOnce(locationName, risk, title) {
                if (showLiveNotification) {
                    app?.capsuleCenter?.showWeatherRisk(locationName, risk)
                } else {
                    app?.notificationCenter?.showPlainNotification(
                        notificationId = RISK_BASE_ID + stableId(title),
                        title = title,
                        content = content.take(120),
                        channelId = App.CHANNEL_ID_WEATHER,
                        smallIcon = WeatherAlertIconMapper.riskIconRes(risk)
                    )
                }
            }
        }
    }

    private fun notifyOfficialOnce(locationName: String, alert: WeatherAlertData, title: String, action: () -> Unit) {
        val key = "official:${locationName}:${title}"
        val fingerprint = listOf(
            title,
            alert.effectiveTime.ifBlank { alert.onsetTime },
            alert.expireTime.ifBlank { alert.issuedTime }
        ).joinToString("|")
        val previous = prefs.getString(key, null)
        if (previous == fingerprint && !isPast(alert.expireTime)) return
        action()
        prefs.edit().putString(key, fingerprint).apply()
    }

    private fun notifyRiskOnce(locationName: String, risk: WeatherRiskAlert, title: String, action: () -> Unit) {
        val key = "risk:${locationName}:${riskEventBucket(risk)}"
        val currentScore = severityScore(risk.level)
        val previousScore = prefs.getInt(key, 0)
        if (previousScore >= currentScore) return
        action()
        prefs.edit().putInt(key, currentScore).apply()
    }

    @Suppress("unused")
    private fun notifyOnce(key: String, action: () -> Unit) {
        val now = System.currentTimeMillis()
        val last = prefs.getLong(key, 0L)
        if (now - last < DEDUPE_WINDOW_MS) return
        action()
        prefs.edit().putLong(key, now).apply()
    }

    private fun canNotify(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun stableId(value: String): Int {
        return (value.hashCode() and 0x0FFFFFFF) % 10_000
    }

    private fun isPast(value: String): Boolean {
        if (value.isBlank()) return false
        return try {
            OffsetDateTime.parse(value).isBefore(OffsetDateTime.now())
        } catch (_: DateTimeParseException) {
            false
        }
    }

    private fun riskEventBucket(risk: WeatherRiskAlert): String {
        val parsed = runCatching { OffsetDateTime.parse(risk.fxTime) }.getOrNull()
        val bucket = if (parsed != null) {
            val hourBucket = (parsed.hour / 3) * 3
            parsed.toLocalDate().toString() + "T" + hourBucket.toString().padStart(2, '0')
        } else {
            risk.fxTime.ifBlank { "unknown" }
        }
        return "${riskCategory(risk)}:$bucket"
    }

    private fun riskCategory(risk: WeatherRiskAlert): String {
        val title = risk.title
        val text = risk.weatherText
        return when {
            title.contains("高温") || text.contains("高温") -> "heat"
            title.contains("低温") || title.contains("寒潮") || title.contains("强降温") || title.contains("寒冷") || title.contains("霜冻") || title.contains("冰冻") || text.contains("低温") || text.contains("寒潮") || text.contains("强降温") || text.contains("寒冷") || text.contains("霜冻") || text.contains("冰冻") -> "cold"
            title.contains("雷") || title.contains("强对流") || title.contains("冰雹") || title.contains("雹") -> "thunder"
            title.contains("雪") || title.contains("冻雨") || title.contains("结冰") -> "snow"
            title.contains("雨") || title.contains("降雨") -> "rain"
            title.contains("风") || title.contains("台风") -> "wind"
            title.contains("雾") || title.contains("霾") || title.contains("沙") || title.contains("尘") -> "visibility"
            else -> title
        }
    }

    private fun severityScore(level: String): Int {
        return when (level) {
            "high" -> 3
            "medium" -> 2
            else -> 1
        }
    }

    companion object {
        private const val PREFS_NAME = "weather_alert_dedupe"
        private const val DEDUPE_WINDOW_MS = 6L * 60L * 60L * 1000L
        private const val OFFICIAL_BASE_ID = 740_000
        private const val RISK_BASE_ID = 750_000
    }
}
