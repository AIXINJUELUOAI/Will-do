package com.antgskds.calendarassistant.service.receiver

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.data.repository.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.Calendar

class DailySummaryReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DAILY_SUMMARY) return

        val type = intent.getIntExtra(EXTRA_TYPE, -1)
        val repository = AppRepository.getInstance(context)

        // 保持 Receiver 存活以进行异步操作
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. 稍微延迟等待 Repository 初始化（以防 App 冷启动）
                Thread.sleep(500) // 简单且有效的实用主义做法

                val settings = repository.settings.value
                if (!settings.isDailySummaryEnabled) return@launch

                // 2. 确定日期和文案
                val isMorning = (type == TYPE_MORNING)
                val targetDate = if (isMorning) LocalDate.now() else LocalDate.now().plusDays(1)

                // 3. 筛选日程 (Repository 的 events 是 Hot Flow，通常已有缓存)
                val events = repository.events.value.filter { it.startDate == targetDate }

                if (events.isEmpty()) return@launch

                // 4. 构建文案
                val title = if (isMorning) "今日日程提醒" else "明日日程预告"
                val content = "您有 ${events.size} 个日程：${events.joinToString("，") { it.title }}"

                // 5. 发送通知
                sendNotification(context, title, content, type)

            } catch (e: Exception) {
                Log.e("DailySummary", "Error processing summary", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun sendNotification(context: Context, title: String, content: String, idOffset: Int) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, App.CHANNEL_ID_POPUP)
            .setSmallIcon(R.drawable.ic_notification_small)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        manager.notify(8000 + idOffset, builder.build())
    }

    // --- 调度逻辑收敛在 Companion Object 中 ---
    companion object {
        private const val ACTION_DAILY_SUMMARY = "com.antgskds.calendarassistant.ACTION_DAILY_SUMMARY"
        private const val EXTRA_TYPE = "TYPE"
        private const val TYPE_MORNING = 0
        private const val TYPE_EVENING = 1

        /**
         * 外部调用入口：设置每天 06:00 和 22:00 的闹钟
         */
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

            // 如果时间已过，推迟到明天
            if (calendar.timeInMillis <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            }

            try {
                alarmManager.setInexactRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
                )
                Log.d("DailySummary", "Scheduled type $type for ${calendar.time}")
            } catch (e: Exception) {
                Log.e("DailySummary", "Schedule failed", e)
            }
        }
    }
}