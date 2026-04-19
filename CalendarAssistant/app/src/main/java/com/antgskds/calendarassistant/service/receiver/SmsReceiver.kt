package com.antgskds.calendarassistant.service.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.util.Log
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.core.sms.SmsAnalysis
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.source.SettingsDataSource
import com.antgskds.calendarassistant.service.notification.NotificationScheduler
import com.antgskds.calendarassistant.ui.theme.EventColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 短信广播接收器
 *
 * 监听 SMS_RECEIVED，调用 SmsAnalysis 解析取件码，
 * 复用与 AI 识屏一致的 CalendarEventData → MyEvent 入库管线。
 */
class SmsReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SmsReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "android.provider.Telephony.SMS_RECEIVED") return

        Log.d(TAG, "====== [探针] 收到 SMS_RECEIVED 广播 ======")

        // 修复：直接从 SharedPreferences 同步读取设置，
        // 避免 App 冷启动时 StateFlow 尚未加载导致 isSmsMonitoringEnabled 读到默认 false
        val settings = SettingsDataSource(context).loadSettings()
        Log.d(TAG, "[探针] Settings 同步读取完成, isSmsMonitoringEnabled=${settings.isSmsMonitoringEnabled}")

        if (!settings.isSmsMonitoringEnabled) {
            Log.d(TAG, "[探针] 短信监控未开启，跳过")
            return
        }

        val messages = getSmsMessages(intent)
        Log.d(TAG, "[探针] 解析到 ${messages.size} 条短信 PDU")

        val app = context.applicationContext as App
        val repository = app.repository
        val pendingResult = goAsync()
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        scope.launch {
            try {
                for (msg in messages) {
                    val sender = msg.originatingAddress ?: run {
                        Log.w(TAG, "[探针] 短信 originatingAddress 为空，跳过")
                        continue
                    }
                    val body = msg.messageBody ?: run {
                        Log.w(TAG, "[探针] 短信 messageBody 为空，跳过")
                        continue
                    }

                    Log.d(TAG, "[探针] 收到短信 from=$sender, body=${body.take(80)}...")

                    // 解析 → CalendarEventData（与 AI 识屏输出格式一致）
                    val eventData = SmsAnalysis.parse(sender, body)
                    if (eventData == null) {
                        Log.d(TAG, "[探针] SmsAnalysis.parse 返回 null，未识别到取件码")
                        continue
                    }
                    Log.d(TAG, "[探针] 解析成功: title=${eventData.title}, tag=${eventData.tag}, desc=${eventData.description}")

                    // CalendarEventData → MyEvent（复用识屏逻辑）
                    val event = eventDataToMyEvent(eventData, repository.events.value.size)
                    Log.d(TAG, "[探针] 转换为 MyEvent: ${event.title}")

                    // 去重：同一天相同 description
                    val isDuplicate = repository.events.value.any { existing ->
                        existing.tag == eventData.tag &&
                                existing.description == eventData.description &&
                                !existing.endDate.isBefore(java.time.LocalDate.now())
                    }
                    if (isDuplicate) {
                        Log.d(TAG, "[探针] 重复取件码已跳过: ${eventData.title}")
                        continue
                    }

                    repository.addEvent(event)
                    NotificationScheduler.scheduleReminders(context, event)
                    Log.d(TAG, "[探针] ✅ 取件码已入库: ${eventData.title} from $sender")
                }
            } catch (e: Exception) {
                Log.e(TAG, "[探针] 短信处理异常", e)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun getSmsMessages(intent: Intent): Array<SmsMessage> {
        val bundle = intent.extras ?: return emptyArray()
        val pdus = bundle.get("pdus") as? Array<*> ?: return emptyArray()
        val format = bundle.getString("format") ?: ""

        return pdus.mapNotNull { pdu ->
            try {
                SmsMessage.createFromPdu(pdu as ByteArray, format)
            } catch (_: Exception) {
                null
            }
        }.toTypedArray()
    }
}

/**
 * CalendarEventData → MyEvent
 * 复用 TextAccessibilityService.saveEventsLocally 的转换逻辑
 */
private fun eventDataToMyEvent(eventData: com.antgskds.calendarassistant.data.model.CalendarEventData, currentEventsCount: Int): MyEvent {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    val now = LocalDateTime.now()
    val startDateTime = try {
        if (eventData.startTime.isNotBlank()) LocalDateTime.parse(eventData.startTime, formatter) else now
    } catch (_: Exception) { now }

    val endDateTime = try {
        if (eventData.endTime.isNotBlank()) LocalDateTime.parse(eventData.endTime, formatter) else startDateTime.plusHours(1)
    } catch (_: Exception) { startDateTime.plusHours(1) }

    return MyEvent(
        title = eventData.title.trim(),
        startDate = startDateTime.toLocalDate(),
        endDate = endDateTime.toLocalDate(),
        startTime = startDateTime.format(timeFormatter),
        endTime = endDateTime.format(timeFormatter),
        location = eventData.location,
        description = eventData.description,
        color = EventColors[currentEventsCount % EventColors.size],
        eventType = com.antgskds.calendarassistant.data.model.EventType.EVENT,
        tag = eventData.tag.ifBlank { com.antgskds.calendarassistant.data.model.EventTags.GENERAL }
    )
}
