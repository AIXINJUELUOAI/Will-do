package com.antgskds.calendarassistant.feature.quickmemo.application

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.feature.recognition.application.ai.AnalysisResult
import com.antgskds.calendarassistant.shared.operation.CapsuleCommandApi
import com.antgskds.calendarassistant.shared.query.CapsuleQueryApi
import com.antgskds.calendarassistant.shared.query.SettingsQueryApi
import com.antgskds.calendarassistant.feature.capsule.domain.model.CapsuleType
import com.antgskds.calendarassistant.feature.capsule.domain.model.CapsuleUiState
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoAnalysisStatus
import com.antgskds.calendarassistant.feature.quickmemo.data.QuickMemoRepository
import com.antgskds.calendarassistant.feature.quickmemo.data.serialization.QuickMemoSuggestionCodec
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoSuggestionEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoSuggestionStatus
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoSuggestionType
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoTodoState
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoTranscriptionStatus
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoType
import com.antgskds.calendarassistant.feature.quickmemo.domain.transcription.NoopSpeechTranscriber
import com.antgskds.calendarassistant.feature.quickmemo.domain.transcription.SpeechTranscriber
import com.antgskds.calendarassistant.feature.quickmemo.domain.transcription.TranscriptionResult
import com.antgskds.calendarassistant.feature.recognition.application.RecognitionOrchestrator
import com.antgskds.calendarassistant.feature.notification.application.NotificationOrchestrator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext

class QuickMemoFacade(
    private val repository: QuickMemoRepository,
    private val appScope: CoroutineScope,
    private val speechTranscriber: SpeechTranscriber = NoopSpeechTranscriber(),
    private val recognitionCenter: RecognitionOrchestrator? = null,
    private val settingsQueryApi: SettingsQueryApi? = null,
    private val appContext: Context? = null,
    private val notificationCenter: NotificationOrchestrator? = null,
    private val capsuleCommandApi: CapsuleCommandApi? = null,
    private val capsuleQueryApi: CapsuleQueryApi? = null
) {
    companion object {
        private const val TAG = "QuickMemoFacade"
        private const val TRANSCRIPTION_TIMEOUT_MS = 120_000L
        private const val ASR_PROCESS_CRASH_CHECK_INTERVAL_MS = 3_000L
        private const val ASR_PROCESS_CRASH_CHECK_WINDOW_MS = 30_000L
        private const val ASR_PROCESS_SUFFIX = ":quickmemo_asr"
        private const val USE_ISOLATED_ASR_PROCESS = true
        private const val TEXT_QUICK_MEMO_ID_PREFIX = "TEXT_QUICK_MEMO_"
    }

    private val _quickMemos = MutableStateFlow<List<QuickMemoEntity>>(emptyList())
    val quickMemos: StateFlow<List<QuickMemoEntity>> = _quickMemos.asStateFlow()
    private val _suggestions = MutableStateFlow<List<QuickMemoSuggestionEntity>>(emptyList())
    val suggestions: StateFlow<List<QuickMemoSuggestionEntity>> = _suggestions.asStateFlow()
    private val activeTranscriptionIds = mutableSetOf<Long>()
    private val autoPinAfterTranscriptionIds = mutableSetOf<Long>()

    fun start() {
        appScope.launch(Dispatchers.IO) {
            repository.quickMemos.collect { list ->
                _quickMemos.value = list
            }
        }
        appScope.launch(Dispatchers.IO) {
            repository.suggestions.collect { list ->
                _suggestions.value = list
            }
        }
        appScope.launch(Dispatchers.IO) {
            val resetCount = repository.markProcessingVoiceMemosFailed()
            if (resetCount > 0) {
                Log.w(TAG, "reset stale processing voice memos: $resetCount")
            }
        }
        // Do not auto-retry old voice transcriptions at startup: Sherpa JNI aborts are process-fatal.
        // Users can still retry explicitly from the quick memo UI after the app is open.
    }

    suspend fun getQuickMemo(id: Long): QuickMemoEntity? = withContext(Dispatchers.IO) {
        repository.getQuickMemo(id)
    }

    suspend fun createTextMemo(bodyText: String, asTodo: Boolean = false): Long = withContext(Dispatchers.IO) {
        val id = repository.createTextMemo(bodyText, asTodo)
        val cleanText = bodyText.trim()
        if (cleanText.isNotBlank()) {
            notifyBraceletQuickMemoResult(id, cleanText)
            analyzeTextForSuggestions(id, cleanText)
        }
        id
    }

    suspend fun createVoiceMemo(
        audioPath: String,
        durationMs: Long,
        bodyText: String = "",
        asTodo: Boolean = false,
        autoPinOnTranscriptionSuccess: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        val id = repository.createVoiceMemo(audioPath, durationMs, bodyText, asTodo)
        if (autoPinOnTranscriptionSuccess) {
            markAutoPinAfterTranscription(id)
        }
        processVoiceMemoAsync(id)
        id
    }

    suspend fun createImageMemo(
        imagePath: String,
        bodyText: String = "",
        asTodo: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        val id = repository.createImageMemo(imagePath, bodyText, asTodo)
        val cleanText = bodyText.trim()
        if (cleanText.isNotBlank()) {
            notifyBraceletQuickMemoResult(id, cleanText)
            analyzeTextForSuggestions(id, cleanText)
        } else {
            notifyBraceletQuickMemoResult(id, "图片随口记")
        }
        id
    }

    suspend fun updateBody(id: Long, bodyText: String) = withContext(Dispatchers.IO) {
        repository.updateBody(id, bodyText)
        val memo = repository.getQuickMemo(id) ?: return@withContext
        if (activeTextQuickMemoId() == id) {
            refreshPinnedTextQuickMemo(memo)
        }
    }

    suspend fun attachImageToMemo(id: Long, imagePath: String) = withContext(Dispatchers.IO) {
        repository.attachImage(id, imagePath)
    }

    suspend fun removeImageFromMemo(id: Long): Boolean = withContext(Dispatchers.IO) {
        val removed = repository.removeImage(id)
        if (removed && activeTextQuickMemoId() == id) {
            repository.getQuickMemo(id)?.let { refreshPinnedTextQuickMemo(it) }
                ?: capsuleCommandApi?.clearTextQuickMemo()
        }
        removed
    }

    suspend fun attachVoiceToMemo(id: Long, audioPath: String, durationMs: Long): Boolean = withContext(Dispatchers.IO) {
        val attached = repository.attachVoice(id, audioPath, durationMs)
        if (attached) {
            processVoiceMemoAsync(id)
        }
        attached
    }

    suspend fun pinQuickMemo(id: Long): Boolean = withContext(Dispatchers.IO) {
        val memo = repository.getQuickMemo(id) ?: return@withContext false
        val text = memo.displayTextForCapsule().takeIf { it.isNotBlank() } ?: return@withContext false
        capsuleCommandApi?.showTextQuickMemo(id, text)
        true
    }

    suspend fun clearPinnedTextQuickMemo(id: Long? = null): Boolean = withContext(Dispatchers.IO) {
        if (id != null && activeTextQuickMemoId() != id) return@withContext false
        capsuleCommandApi?.clearTextQuickMemo()
        true
    }

    fun isQuickMemoPinned(id: Long): Boolean = activeTextQuickMemoId() == id

    suspend fun markTodoActive(id: Long) = withContext(Dispatchers.IO) {
        repository.updateTodoState(id, QuickMemoTodoState.ACTIVE)
    }

    suspend fun removeTodo(id: Long) = withContext(Dispatchers.IO) {
        repository.updateTodoState(id, QuickMemoTodoState.NONE)
    }

    suspend fun toggleTodoCompletion(id: Long) = withContext(Dispatchers.IO) {
        repository.toggleTodoCompletion(id)
    }

    suspend fun updateSortRanks(ids: List<Long>) = withContext(Dispatchers.IO) {
        repository.updateSortRanks(ids)
    }

    suspend fun deleteQuickMemo(id: Long) = withContext(Dispatchers.IO) {
        clearPinnedTextQuickMemo(id)
        repository.deleteQuickMemo(id)
    }

    suspend fun clearAllQuickMemos(): Int = withContext(Dispatchers.IO) {
        capsuleCommandApi?.clearTextQuickMemo()
        repository.clearAllQuickMemos()
    }

    suspend fun getSuggestion(id: Long): QuickMemoSuggestionEntity? = withContext(Dispatchers.IO) {
        repository.getSuggestion(id)
    }

    suspend fun markSuggestionCreated(id: Long, eventId: Long) = withContext(Dispatchers.IO) {
        repository.updateSuggestionStatus(id, QuickMemoSuggestionStatus.CREATED, eventId)
    }

    fun retryTranscription(id: Long) {
        processVoiceMemoAsync(id)
    }

    fun processVoiceMemoAsync(id: Long) {
        appScope.launch(Dispatchers.IO) {
            if (!tryBeginTranscription(id)) return@launch
            val autoPin = consumeAutoPinAfterTranscription(id)
            try {
                val memo = repository.getQuickMemo(id) ?: run {
                    finishTranscription(id)
                    return@launch
                }
                val audioPath = memo.audioPath?.takeIf { it.isNotBlank() }
                if (audioPath == null) {
                    repository.updateTranscriptionStatus(id, QuickMemoTranscriptionStatus.FAILED)
                    notifyBraceletQuickMemoFailed(id)
                    finishTranscription(id)
                    return@launch
                }
                repository.updateTranscriptionStatus(id, QuickMemoTranscriptionStatus.PROCESSING)
                val context = appContext
                if (context == null || !USE_ISOLATED_ASR_PROCESS) {
                    Log.w(TAG, "ASR using in-process diagnostic path memoId=$id isolated=$USE_ISOLATED_ASR_PROCESS")
                    processVoiceMemoInCurrentProcess(id, audioPath, autoPin)
                } else {
                    val startedAt = System.currentTimeMillis()
                    QuickMemoTranscriptionService.enqueue(context, id, audioPath, autoPin)
                    Log.i(TAG, "ASR service enqueued memoId=$id audioPath=$audioPath autoPin=$autoPin")
                    launchTranscriptionWatchdog(id, audioPath, startedAt)
                }
            } catch (e: Exception) {
                Log.e(TAG, "process voice memo failed", e)
                repository.updateTranscriptionStatus(id, QuickMemoTranscriptionStatus.FAILED)
                notifyBraceletQuickMemoFailed(id)
                finishTranscription(id)
            }
            return@launch
            /*
            var restartForNewAudio = false
            var transcribingAudioPath: String? = null
            try {
                val memo = repository.getQuickMemo(id) ?: return@launch
                val audioPath = memo.audioPath?.takeIf { it.isNotBlank() }
                if (audioPath == null) {
                    repository.updateTranscriptionStatus(id, QuickMemoTranscriptionStatus.FAILED)
                    return@launch
                }
                transcribingAudioPath = audioPath
                repository.updateTranscriptionStatus(id, QuickMemoTranscriptionStatus.PROCESSING)
                when (val result = withTimeout(TRANSCRIPTION_TIMEOUT_MS) { speechTranscriber.transcribe(audioPath) }) {
                    is TranscriptionResult.Success -> {
                        if (!isCurrentAudioPath(id, audioPath)) {
                            restartForNewAudio = true
                            return@launch
                        }
                        val text = result.text.trim()
                        if (text.isBlank()) {
                            repository.updateTranscriptionStatus(id, QuickMemoTranscriptionStatus.FAILED)
                        } else {
                            repository.updateTranscriptionStatus(id, QuickMemoTranscriptionStatus.SUCCESS, text)
                            analyzeTextForSuggestions(id, text)
                            maybeAutoPinVoiceMemoAfterTranscription(id, autoPin)
                        }
                    }
                    is TranscriptionResult.Failure -> {
                        if (!isCurrentAudioPath(id, audioPath)) {
                            restartForNewAudio = true
                            return@launch
                        }
                        Log.w(TAG, "语音转写失败: ${result.message}")
                        clearAutoPinAfterTranscription(id)
                        repository.updateTranscriptionStatus(id, QuickMemoTranscriptionStatus.FAILED)
                    }
                }
            } catch (e: Exception) {
                val audioPath = transcribingAudioPath
                if (audioPath != null && !isCurrentAudioPath(id, audioPath)) {
                    restartForNewAudio = true
                    return@launch
                }
                Log.e(TAG, "处理语音随口记失败", e)
                clearAutoPinAfterTranscription(id)
                repository.updateTranscriptionStatus(id, QuickMemoTranscriptionStatus.FAILED)
            } finally {
                finishTranscription(id)
                if (!restartForNewAudio) {
                    clearAutoPinAfterTranscription(id)
                }
                if (restartForNewAudio) {
                    processVoiceMemoAsync(id)
                }
            }
            */
        }
    }

    private suspend fun processVoiceMemoInCurrentProcess(id: Long, audioPath: String, autoPin: Boolean) {
        try {
            when (val result = withTimeout(TRANSCRIPTION_TIMEOUT_MS) { speechTranscriber.transcribe(audioPath) }) {
                is TranscriptionResult.Success -> {
                    if (!isCurrentAudioPath(id, audioPath)) return
                    val text = result.text.trim()
                    if (text.isBlank()) {
                        repository.updateTranscriptionStatus(id, QuickMemoTranscriptionStatus.FAILED)
                    } else {
                        repository.updateTranscriptionStatus(id, QuickMemoTranscriptionStatus.SUCCESS, text)
                        onExternalTranscriptionSucceeded(id, text, autoPin)
                    }
                }
                is TranscriptionResult.Failure -> {
                    if (!isCurrentAudioPath(id, audioPath)) return
                    Log.w(TAG, "璇煶杞啓澶辫触: ${result.message}")
                    repository.updateTranscriptionStatus(id, QuickMemoTranscriptionStatus.FAILED)
                    onExternalTranscriptionFailed(id)
                }
            }
        } finally {
            finishTranscription(id)
        }
    }

    private fun launchTranscriptionWatchdog(id: Long, audioPath: String, startedAt: Long) {
        appScope.launch(Dispatchers.IO) {
            var elapsed = 0L
            while (elapsed < ASR_PROCESS_CRASH_CHECK_WINDOW_MS) {
                kotlinx.coroutines.delay(ASR_PROCESS_CRASH_CHECK_INTERVAL_MS)
                elapsed += ASR_PROCESS_CRASH_CHECK_INTERVAL_MS
                val memo = repository.getQuickMemo(id) ?: run {
                    finishTranscription(id)
                    return@launch
                }
                if (memo.audioPath != audioPath || memo.transcriptionStatus != QuickMemoTranscriptionStatus.PROCESSING) {
                    finishTranscription(id)
                    return@launch
                }
                if (hasAsrProcessExitedSince(startedAt)) {
                    Log.w(TAG, "ASR process exited during transcription, mark memo failed: $id")
                    repository.updateTranscriptionStatus(id, QuickMemoTranscriptionStatus.FAILED)
                    notifyBraceletQuickMemoFailed(id)
                    val after = repository.getQuickMemo(id)
                    Log.w(TAG, "ASR watchdog status after mark memoId=$id status=${after?.transcriptionStatus} audio=${after?.audioPath}")
                    finishTranscription(id)
                    return@launch
                }
            }

            kotlinx.coroutines.delay((TRANSCRIPTION_TIMEOUT_MS - ASR_PROCESS_CRASH_CHECK_WINDOW_MS).coerceAtLeast(0L) + 5_000L)
            val memo = repository.getQuickMemo(id) ?: run {
                finishTranscription(id)
                return@launch
            }
            if (memo.audioPath == audioPath && memo.transcriptionStatus == QuickMemoTranscriptionStatus.PROCESSING) {
                Log.w(TAG, "ASR transcription timeout, mark memo failed: $id")
                repository.updateTranscriptionStatus(id, QuickMemoTranscriptionStatus.FAILED)
                notifyBraceletQuickMemoFailed(id)
                val after = repository.getQuickMemo(id)
                Log.w(TAG, "ASR timeout status after mark memoId=$id status=${after?.transcriptionStatus}")
            }
            finishTranscription(id)
        }
    }

    private fun hasAsrProcessExitedSince(startedAt: Long): Boolean {
        val context = appContext ?: return false
        val manager = context.getSystemService(ActivityManager::class.java) ?: return false
        val asrProcessName = "${context.packageName}$ASR_PROCESS_SUFFIX"
        return manager
            .getHistoricalProcessExitReasons(context.packageName, 0, 8)
            .any { info -> info.processName == asrProcessName && info.timestamp >= startedAt }
    }

    fun onExternalTranscriptionSucceeded(id: Long, text: String, autoPin: Boolean) {
        appScope.launch(Dispatchers.IO) {
            finishTranscription(id)
            val cleanText = text.trim()
            if (cleanText.isNotBlank()) {
                notifyBraceletQuickMemoResult(id, cleanText)
                analyzeTextForSuggestions(id, cleanText)
            }
            if (autoPin) {
                maybeAutoPinVoiceMemoAfterTranscription(id, autoPin)
            }
        }
    }

    fun onExternalTranscriptionFailed(id: Long) {
        notifyBraceletQuickMemoFailed(id)
        finishTranscription(id)
    }

    private suspend fun isCurrentAudioPath(id: Long, audioPath: String): Boolean {
        return repository.getQuickMemo(id)?.audioPath == audioPath
    }

    private fun tryBeginTranscription(id: Long): Boolean = synchronized(activeTranscriptionIds) {
        activeTranscriptionIds.add(id)
    }

    private fun finishTranscription(id: Long) = synchronized(activeTranscriptionIds) {
        activeTranscriptionIds.remove(id)
    }

    private fun notifyBraceletQuickMemoResult(id: Long, text: String) {
        (appContext as? App)?.braceletNotificationCenter?.notifyQuickMemoResult(id, text, failed = false)
    }

    private fun notifyBraceletQuickMemoFailed(id: Long) {
        (appContext as? App)?.braceletNotificationCenter?.notifyQuickMemoResult(id, "转写失败", failed = true)
    }

    private fun markAutoPinAfterTranscription(id: Long) = synchronized(autoPinAfterTranscriptionIds) {
        autoPinAfterTranscriptionIds.add(id)
    }

    private fun consumeAutoPinAfterTranscription(id: Long): Boolean = synchronized(autoPinAfterTranscriptionIds) {
        autoPinAfterTranscriptionIds.remove(id)
    }

    private fun clearAutoPinAfterTranscription(id: Long) = synchronized(autoPinAfterTranscriptionIds) {
        autoPinAfterTranscriptionIds.remove(id)
    }

    private suspend fun maybeAutoPinVoiceMemoAfterTranscription(id: Long, requested: Boolean) {
        if (!requested) return
        if (settingsQueryApi?.settings?.value?.isLiveCapsuleEnabled != true) return
        runCatching { pinQuickMemo(id) }
            .onFailure { Log.w(TAG, "语音随口记自动挂起失败", it) }
    }

    private fun refreshPinnedTextQuickMemo(memo: QuickMemoEntity) {
        val id = memo.id ?: return
        val text = memo.displayTextForCapsule()
        if (text.isBlank()) {
            capsuleCommandApi?.clearTextQuickMemo()
        } else {
            capsuleCommandApi?.showTextQuickMemo(id, text)
        }
    }

    private fun activeTextQuickMemoId(): Long? {
        val state = capsuleQueryApi?.uiState?.value as? CapsuleUiState.Active ?: return null
        return state.capsules.firstOrNull { item -> item.type == CapsuleType.TEXT_QUICK_MEMO }
            ?.id
            ?.removePrefix(TEXT_QUICK_MEMO_ID_PREFIX)
            ?.toLongOrNull()
    }

    private fun QuickMemoEntity.displayTextForCapsule(): String {
        bodyText.trim().takeIf { it.isNotBlank() }?.let { return it }
        return when (type) {
            QuickMemoType.IMAGE -> "图片随口记"
            QuickMemoType.VOICE -> "语音随口记"
            else -> ""
        }
    }

    fun analyzeTextForSuggestions(id: Long, text: String) {
        val recognition = recognitionCenter ?: return
        val settingsApi = settingsQueryApi ?: return
        val context = appContext ?: return
        appScope.launch(Dispatchers.IO) {
            try {
                if (text.isBlank()) return@launch
                repository.updateAnalysisStatus(id, QuickMemoAnalysisStatus.PROCESSING)
                when (val result = recognition.analyzeTextEvents(
                    text = text,
                    settings = settingsApi.settings.value,
                    context = context,
                    sourceType = "quick_memo",
                    sourceId = id.toString(),
                    ingestRequested = false
                )) {
                    is AnalysisResult.Success -> {
                        val candidates = result.data.filter { it.title.isNotBlank() }
                        candidates.forEach { draft ->
                            val suggestionId = repository.insertSuggestion(
                                QuickMemoSuggestionEntity(
                                    quickMemoId = id,
                                    type = QuickMemoSuggestionType.SCHEDULE,
                                    status = QuickMemoSuggestionStatus.PENDING,
                                    candidateJson = QuickMemoSuggestionCodec.encode(draft)
                                )
                            )
                            notificationCenter?.showQuickMemoScheduleSuggestion(suggestionId, id, draft)
                        }
                        repository.updateAnalysisStatus(id, QuickMemoAnalysisStatus.SUCCESS)
                    }
                    is AnalysisResult.Empty -> {
                        repository.updateAnalysisStatus(id, QuickMemoAnalysisStatus.SUCCESS)
                    }
                    is AnalysisResult.Failure -> {
                        Log.w(TAG, "随口记日程候选分析失败: ${result.failure.fullMessage()}")
                        repository.updateAnalysisStatus(id, QuickMemoAnalysisStatus.FAILED)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "随口记日程候选分析异常", e)
                repository.updateAnalysisStatus(id, QuickMemoAnalysisStatus.FAILED)
            }
        }
    }

}
