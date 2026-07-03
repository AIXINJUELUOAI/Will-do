package com.antgskds.calendarassistant.core.quickmemo

import java.io.File
import kotlinx.coroutines.flow.Flow

class QuickMemoRepository(
    private val quickMemoDao: QuickMemoDao
) {
    val quickMemos: Flow<List<QuickMemoEntity>> = quickMemoDao.observeQuickMemos()
    val suggestions: Flow<List<QuickMemoSuggestionEntity>> = quickMemoDao.observeSuggestions()

    suspend fun getQuickMemo(id: Long): QuickMemoEntity? = quickMemoDao.getQuickMemo(id)

    suspend fun getUnfinishedVoiceMemos(): List<QuickMemoEntity> = quickMemoDao.getUnfinishedVoiceMemos()

    suspend fun createTextMemo(bodyText: String, asTodo: Boolean = false): Long {
        val now = System.currentTimeMillis()
        return quickMemoDao.insertQuickMemo(
            QuickMemoEntity(
                type = QuickMemoType.TEXT,
                bodyText = normalizeBody(bodyText),
                transcriptionStatus = QuickMemoTranscriptionStatus.NONE,
                analysisStatus = QuickMemoAnalysisStatus.NONE,
                createdAt = now,
                updatedAt = now,
                sortRank = nextTopSortRank(),
                todoState = if (asTodo) QuickMemoTodoState.ACTIVE else QuickMemoTodoState.NONE
            )
        )
    }

    suspend fun createVoiceMemo(
        audioPath: String,
        durationMs: Long,
        bodyText: String = "",
        asTodo: Boolean = false
    ): Long {
        val now = System.currentTimeMillis()
        return quickMemoDao.insertQuickMemo(
            QuickMemoEntity(
                type = QuickMemoType.VOICE,
                bodyText = normalizeBody(bodyText),
                audioPath = audioPath,
                audioDurationMs = durationMs.coerceAtLeast(0L),
                transcriptionStatus = QuickMemoTranscriptionStatus.PENDING,
                analysisStatus = QuickMemoAnalysisStatus.NONE,
                createdAt = now,
                updatedAt = now,
                sortRank = nextTopSortRank(),
                todoState = if (asTodo) QuickMemoTodoState.ACTIVE else QuickMemoTodoState.NONE
            )
        )
    }

    suspend fun createImageMemo(
        imagePath: String,
        bodyText: String = "",
        asTodo: Boolean = false
    ): Long {
        val now = System.currentTimeMillis()
        return quickMemoDao.insertQuickMemo(
            QuickMemoEntity(
                type = QuickMemoType.IMAGE,
                bodyText = normalizeBody(bodyText),
                imagePath = imagePath,
                transcriptionStatus = QuickMemoTranscriptionStatus.NONE,
                analysisStatus = QuickMemoAnalysisStatus.NONE,
                createdAt = now,
                updatedAt = now,
                sortRank = nextTopSortRank(),
                todoState = if (asTodo) QuickMemoTodoState.ACTIVE else QuickMemoTodoState.NONE
            )
        )
    }

    suspend fun updateBody(id: Long, bodyText: String) {
        val memo = quickMemoDao.getQuickMemo(id) ?: return
        quickMemoDao.updateQuickMemo(
            memo.copy(
                bodyText = normalizeBody(bodyText),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun attachImage(id: Long, imagePath: String) {
        val memo = quickMemoDao.getQuickMemo(id) ?: return
        val oldPath = memo.imagePath?.takeIf { it.isNotBlank() && it != imagePath }
        quickMemoDao.updateQuickMemo(
            memo.copy(
                type = if (memo.type == QuickMemoType.VOICE) memo.type else QuickMemoType.IMAGE,
                imagePath = imagePath,
                updatedAt = System.currentTimeMillis()
            )
        )
        oldPath?.let { path -> runCatching { File(path).delete() } }
    }

    suspend fun updateTranscriptionStatus(id: Long, status: String, bodyText: String? = null) {
        val memo = quickMemoDao.getQuickMemo(id) ?: return
        val now = System.currentTimeMillis()
        quickMemoDao.updateQuickMemo(
            memo.copy(
                bodyText = bodyText?.let { normalizeBody(it) } ?: memo.bodyText,
                transcriptionStatus = status,
                updatedAt = now
            )
        )
    }

    suspend fun updateAnalysisStatus(id: Long, status: String) {
        val memo = quickMemoDao.getQuickMemo(id) ?: return
        quickMemoDao.updateQuickMemo(
            memo.copy(
                analysisStatus = status,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateTodoState(id: Long, todoState: String) {
        val memo = quickMemoDao.getQuickMemo(id) ?: return
        val now = System.currentTimeMillis()
        val normalizedState = normalizeTodoState(todoState)
        val shouldMoveToTop = normalizedState == QuickMemoTodoState.ACTIVE && memo.todoState != QuickMemoTodoState.ACTIVE
        quickMemoDao.updateQuickMemo(
            memo.copy(
                todoState = normalizedState,
                todoPendingUntil = null,
                todoCompletedAt = if (normalizedState == QuickMemoTodoState.COMPLETED) now else null,
                sortRank = if (shouldMoveToTop) nextTopSortRank() else memo.sortRank,
                updatedAt = now
            )
        )
    }

    suspend fun toggleTodoCompletion(id: Long) {
        val memo = quickMemoDao.getQuickMemo(id) ?: return
        if (!memo.isTodo) return
        updateTodoState(
            id = id,
            todoState = if (memo.isTodoCompleted) QuickMemoTodoState.ACTIVE else QuickMemoTodoState.COMPLETED
        )
    }

    suspend fun deleteQuickMemo(id: Long) {
        val memo = quickMemoDao.getQuickMemo(id)
        quickMemoDao.deleteQuickMemoById(id)
        memo?.audioPath?.takeIf { it.isNotBlank() }?.let { path ->
            runCatching { File(path).delete() }
        }
        memo?.imagePath?.takeIf { it.isNotBlank() }?.let { path ->
            runCatching { File(path).delete() }
        }
    }

    suspend fun updateSortRanks(ids: List<Long>) {
        quickMemoDao.updateSortRanks(ids.distinct())
    }

    suspend fun insertSuggestion(suggestion: QuickMemoSuggestionEntity): Long {
        return quickMemoDao.insertSuggestion(suggestion)
    }

    suspend fun getSuggestion(id: Long): QuickMemoSuggestionEntity? {
        return quickMemoDao.getSuggestion(id)
    }

    suspend fun updateSuggestion(suggestion: QuickMemoSuggestionEntity) {
        quickMemoDao.updateSuggestion(suggestion.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateSuggestionStatus(id: Long, status: String, eventId: Long? = null) {
        val suggestion = quickMemoDao.getSuggestion(id) ?: return
        quickMemoDao.updateSuggestion(
            suggestion.copy(
                status = status,
                eventId = eventId ?: suggestion.eventId,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getSuggestionsForMemo(quickMemoId: Long): List<QuickMemoSuggestionEntity> {
        return quickMemoDao.getSuggestionsForMemo(quickMemoId)
    }

    private fun normalizeBody(bodyText: String): String {
        return bodyText.replace("\r\n", "\n").replace('\r', '\n')
    }

    private fun normalizeTodoState(todoState: String): String {
        return when (todoState) {
            QuickMemoTodoState.ACTIVE,
            QuickMemoTodoState.COMPLETED -> todoState
            else -> QuickMemoTodoState.NONE
        }
    }

    private suspend fun nextTopSortRank(): Long {
        return (quickMemoDao.getMinSortRank() ?: 0L) - 1_000L
    }
}
