package com.antgskds.calendarassistant.core.sms

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import com.antgskds.calendarassistant.data.model.CalendarEventData
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
 * 短信 ContentObserver
 *
 * 监听 content://sms 数据库变化，新短信到来时自动查询并用 SmsAnalysis 解析取件码。
 * 优势：不需要 NotificationListenerService 额外权限，仅需 READ_SMS（已具备）。
 *
 * 对齐 parcel 项目方案：广播 + ContentObserver 双通道。
 */
class SmsContentObserver(
    private val context: Context,
    private val getRepository: () -> com.antgskds.calendarassistant.data.repository.AppRepository?
) : ContentObserver(Handler(Looper.getMainLooper())) {

    companion object {
        private const val TAG = "SmsContentObserver"
        private val SMS_URI: Uri = Telephony.Sms.CONTENT_URI

        // 记录上次处理到的短信 ID，避免重复处理（进程存活期间有效）
        @Volatile private var lastProcessedId: Long = -1L

        /** 500ms 时间戳拦截，合并同一条短信的多次 onChange */
        @Volatile private var lastOnChangeTs: Long = 0L
    }

    fun register() {
        val contentResolver = context.contentResolver
        try {
            // 初始化 lastProcessedId 为当前最新短信 ID
            val cursor = contentResolver.query(
                SMS_URI,
                arrayOf(Telephony.Sms._ID),
                null, null,
                "${Telephony.Sms._ID} DESC"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    lastProcessedId = it.getLong(0)
                    Log.d(TAG, "[探针] 初始化 lastProcessedId=$lastProcessedId")
                }
            }

            contentResolver.registerContentObserver(SMS_URI, true, this)
            Log.d(TAG, "[探针] 短信 ContentObserver 已注册")
        } catch (e: SecurityException) {
            Log.e(TAG, "[探针] 注册失败：缺少 READ_SMS 权限", e)
        } catch (e: Exception) {
            Log.e(TAG, "[探针] 注册异常", e)
        }
    }

    fun unregister() {
        try {
            context.contentResolver.unregisterContentObserver(this)
            Log.d(TAG, "[探针] 短信 ContentObserver 已注销")
        } catch (_: Exception) {}
    }

    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)

        // 500ms 时间戳拦截，合并同一条短信的多次 onChange
        val now = System.currentTimeMillis()
        if (now - lastOnChangeTs < 500L) return
        lastOnChangeTs = now

        Log.d(TAG, "[探针] 检测到短信数据库变化")

        val contentResolver = context.contentResolver
        val repository = getRepository() ?: return

        val settings = SettingsDataSource(context).loadSettings()
        if (!settings.isSmsMonitoringEnabled) return

        // 查询比 lastProcessedId 更新的短信
        val cursor = contentResolver.query(
            SMS_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY),
            "${Telephony.Sms._ID} > ?",
            arrayOf(lastProcessedId.toString()),
            "${Telephony.Sms._ID} ASC"
        ) ?: return

        val newMessages = mutableListOf<Triple<Long, String, String>>()
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val address = it.getString(1) ?: continue
                val body = it.getString(2) ?: continue
                newMessages.add(Triple(id, address, body))
            }
        }

        if (newMessages.isEmpty()) return

        // 更新 lastProcessedId
        lastProcessedId = newMessages.last().first
        Log.d(TAG, "[探针] 发现 ${newMessages.size} 条新短信, latestId=$lastProcessedId")

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            for ((id, sender, body) in newMessages) {
                try {
                    processSms(repository, sender, body, id)
                } catch (e: Exception) {
                    Log.e(TAG, "[探针] 处理短信异常 id=$id", e)
                }
            }
        }
    }

    private suspend fun processSms(
        repository: com.antgskds.calendarassistant.data.repository.AppRepository,
        sender: String,
        body: String,
        smsId: Long
    ) {
        // 仅处理 106 开头的号码
        if (!sender.startsWith("106")) {
            Log.d(TAG, "[探针] 跳过非106号码: $sender")
            return
        }

        Log.d(TAG, "[探针] 处理短信 id=$smsId from=$sender, body=${body.take(80)}...")

        val eventData = SmsAnalysis.parse(sender, body)
        if (eventData == null) {
            Log.d(TAG, "[探针] SmsAnalysis.parse 返回 null，未识别到取件码")
            return
        }

        Log.d(TAG, "[探针] 解析成功: title=${eventData.title}, tag=${eventData.tag}, desc=${eventData.description}")

        val event = eventDataToMyEvent(eventData, repository.events.value.size)

        // 去重
        val isDuplicate = repository.events.value.any { existing ->
            existing.tag == eventData.tag &&
                    existing.description == eventData.description &&
                    !existing.endDate.isBefore(java.time.LocalDate.now())
        }
        if (isDuplicate) {
            Log.d(TAG, "[探针] 重复取件码已跳过: ${eventData.title}")
            return
        }

        repository.addEvent(event)
        NotificationScheduler.scheduleReminders(context, event)
        Log.d(TAG, "[探针] ✅ 取件码已入库: ${eventData.title} from $sender")
    }
}

private fun eventDataToMyEvent(
    eventData: CalendarEventData,
    currentEventsCount: Int
): MyEvent {
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
