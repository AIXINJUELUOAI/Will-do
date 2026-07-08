package com.antgskds.calendarassistant.core.quickmemo

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickMemoDao {
    @Query("SELECT * FROM quick_memos ORDER BY sort_rank ASC, updated_at DESC")
    fun observeQuickMemos(): Flow<List<QuickMemoEntity>>

    @Query("SELECT * FROM quick_memo_suggestions ORDER BY created_at DESC")
    fun observeSuggestions(): Flow<List<QuickMemoSuggestionEntity>>

    @Query("SELECT * FROM quick_memos WHERE id = :id LIMIT 1")
    suspend fun getQuickMemo(id: Long): QuickMemoEntity?

    @Query("SELECT * FROM quick_memos ORDER BY created_at ASC")
    suspend fun getAllQuickMemos(): List<QuickMemoEntity>

    @Query("SELECT * FROM quick_memos WHERE type = 'VOICE' AND transcription_status IN ('PENDING', 'PROCESSING') ORDER BY created_at ASC")
    suspend fun getUnfinishedVoiceMemos(): List<QuickMemoEntity>

    @Query("SELECT MIN(sort_rank) FROM quick_memos")
    suspend fun getMinSortRank(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuickMemo(memo: QuickMemoEntity): Long

    @Update
    suspend fun updateQuickMemo(memo: QuickMemoEntity)

    @Delete
    suspend fun deleteQuickMemo(memo: QuickMemoEntity)

    @Query("DELETE FROM quick_memos WHERE id = :id")
    suspend fun deleteQuickMemoById(id: Long)

    @Query("DELETE FROM quick_memo_suggestions")
    suspend fun deleteAllSuggestions()

    @Query("DELETE FROM quick_memos")
    suspend fun deleteAllQuickMemos()

    @Transaction
    suspend fun deleteAllQuickMemoData() {
        deleteAllSuggestions()
        deleteAllQuickMemos()
    }

    @Query("UPDATE quick_memos SET sort_rank = :sortRank WHERE id = :id")
    suspend fun updateSortRank(id: Long, sortRank: Long)

    @Transaction
    suspend fun updateSortRanks(ids: List<Long>) {
        ids.forEachIndexed { index, id ->
            updateSortRank(id, index.toLong() * 1_000L)
        }
    }

    @Query("SELECT * FROM quick_memo_suggestions WHERE quick_memo_id = :quickMemoId ORDER BY created_at DESC")
    fun observeSuggestionsForMemo(quickMemoId: Long): Flow<List<QuickMemoSuggestionEntity>>

    @Query("SELECT * FROM quick_memo_suggestions WHERE quick_memo_id = :quickMemoId ORDER BY created_at DESC")
    suspend fun getSuggestionsForMemo(quickMemoId: Long): List<QuickMemoSuggestionEntity>

    @Query("SELECT * FROM quick_memo_suggestions ORDER BY created_at ASC")
    suspend fun getAllSuggestions(): List<QuickMemoSuggestionEntity>

    @Query("SELECT * FROM quick_memo_suggestions WHERE id = :id LIMIT 1")
    suspend fun getSuggestion(id: Long): QuickMemoSuggestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuggestion(suggestion: QuickMemoSuggestionEntity): Long

    @Update
    suspend fun updateSuggestion(suggestion: QuickMemoSuggestionEntity)

    @Query("DELETE FROM quick_memo_suggestions WHERE quick_memo_id = :quickMemoId")
    suspend fun deleteSuggestionsForMemo(quickMemoId: Long)
}
