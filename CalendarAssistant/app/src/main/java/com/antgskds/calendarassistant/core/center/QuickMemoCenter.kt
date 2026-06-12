package com.antgskds.calendarassistant.core.center

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoEntity
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoAnalysisStatus
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoRepository
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoSuggestionCodec
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoSuggestionEntity
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoSuggestionStatus
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoSuggestionType
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoTodoState
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoTranscriptionStatus
import com.antgskds.calendarassistant.core.quickmemo.asr.NoopSpeechTranscriber
import com.antgskds.calendarassistant.core.quickmemo.asr.SpeechTranscriber
import com.antgskds.calendarassistant.core.quickmemo.asr.TranscriptionResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class QuickMemoCenter(
    private val repository: QuickMemoRepository,
    private val appScope: CoroutineScope,
    private val speechTranscriber: SpeechTranscriber = NoopSpeechTranscriber(),
    private val recognitionCenter: RecognitionCenter? = null,
    private val settingsQueryApi: SettingsQueryApi? = null,
    private val appContext: Context? = null,
    private val notificationCenter: NotificationCenter? = null
) {
    companion object {
        private const val TAG = "QuickMemoCenter"
    }

    private val _quickMemos = MutableStateFlow<List<QuickMemoEntity>>(emptyList())
    val quickMemos: StateFlow<List<QuickMemoEntity>> = _quickMemos.asStateFlow()
    private val _suggestions = MutableStateFlow<List<QuickMemoSuggestionEntity>>(emptyList())
    val suggestions: StateFlow<List<QuickMemoSuggestionEntity>> = _suggestions.asStateFlow()

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
    }

    suspend fun getQuickMemo(id: Long): QuickMemoEntity? = withContext(Dispatchers.IO) {
        repository.getQuickMemo(id)
    }

    suspend fun createTextMemo(bodyText: String, asTodo: Boolean = false): Long = withContext(Dispatchers.IO) {
        repository.createTextMemo(bodyText, asTodo)
    }

    suspend fun createVoiceMemo(
        audioPath: String,
        durationMs: Long,
        bodyText: String = "",
        asTodo: Boolean = false
    ): Long = withContext(Dispatchers.IO) {
        val id = repository.createVoiceMemo(audioPath, durationMs, bodyText, asTodo)
        processVoiceMemoAsync(id)
        id
    }

    suspend fun updateBody(id: Long, bodyText: String) = withContext(Dispatchers.IO) {
        repository.updateBody(id, bodyText)
    }

    suspend fun markTodoActive(id: Long) = withContext(Dispatchers.IO) {
        repository.updateTodoState(id, QuickMemoTodoState.ACTIVE)
    }

    suspend fun toggleTodoCompletion(id: Long) = withContext(Dispatchers.IO) {
        repository.toggleTodoCompletion(id)
    }

    suspend fun deleteQuickMemo(id: Long) = withContext(Dispatchers.IO) {
        repository.deleteQuickMemo(id)
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
            try {
                val memo = repository.getQuickMemo(id) ?: return@launch
                val audioPath = memo.audioPath?.takeIf { it.isNotBlank() } ?: return@launch
                repository.updateTranscriptionStatus(id, QuickMemoTranscriptionStatus.PROCESSING)
                when (val result = speechTranscriber.transcribe(audioPath)) {
                    is TranscriptionResult.Success -> {
                        val text = result.text.trim()
                        if (text.isBlank()) {
                            repository.updateTranscriptionStatus(id, QuickMemoTranscriptionStatus.FAILED)
                        } else {
                            repository.updateTranscriptionStatus(id, QuickMemoTranscriptionStatus.SUCCESS, text)
                            analyzeTextForSuggestions(id, text)
                        }
                    }
                    is TranscriptionResult.Failure -> {
                        Log.w(TAG, "语音转写失败: ${result.message}")
                        repository.updateTranscriptionStatus(id, QuickMemoTranscriptionStatus.FAILED)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "处理语音随口记失败", e)
                repository.updateTranscriptionStatus(id, QuickMemoTranscriptionStatus.FAILED)
            }
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
                            notificationCenter?.showQuickMemoScheduleSuggestion(suggestionId, draft)
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
