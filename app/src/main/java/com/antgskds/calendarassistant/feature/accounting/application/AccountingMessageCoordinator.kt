package com.antgskds.calendarassistant.feature.accounting.application

import android.content.Context
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingMessage
import com.antgskds.calendarassistant.feature.recognition.application.ai.AnalysisResult
import com.antgskds.calendarassistant.platform.receiver.AccountingMessageAccessPolicy
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import com.antgskds.calendarassistant.shared.operation.RecognitionApi
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

/** 只负责投递与重复回调抑制；识别、去重入库、结果通知仍由 RecognitionApi 编排。 */
class AccountingMessageCoordinator(
    private val context: Context,
    private val notificationPriority: com.antgskds.calendarassistant.feature.accounting.domain.AccountingNotificationPriorityPolicy,
    private val recognition: () -> RecognitionApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Pair<AccountingMessage, String>>(ConfigCatalog.AUTO_ACCOUNTING_QUEUE_SIZE)
    private val mutex = Mutex()
    private val prefs = context.getSharedPreferences("accounting_message_receipts", Context.MODE_PRIVATE)

    init {
        scope.launch {
            for ((message, deliveryId) in queue) process(message, deliveryId)
        }
    }

    fun submit(message: AccountingMessage, deliveryId: String = "") {
        if (!AccountingMessageAccessPolicy.enabled(context)) return
        if (!queue.trySend(message to deliveryId).isSuccess) Log.w("AccountingMessage", "消息队列已满，本次跳过")
    }

    // 短信广播在 goAsync 生命周期中等待落库，通知和 Observer 走有界队列。
    suspend fun process(message: AccountingMessage, deliveryId: String = "") = mutex.withLock {
        if (!AccountingMessageAccessPolicy.enabled(context)) return@withLock
        if (message.body.length + message.title.length > ConfigCatalog.ACCOUNTING_MESSAGE_MAX_TEXT) return@withLock
        val key = UUID.nameUUIDFromBytes(
            "${message.kind}|$deliveryId|${message.receivedAt}|${message.body}".toByteArray(Charsets.UTF_8)
        ).toString()
        val receipts = runCatching {
            Json.decodeFromString<List<String>>(prefs.getString("processed", "[]")!!)
        }.getOrDefault(emptyList())
        if (key in receipts) return@withLock
        try {
            val result = recognition().analyzeAccountingMessage(message, context, "accounting-message:$key")
            if (result is AnalysisResult.Success) {
                if (AccountingMessageAccessPolicy.enabled(context))
                    notificationPriority.record(message, result.accountingResult, System.currentTimeMillis())
                prefs.edit().putString("processed", Json.encodeToString(
                    (receipts + key).takeLast(ConfigCatalog.ACCOUNTING_MESSAGE_RECEIPTS))).commit()
                Log.i("AccountingMessage", "本地规则处理完成 source=${message.kind}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("AccountingMessage", "本地规则处理失败", e)
        }
    }
}
