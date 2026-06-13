package com.antgskds.calendarassistant.service.receiver

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import com.antgskds.calendarassistant.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class DailySummaryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DAILY_SUMMARY) return

        val type = intent.getIntExtra(EXTRA_TYPE, -1)
        val app = context.applicationContext as App

        // 保持 Receiver 存活以进行异步操作
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. 稍微延迟等待 Repository 初始化（以防 App 冷启动）
                Thread.sleep(500) // 简单且有效的实用主义做法

                val settings = app.settingsQueryApi.settings.value
                val isMorning = (type == TYPE_MORNING)
                val cachedWeather = app.weatherQueryApi.weatherData.value
                val payload = app.dailySummaryQueryApi.buildPayload(
                    isMorning = isMorning,
                    settings = settings,
                    events = app.scheduleCenter.events.value,
                    weatherData = cachedWeather
                ) ?: return@launch

                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    app.notificationCenter.showDailySummaryNotification(payload, isMorning)
                }

            } catch (e: Exception) {
                Log.e("DailySummary", "Error processing summary", e)
            } finally {
                scheduleAlarm(context, if (type == TYPE_MORNING) 6 else 22, 0, type)
                pendingResult.finish()
            }
        }
    }

    // --- 调度逻辑收敛在 Companion Object 中 ---
    companion object {
        private const val ACTION_DAILY_SUMMARY = "com.antgskds.calendarassistant.ACTION_DAILY_SUMMARY"
        private const val EXTRA_TYPE = "TYPE"
        private const val TYPE_MORNING = 0
        private const val TYPE_EVENING = 1

        /** 外部调用入口：设置每天 06:00 和 22:00 的单次闹钟。 */
        fun schedule(context: Context) {
            scheduleAlarm(context, 6, 0, TYPE_MORNING)  // 早报
            scheduleAlarm(context, 22, 0, TYPE_EVENING) // 晚报
        }

        private fun scheduleAlarm(context: Context, hour: Int, minute: Int, type: Int) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, DailySummaryReceiver::class.java).apply {
                action = ACTION_DAILY_SUMMARY
                putExtra(EXTRA_TYPE, type)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context, type, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }

            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            alarmManager.cancel(pendingIntent)

            try {
                setDailySummaryAlarm(alarmManager, calendar.timeInMillis, pendingIntent)
                Log.d("DailySummary", "Scheduled type $type for ${calendar.time}")
            } catch (e: Exception) {
                Log.e("DailySummary", "Schedule failed", e)
            }
        }

        private fun setDailySummaryAlarm(
            alarmManager: AlarmManager,
            triggerAtMillis: Long,
            pendingIntent: PendingIntent
        ) {
            try {
                when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                    }
                    else -> alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
                }
            } catch (e: SecurityException) {
                Log.w("DailySummary", "Exact alarm permission missing, falling back to inexact alarm", e)
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            } catch (e: IllegalStateException) {
                Log.w("DailySummary", "Exact alarm quota reached, falling back to inexact alarm", e)
                alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent)
            }
        }
    }
}
