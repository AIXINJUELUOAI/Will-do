package com.antgskds.calendarassistant.core.center

import android.content.ClipboardManager
import android.content.Context
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
import kotlinx.coroutines.withContext
import java.security.MessageDigest

data class ClipboardCodePrompt(
    val candidate: InstantCodeCandidate,
    val fingerprint: String,
    val contentHash: String
)

class ClipboardCodeCenter(
    private val appContext: Context,
    private val settingsQueryApi: SettingsQueryApi,
    private val ingestCommandApi: IngestCommandApi,
    private val appScope: CoroutineScope
) {
    private val _pendingPrompt = MutableStateFlow<ClipboardCodePrompt?>(null)
    val pendingPrompt: StateFlow<ClipboardCodePrompt?> = _pendingPrompt.asStateFlow()

    private val handledHashes = LinkedHashMap<String, Long>()
    private val ignoredHashes = LinkedHashMap<String, Long>()

    fun checkClipboardForPrompt(source: String) {
        if (!settingsQueryApi.settings.value.clipboardCodeRecognitionEnabled) return
        if (PrivilegeManager.hasPrivilege) return
        appScope.launch {
            val text = readClipboardText() ?: return@launch
            val candidate = InstantCodeParser.parseClipboard(text, InstantCodeParseMode.CLIPBOARD_CONFIRM) ?: return@launch
            val draft = InstantCodeParser.toDraft(candidate)
            val fingerprint = SmsPickupFingerprint.fromDraft(draft) ?: fingerprintOf(candidate.code)
            val hash = hashText(text)
            val now = System.currentTimeMillis()
            cleanup(now)
            if (handledHashes.containsKey(hash) || ignoredHashes.containsKey(hash)) return@launch
            Log.d(TAG, "Clipboard code prompt from $source: ${candidate.type.displayLabel} ${candidate.code}")
            _pendingPrompt.value = ClipboardCodePrompt(candidate, fingerprint, hash)
        }
    }

    fun confirmPendingPrompt() {
        val prompt = _pendingPrompt.value ?: return
        _pendingPrompt.value = null
        appScope.launch {
            val draft = InstantCodeParser.toDraft(prompt.candidate)
            runCatching { ingestCommandApi.ingestInstantCode(draft, "clipboard_confirm") }
                .onFailure { Log.w(TAG, "Clipboard code confirm ingest failed", it) }
            markHandled(prompt.contentHash)
        }
    }

    fun dismissPendingPrompt() {
        val prompt = _pendingPrompt.value ?: return
        _pendingPrompt.value = null
        ignoredHashes[prompt.contentHash] = System.currentTimeMillis()
        cleanup(System.currentTimeMillis())
    }

    suspend fun autoIngestCurrentClipboard(source: String): Boolean {
        if (!settingsQueryApi.settings.value.clipboardCodeRecognitionEnabled) return false
        val text = readClipboardText() ?: return false
        return autoIngestText(text, source)
    }

    suspend fun autoIngestText(text: String, source: String): Boolean {
        if (!settingsQueryApi.settings.value.clipboardCodeRecognitionEnabled) return false
        val candidate = InstantCodeParser.parseClipboard(text, InstantCodeParseMode.CLIPBOARD_AUTO) ?: return false
        val draft = InstantCodeParser.toDraft(candidate)
        val hash = hashText(text)
        val fingerprint = SmsPickupFingerprint.fromDraft(draft) ?: fingerprintOf(candidate.code)
        val now = System.currentTimeMillis()
        cleanup(now)
        if (handledHashes.containsKey(hash) || handledHashes.containsKey(fingerprint)) return false
        val added = runCatching { ingestCommandApi.ingestInstantCode(draft, source) }
            .onFailure { Log.w(TAG, "Clipboard code auto ingest failed", it) }
            .getOrNull()
        handledHashes[hash] = now
        handledHashes[fingerprint] = now
        cleanup(now)
        if (added != null) {
            Log.d(TAG, "Clipboard code auto ingested: ${candidate.type.displayLabel} ${candidate.code}")
        }
        return added != null
    }

    private suspend fun readClipboardText(): String? = withContext(Dispatchers.Main) {
        runCatching {
            val clipboard = appContext.getSystemService(ClipboardManager::class.java) ?: return@runCatching null
            val clip = clipboard.primaryClip ?: return@runCatching null
            if (clip.itemCount <= 0) return@runCatching null
            clip.getItemAt(0).coerceToText(appContext)?.toString()?.trim()?.takeIf { it.isNotBlank() }
        }.onFailure { Log.w(TAG, "Read clipboard failed", it) }.getOrNull()
    }

    private fun markHandled(hash: String) {
        handledHashes[hash] = System.currentTimeMillis()
        cleanup(System.currentTimeMillis())
    }

    private fun cleanup(now: Long) {
        cleanupMap(handledHashes, now)
        cleanupMap(ignoredHashes, now)
    }

    private fun cleanupMap(map: LinkedHashMap<String, Long>, now: Long) {
        val iterator = map.entries.iterator()
        while (iterator.hasNext()) {
            if (now - iterator.next().value > ENTRY_TTL_MS) iterator.remove()
        }
        while (map.size > MAX_ENTRIES) {
            val eldest = map.entries.iterator()
            if (!eldest.hasNext()) return
            eldest.next()
            eldest.remove()
        }
    }

    private fun hashText(text: String): String = fingerprintOf(text.trim())

    private fun fingerprintOf(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private companion object {
        private const val TAG = "ClipboardCodeCenter"
        private const val ENTRY_TTL_MS = 10 * 60 * 1000L
        private const val MAX_ENTRIES = 128
    }
}
