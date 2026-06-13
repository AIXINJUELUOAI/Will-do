package com.antgskds.calendarassistant.core.center

import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.antgskds.calendarassistant.core.instantcode.InstantCodeCandidate
import com.antgskds.calendarassistant.core.instantcode.InstantCodeParseMode
import com.antgskds.calendarassistant.core.instantcode.InstantCodeParser
import com.antgskds.calendarassistant.core.operation.IngestCommandApi
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.core.sms.SmsPickupFingerprint
import com.antgskds.calendarassistant.core.util.PrivilegeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest

data class ClipboardCodePrompt(
    val candidate: InstantCodeCandidate,
    val fingerprint: String,
    val instanceKey: String
)

private data class ClipboardSnapshot(
    val text: String,
    val textHash: String,
    val instanceKey: String
)

private data class ClipboardInstance(
    val firstSeenElapsed: Long,
    var lastSeenElapsed: Long
)

class ClipboardCodeCenter(
    private val appContext: Context,
    private val settingsQueryApi: SettingsQueryApi,
    private val ingestCommandApi: IngestCommandApi,
    private val appScope: CoroutineScope
) {
    private val _pendingPrompt = MutableStateFlow<ClipboardCodePrompt?>(null)
    val pendingPrompt: StateFlow<ClipboardCodePrompt?> = _pendingPrompt.asStateFlow()

    private val stateMutex = Mutex()
    private val handledKeys = LinkedHashMap<String, Long>()
    private val ignoredKeys = LinkedHashMap<String, Long>()
    private val failedKeys = LinkedHashMap<String, Long>()
    private val contentInstances = LinkedHashMap<String, ClipboardInstance>()
    private val processingKeys = mutableSetOf<String>()
    private var lastSeenPromptTextHash: String? = null
    private var lastPromptedTextHash: String? = null
    @Volatile private var privilegedMonitorActive = false

    fun checkClipboardForPrompt(source: String) {
        appScope.launch {
            checkClipboardForPromptInternal(source)
        }
    }

    fun setPrivilegedMonitorActive(active: Boolean) {
        privilegedMonitorActive = active
        Log.d(TAG, "Privileged clipboard monitor active=$active")
    }

    fun confirmPendingPrompt() {
        val prompt = _pendingPrompt.value ?: return
        _pendingPrompt.value = null
        appScope.launch {
            val draft = InstantCodeParser.toDraft(prompt.candidate)
            val added = runCatching { ingestCommandApi.ingestInstantCode(draft, "clipboard_confirm") }
                .onFailure { Log.w(TAG, "Clipboard code confirm ingest failed", it) }
                .getOrNull()
            if (added != null) {
                markHandled(listOf(prompt.instanceKey, fingerprintKey(prompt.fingerprint)))
            } else {
                markFailed(prompt.instanceKey)
            }
        }
    }

    fun dismissPendingPrompt() {
        val prompt = _pendingPrompt.value ?: return
        _pendingPrompt.value = null
        appScope.launch {
            stateMutex.withLock {
                val now = SystemClock.elapsedRealtime()
                ignoredKeys[prompt.instanceKey] = now
                cleanupLocked(now)
            }
        }
    }

    suspend fun autoIngestCurrentClipboard(source: String): Boolean {
        val snapshot = readClipboardSnapshot(source) ?: return false
        return autoIngestSnapshot(snapshot, source)
    }

    suspend fun autoIngestText(text: String, source: String): Boolean {
        val snapshot = createSnapshot(text, source) ?: return false
        return autoIngestSnapshot(snapshot, source)
    }

    private suspend fun checkClipboardForPromptInternal(source: String) {
        if (!settingsQueryApi.settings.value.clipboardCodeRecognitionEnabled) {
            Log.d(TAG, "Skip clipboard prompt from $source: setting disabled")
            return
        }
        if (PrivilegeManager.hasPrivilege && privilegedMonitorActive) {
            Log.d(TAG, "Skip clipboard prompt from $source: privileged monitor active")
            return
        }
        val snapshot = readClipboardSnapshot(source) ?: return
        if (shouldSkipRepeatedPrompt(snapshot, source)) return
        val candidate = InstantCodeParser.parseClipboard(snapshot.text, InstantCodeParseMode.CLIPBOARD_CONFIRM)
        if (candidate == null) {
            Log.d(TAG, "Skip clipboard prompt from $source: no instant code matched")
            return
        }
        val shouldProcess = beginProcessing(snapshot.instanceKey, source) ?: return
        if (!shouldProcess) return
        try {
            val draft = InstantCodeParser.toDraft(candidate)
            val fingerprint = SmsPickupFingerprint.fromDraft(draft) ?: fingerprintOf(candidate.code)
            Log.d(TAG, "Clipboard code prompt from $source: ${candidate.type.displayLabel} ${candidate.code}")
            markPrompted(snapshot.textHash)
            _pendingPrompt.value = ClipboardCodePrompt(candidate, fingerprint, snapshot.instanceKey)
        } finally {
            endProcessing(snapshot.instanceKey)
        }
    }

    private suspend fun autoIngestSnapshot(snapshot: ClipboardSnapshot, source: String): Boolean {
        if (!settingsQueryApi.settings.value.clipboardCodeRecognitionEnabled) {
            Log.d(TAG, "Skip clipboard auto ingest from $source: setting disabled")
            return false
        }
        val text = snapshot.text
        val candidate = InstantCodeParser.parseClipboard(text, InstantCodeParseMode.CLIPBOARD_AUTO) ?: return false
        val draft = InstantCodeParser.toDraft(candidate)
        val fingerprint = SmsPickupFingerprint.fromDraft(draft) ?: fingerprintOf(candidate.code)
        val processingKeys = listOf(snapshot.instanceKey, fingerprintKey(fingerprint))
        val shouldProcess = beginProcessing(processingKeys, source) ?: return false
        if (!shouldProcess) return false
        return try {
            val added = runCatching { ingestCommandApi.ingestInstantCode(draft, source) }
                .onFailure { Log.w(TAG, "Clipboard code auto ingest failed", it) }
                .getOrNull()
            if (added != null) {
                markHandled(processingKeys)
                Log.d(TAG, "Clipboard code auto ingested: ${candidate.type.displayLabel} ${candidate.code}")
                true
            } else {
                markFailed(snapshot.instanceKey)
                Log.d(TAG, "Clipboard code auto ingest returned null: ${candidate.type.displayLabel} ${candidate.code}")
                false
            }
        } finally {
            endProcessing(processingKeys)
        }
    }

    private suspend fun readClipboardSnapshot(source: String): ClipboardSnapshot? {
        val text = readClipboardText(source) ?: return null
        return createSnapshot(text, source)
    }

    private suspend fun createSnapshot(text: String, source: String): ClipboardSnapshot? {
        val hash = hashText(text)
        val now = SystemClock.elapsedRealtime()
        val instanceKey = stateMutex.withLock {
            cleanupLocked(now)
            val existing = contentInstances[hash]
            val instance = if (existing != null && now - existing.lastSeenElapsed <= INSTANCE_REUSE_MS) {
                existing.lastSeenElapsed = now
                existing
            } else {
                ClipboardInstance(firstSeenElapsed = now, lastSeenElapsed = now).also {
                    contentInstances[hash] = it
                }
            }
            "$hash:${instance.firstSeenElapsed}"
        }
        Log.d(TAG, "Clipboard snapshot from $source: hash=${hash.take(8)}")
        return ClipboardSnapshot(text = text, textHash = hash, instanceKey = instanceKey)
    }

    private suspend fun shouldSkipRepeatedPrompt(snapshot: ClipboardSnapshot, source: String): Boolean =
        stateMutex.withLock {
            if (snapshot.textHash != lastSeenPromptTextHash) {
                lastSeenPromptTextHash = snapshot.textHash
                lastPromptedTextHash = null
                return@withLock false
            }
            if (snapshot.textHash == lastPromptedTextHash) {
                Log.d(TAG, "Skip clipboard prompt from $source: unchanged content already prompted")
                true
            } else {
                false
            }
        }

    private suspend fun markPrompted(textHash: String) {
        stateMutex.withLock {
            lastPromptedTextHash = textHash
        }
    }

    private suspend fun readClipboardText(source: String): String? = withContext(Dispatchers.Main) {
        runCatching {
            val clipboard = appContext.getSystemService(ClipboardManager::class.java) ?: return@runCatching null
            val clip = clipboard.primaryClip ?: return@runCatching null
            if (clip.itemCount <= 0) return@runCatching null
            clip.getItemAt(0).coerceToText(appContext)?.toString()?.trim()?.takeIf { it.isNotBlank() }
        }.onFailure { Log.w(TAG, "Read clipboard failed", it) }.getOrNull()
            .also { if (it == null) Log.d(TAG, "Skip clipboard from $source: empty or unreadable") }
    }

    private suspend fun beginProcessing(key: String, source: String): Boolean? = stateMutex.withLock {
        beginProcessingLocked(listOf(key), source)
    }

    private suspend fun beginProcessing(keys: List<String>, source: String): Boolean? = stateMutex.withLock {
        beginProcessingLocked(keys, source)
    }

    private fun beginProcessingLocked(keys: List<String>, source: String): Boolean? {
        val now = SystemClock.elapsedRealtime()
        cleanupLocked(now)
        return when {
            keys.any { processingKeys.contains(it) } -> {
                Log.d(TAG, "Skip clipboard from $source: already processing")
                false
            }
            keys.any { handledKeys.containsKey(it) } -> {
                Log.d(TAG, "Skip clipboard from $source: already handled")
                null
            }
            keys.any { ignoredKeys.containsKey(it) } -> {
                Log.d(TAG, "Skip clipboard from $source: ignored")
                null
            }
            keys.any { failedKeys.containsKey(it) } -> {
                Log.d(TAG, "Skip clipboard from $source: in failed cooldown")
                null
            }
            else -> {
                processingKeys.addAll(keys)
                true
            }
        }
    }

    private suspend fun endProcessing(key: String) {
        stateMutex.withLock { processingKeys.remove(key) }
    }

    private suspend fun endProcessing(keys: List<String>) {
        stateMutex.withLock { processingKeys.removeAll(keys.toSet()) }
    }

    private suspend fun markHandled(keys: List<String>) {
        stateMutex.withLock {
            val now = SystemClock.elapsedRealtime()
            keys.forEach { key ->
                handledKeys[key] = now
                failedKeys.remove(key)
            }
            cleanupLocked(now)
        }
    }

    private suspend fun markFailed(key: String) {
        stateMutex.withLock {
            val now = SystemClock.elapsedRealtime()
            failedKeys[key] = now
            cleanupLocked(now)
        }
    }

    private fun cleanupLocked(now: Long) {
        cleanupMap(handledKeys, now, ENTRY_TTL_MS)
        cleanupMap(ignoredKeys, now, ENTRY_TTL_MS)
        cleanupMap(failedKeys, now, FAILED_TTL_MS)
        val instanceIterator = contentInstances.entries.iterator()
        while (instanceIterator.hasNext()) {
            if (now - instanceIterator.next().value.lastSeenElapsed > INSTANCE_TTL_MS) instanceIterator.remove()
        }
        while (contentInstances.size > MAX_ENTRIES) {
            val eldest = contentInstances.entries.iterator()
            if (!eldest.hasNext()) return
            eldest.next()
            eldest.remove()
        }
    }

    private fun cleanupMap(map: LinkedHashMap<String, Long>, now: Long, ttl: Long) {
        val iterator = map.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value > ttl) iterator.remove()
        }
        while (map.size > MAX_ENTRIES) {
            val eldest = map.entries.iterator()
            if (!eldest.hasNext()) return
            eldest.next()
            eldest.remove()
        }
    }

    private fun hashText(text: String): String = fingerprintOf(text.trim())

    private fun fingerprintKey(value: String): String = "fingerprint:$value"

    private fun fingerprintOf(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        private const val TAG = "ClipboardCodeCenter"
        private const val ENTRY_TTL_MS = 10 * 60 * 1000L
        private const val FAILED_TTL_MS = 15 * 1000L
        private const val INSTANCE_REUSE_MS = 5 * 1000L
        private const val INSTANCE_TTL_MS = 10 * 60 * 1000L
        private const val MAX_ENTRIES = 128
    }
}
