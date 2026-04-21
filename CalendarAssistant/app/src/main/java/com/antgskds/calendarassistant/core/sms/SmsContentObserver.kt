package com.antgskds.calendarassistant.core.sms

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import com.antgskds.calendarassistant.core.operation.IngestCommandApi
import com.antgskds.calendarassistant.data.source.SettingsDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
    private val getIngestCommandApi: () -> IngestCommandApi?
) : ContentObserver(Handler(Looper.getMainLooper())) {

    companion object {
        private const val TAG = "SmsContentObserver"
        private const val PREFS_NAME = "sms_observer"
        private const val KEY_LAST_PROCESSED_ID = "last_processed_id"
        private val SMS_URI: Uri = Telephony.Sms.CONTENT_URI

        // 记录上次处理到的短信 ID，避免重复处理（进程存活期间有效）
        @Volatile private var lastProcessedId: Long = -1L

        /** 500ms 时间戳拦截，合并同一条短信的多次 onChange */
        @Volatile private var lastOnChangeTs: Long = 0L
    }

    private val pollHandler = Handler(Looper.getMainLooper())
    private var polling = false
    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!polling) return
            scanNewMessages("poll")
            pollHandler.postDelayed(this, 4000L)
        }
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun register() {
        val contentResolver = context.contentResolver
        try {
            val persistedId = prefs.getLong(KEY_LAST_PROCESSED_ID, -1L)

            // 初始化 lastProcessedId：优先使用持久化值；首次运行回看最近 5 条，减少漏识别
            val cursor = contentResolver.query(
                SMS_URI,
                arrayOf(Telephony.Sms._ID),
                null, null,
                "${Telephony.Sms._ID} DESC"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val latest = it.getLong(0)
                    lastProcessedId = when {
                        persistedId >= 0L -> persistedId
                        latest > 5L -> latest - 5L
                        else -> 0L
                    }
                    Log.d(TAG, "[探针] 初始化 lastProcessedId=$lastProcessedId (latest=$latest, persisted=$persistedId)")
                }
            }

            contentResolver.registerContentObserver(SMS_URI, true, this)
            Log.d(TAG, "[探针] 短信 ContentObserver 已注册")

            scanNewMessages("register")
            startPolling()
        } catch (e: SecurityException) {
            Log.e(TAG, "[探针] 注册失败：缺少 READ_SMS 权限", e)
        } catch (e: Exception) {
            Log.e(TAG, "[探针] 注册异常", e)
        }
    }

    fun unregister() {
        try {
            stopPolling()
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

        scanNewMessages("onChange")
    }

    private fun startPolling() {
        if (polling) return
        polling = true
        pollHandler.postDelayed(pollRunnable, 4000L)
        Log.d(TAG, "[探针] 短信轮询已启动")
    }

    private fun stopPolling() {
        if (!polling) return
        polling = false
        pollHandler.removeCallbacks(pollRunnable)
        Log.d(TAG, "[探针] 短信轮询已停止")
    }

    private fun scanNewMessages(reason: String) {
        val contentResolver = context.contentResolver
        val ingestCommandApi = getIngestCommandApi() ?: return

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
        prefs.edit().putLong(KEY_LAST_PROCESSED_ID, lastProcessedId).apply()
        Log.d(TAG, "[探针] 发现 ${newMessages.size} 条新短信, latestId=$lastProcessedId, reason=$reason")

        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            for ((id, sender, body) in newMessages) {
                try {
                    processSms(ingestCommandApi, sender, body, id)
                } catch (e: Exception) {
                    Log.e(TAG, "[探针] 处理短信异常 id=$id", e)
                }
            }
        }
    }

    private suspend fun processSms(
        ingestCommandApi: IngestCommandApi,
        sender: String,
        body: String,
        smsId: Long
    ) {
        Log.d(TAG, "[探针] 处理短信 id=$smsId from=$sender, body=${body.take(80)}...")

        val eventData = SmsAnalysis.parse(sender, body)
        if (eventData == null) {
            Log.d(TAG, "[探针] SmsAnalysis.parse 返回 null，未识别到取件码")
            return
        }

        Log.d(TAG, "[探针] 解析成功: title=${eventData.title}, tag=${eventData.tag}, desc=${eventData.description}")

        val added = ingestCommandApi.ingestSmsPickup(eventData)
        if (added == null) {
            Log.d(TAG, "[探针] 重复取件码已跳过: ${eventData.title}")
            return
        }

        Log.d(TAG, "[探针] ✅ 取件码已入库: ${added.title} from $sender")
    }
}
