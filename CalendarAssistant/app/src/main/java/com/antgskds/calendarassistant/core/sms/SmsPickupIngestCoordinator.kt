package com.antgskds.calendarassistant.core.sms

import android.util.Log
import com.antgskds.calendarassistant.core.operation.IngestCommandApi
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
        cleanupTerminalFingerprints(now)

        Log.d(TAG, "[探针] 候选短信开始处理 source=${candidate.source.logName}, smsId=${candidate.smsId}, body=${candidate.body.take(80)}...")

        val eventData = SmsAnalysis.parse(candidate.sender, candidate.body)
        if (eventData == null) {
            Log.d(TAG, "[探针] 候选短信解析失败，允许后续入口继续尝试 source=${candidate.source.logName}, smsId=${candidate.smsId}")
            return
        }

        val fingerprint = SmsPickupFingerprint.fromDraft(eventData)
        if (fingerprint != null && terminalFingerprints.containsKey(fingerprint)) {
            Log.d(TAG, "[探针] 同取件码已由其他入口处理，跳过 source=${candidate.source.logName}, fingerprint=$fingerprint")
            return
        }

        val ingestCommandApi = getIngestCommandApi()
        if (ingestCommandApi == null) {
            Log.w(TAG, "[探针] 入库 API 未就绪，允许后续入口重试 source=${candidate.source.logName}")
            return
        }

        try {
            val added = ingestCommandApi.ingestSmsPickup(eventData)
            if (fingerprint != null) {
                terminalFingerprints[fingerprint] = System.currentTimeMillis()
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

    private companion object {
        private const val TAG = "SmsPickupCoordinator"
        private const val TERMINAL_TTL_MS = 10 * 60 * 1000L
        private const val MAX_TERMINAL_FINGERPRINTS = 128
    }
}
