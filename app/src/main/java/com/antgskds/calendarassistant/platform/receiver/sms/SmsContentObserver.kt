package com.antgskds.calendarassistant.platform.receiver.sms

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import com.antgskds.calendarassistant.feature.settings.data.SettingsDataSource
import com.antgskds.calendarassistant.feature.recognition.ingest.sms.SmsPickupIngestCoordinator
import com.antgskds.calendarassistant.feature.recognition.ingest.sms.SmsPickupSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 短信 ContentObserver
 *
 * 监听 content://sms/inbox 数据库变化，新短信到来时自动查询并用 SmsAnalysis 解析取件码。
 * 优势：不需要 NotificationListenerService 额外权限，仅需 READ_SMS（已具备）。
 *
 * 对齐 parcel 项目方案：广播 + ContentObserver 双通道。
 */
class SmsContentObserver(
    private val context: Context,
    private val getSmsPickupIngestCoordinator: () -> SmsPickupIngestCoordinator?
) : ContentObserver(Handler(Looper.getMainLooper())) {

    companion object {
        private const val TAG = "SmsContentObserver"
        private const val PREFS_NAME = "sms_observer"
        private const val KEY_LAST_PROCESSED_ID = "last_processed_id"
        private val SMS_URI: Uri = Telephony.Sms.Inbox.CONTENT_URI
        private const val RECOVERY_LOOKBACK_COUNT = 5
        private const val RECOVERY_LOOKBACK_WINDOW_MS = 24 * 60 * 60 * 1000L

        // 记录上次处理到的短信 ID，避免重复处理（进程存活期间有效）
        @Volatile private var lastProcessedId: Long = -1L

        /** 500ms 时间戳拦截，合并同一条短信的多次 onChange */
        @Volatile private var lastOnChangeTs: Long = 0L
    }

    private val pollHandler = Handler(Looper.getMainLooper())
    private var accountingBaseline = System.currentTimeMillis()
    private var registered = false
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

    @Synchronized
    fun register() {
        if (registered) return
        accountingBaseline = System.currentTimeMillis()
        val contentResolver = context.contentResolver
        try {
            val persistedId = prefs.getLong(KEY_LAST_PROCESSED_ID, -1L)

            // 每次重新注册都回看最近几条。入库层会去重，这可以恢复此前已推进游标但未成功入库的短信。
            val cursor = contentResolver.query(
                SMS_URI,
                arrayOf(Telephony.Sms._ID, Telephony.Sms.DATE),
                null, null,
                "${Telephony.Sms._ID} DESC"
            )
            cursor?.use {
                if (it.moveToFirst()) {
                    val latest = it.getLong(0)
                    val recoveryCutoff = System.currentTimeMillis() - RECOVERY_LOOKBACK_WINDOW_MS
                    var recoveryBaseline = latest
                    var inspected = 0
                    if (it.getLong(1) >= recoveryCutoff) {
                        var oldestRecoveryId = latest
                        inspected = 1
                        while (inspected < RECOVERY_LOOKBACK_COUNT && it.moveToNext()) {
                            if (it.getLong(1) < recoveryCutoff) break
                            oldestRecoveryId = it.getLong(0)
                            inspected++
                        }
                        recoveryBaseline = (oldestRecoveryId - 1L).coerceAtLeast(0L)
                    }
                    lastProcessedId = if (persistedId >= 0L) {
                        minOf(persistedId, recoveryBaseline)
                    } else {
                        recoveryBaseline
                    }
                    Log.d(
                        TAG,
                        "[探针] 初始化 lastProcessedId=$lastProcessedId " +
                            "(latest=$latest, persisted=$persistedId, recoveryCount=$inspected)"
                    )
                }
            }

            contentResolver.registerContentObserver(SMS_URI, true, this)
            registered = true
            Log.d(TAG, "[探针] 短信 ContentObserver 已注册")

            scanNewMessages("register")
            startPolling()
        } catch (e: SecurityException) {
            Log.e(TAG, "[探针] 注册失败：缺少 READ_SMS 权限", e)
        } catch (e: Exception) {
            Log.e(TAG, "[探针] 注册异常", e)
        }
    }

    @Synchronized
    fun unregister() {
        stopPolling()
        if (!registered) return
        try {
            context.contentResolver.unregisterContentObserver(this)
            registered = false
            Log.d(TAG, "[探针] 短信 ContentObserver 已注销")
        } catch (_: Exception) {
            registered = false
        }
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
        val ingestCoordinator = getSmsPickupIngestCoordinator() ?: return

        val settings = SettingsDataSource(context).loadSettings()
        val accountingEnabled = com.antgskds.calendarassistant.platform.receiver.AccountingMessageAccessPolicy.enabled(context, settings)
        if (!settings.isSmsMonitoringEnabled && !accountingEnabled) return

        // 查询比 lastProcessedId 更新的短信
        val cursor = contentResolver.query(
            SMS_URI,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
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
                val receivedAt = it.getLong(3)
                if (accountingEnabled && receivedAt >= accountingBaseline) {
                    (context.applicationContext as? com.antgskds.calendarassistant.App)?.accountingMessageCoordinator?.submit(
                        com.antgskds.calendarassistant.feature.accounting.domain.AccountingMessage(
                            com.antgskds.calendarassistant.feature.accounting.domain.AccountingMessageKind.SMS,
                            address, body, receivedAt))
                }
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
                    if (settings.isSmsMonitoringEnabled) processSms(ingestCoordinator, sender, body, id)
                } catch (e: Exception) {
                    Log.e(TAG, "[探针] 处理短信异常 id=$id", e)
                }
            }
        }
    }

    private fun processSms(
        ingestCoordinator: SmsPickupIngestCoordinator,
        sender: String,
        body: String,
        smsId: Long
    ) {
        Log.d(TAG, "[探针] 提交短信候选 id=$smsId, bodyLength=${body.length}")
        ingestCoordinator.submit(
            source = SmsPickupSource.CONTENT_OBSERVER,
            sender = sender,
            body = body,
            smsId = smsId
        )
    }
}
