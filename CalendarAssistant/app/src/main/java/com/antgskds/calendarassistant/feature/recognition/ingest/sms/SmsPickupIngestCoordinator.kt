package com.antgskds.calendarassistant.feature.recognition.ingest.sms

import android.util.Log
import com.antgskds.calendarassistant.shared.operation.IngestCommandApi
import com.antgskds.calendarassistant.shared.query.SettingsQueryApi
import com.antgskds.calendarassistant.feature.recognition.ingest.pickup.SmsPickupFingerprint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class SmsPickupSource(
    val logName: String,
    val enqueueDelayMs: Long
) {
    SMS_RECEIVER("sms_receiver", 0L),
    CONTENT_OBSERVER("content_observer", 600L),
    NOTIFICATION_LISTENER("notification_listener", 1500L)
}

class SmsPickupIngestCoordinator(
    private val appScope: CoroutineScope,
    private val settingsQueryApi: SettingsQueryApi,
    private val getIngestCommandApi: () -> IngestCommandApi?
) {
    private data class Candidate(
        val source: SmsPickupSource,
        val sender: String,
        val body: String,
        val smsId: Long?
    )

    private val candidates = Channel<Candidate>(capacity = 64)
    private val terminalFingerprints = LinkedHashMap<String, Long>()
    private data class DeliveryRecord(val source: SmsPickupSource, val timestamp: Long)
    private val recentDeliveryBodies = LinkedHashMap<String, DeliveryRecord>()

    init {
        appScope.launch {
            for (candidate in candidates) {
                processCandidate(candidate)
            }
        }
    }

    fun submit(source: SmsPickupSource, sender: String, body: String, smsId: Long? = null) {
        if (body.isBlank()) return
        appScope.launch {
            if (source.enqueueDelayMs > 0L) {
                delay(source.enqueueDelayMs)
            }
            candidates.send(
                Candidate(
                    source = source,
                    sender = sender,
                    body = body,
                    smsId = smsId
                )
            )
        }
    }

    private suspend fun processCandidate(candidate: Candidate) {
        val now = System.currentTimeMillis()
        val dedupEnabled = settingsQueryApi.settings.value.smsPickupDedupEnabled
        if (dedupEnabled) {
            cleanupTerminalFingerprints(now)
        } else {
            cleanupRecentDeliveries(now)
        }

        Log.d(TAG, "[探针] 候选短信开始处理 source=${candidate.source.logName}, smsId=${candidate.smsId}, body=${candidate.body.take(80)}...")

        val eventData = SmsAnalysis.parse(candidate.sender, candidate.body)
        if (eventData == null) {
            Log.d(TAG, "[探针] 候选短信解析失败，允许后续入口继续尝试 source=${candidate.source.logName}, smsId=${candidate.smsId}")
            return
        }

        val fingerprint = SmsPickupFingerprint.fromDraft(eventData)
        if (dedupEnabled && fingerprint != null && terminalFingerprints.containsKey(fingerprint)) {
            Log.d(TAG, "[探针] 同取件码已由其他入口处理，跳过 source=${candidate.source.logName}, fingerprint=$fingerprint")
            return
        }
        val deliveryKey = candidate.body.trim().replace(Regex("\\s+"), " ")
        val previousDelivery = recentDeliveryBodies[deliveryKey]
        if (!dedupEnabled &&
            previousDelivery != null &&
            previousDelivery.source != candidate.source &&
            now - previousDelivery.timestamp <= DELIVERY_DEBOUNCE_MS
        ) {
            Log.d(TAG, "[探针] 同一短信已由其他入口提交，跳过入口抖动 source=${candidate.source.logName}")
            return
        }

        val ingestCommandApi = getIngestCommandApi()
        if (ingestCommandApi == null) {
            Log.w(TAG, "[探针] 入库 API 未就绪，允许后续入口重试 source=${candidate.source.logName}")
            return
        }

        try {
            val added = ingestCommandApi.ingestSmsPickup(eventData)
            if (dedupEnabled && fingerprint != null) {
                terminalFingerprints[fingerprint] = System.currentTimeMillis()
            } else if (!dedupEnabled && added != null) {
                recentDeliveryBodies[deliveryKey] = DeliveryRecord(
                    source = candidate.source,
                    timestamp = System.currentTimeMillis()
                )
            }

            if (added == null) {
                Log.d(TAG, "[探针] 取件码已存在或被最终去重拦截 source=${candidate.source.logName}, title=${eventData.title}")
            } else {
                Log.d(TAG, "[探针] ✅ 取件码已入库 source=${candidate.source.logName}, title=${added.title}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "[探针] 当前入口入库失败，允许后续入口继续尝试 source=${candidate.source.logName}", e)
        }
    }

    private fun cleanupTerminalFingerprints(now: Long) {
        val iterator = terminalFingerprints.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value > TERMINAL_TTL_MS) {
                iterator.remove()
            }
        }
        while (terminalFingerprints.size > MAX_TERMINAL_FINGERPRINTS) {
            val eldest = terminalFingerprints.entries.iterator()
            if (!eldest.hasNext()) return
            eldest.next()
            eldest.remove()
        }
    }

    private fun cleanupRecentDeliveries(now: Long) {
        val iterator = recentDeliveryBodies.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value.timestamp > DELIVERY_DEBOUNCE_MS) {
                iterator.remove()
            }
        }
    }

    private companion object {
        private const val TAG = "SmsPickupCoordinator"
        private const val TERMINAL_TTL_MS = 10 * 60 * 1000L
        private const val MAX_TERMINAL_FINGERPRINTS = 128
        private const val DELIVERY_DEBOUNCE_MS = 5_000L
    }
}
