package com.antgskds.calendarassistant.core.quickmemo

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface QuickMemoDao {
    @Query("SELECT * FROM quick_memos ORDER BY created_at DESC")
    fun observeQuickMemos(): Flow<List<QuickMemoEntity>>

    @Query("SELECT * FROM quick_memo_suggestions ORDER BY created_at DESC")
    fun observeSuggestions(): Flow<List<QuickMemoSuggestionEntity>>

    @Query("SELECT * FROM quick_memos WHERE id = :id LIMIT 1")
    suspend fun getQuickMemo(id: Long): QuickMemoEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQuickMemo(memo: QuickMemoEntity): Long

    @Update
    suspend fun updateQuickMemo(memo: QuickMemoEntity)

    @Delete
    suspend fun deleteQuickMemo(memo: QuickMemoEntity)

    @Query("DELETE FROM quick_memos WHERE id = :id")
    suspend fun deleteQuickMemoById(id: Long)

    @Query("SELECT * FROM quick_memo_suggestions WHERE quick_memo_id = :quickMemoId ORDER BY created_at DESC")
    fun observeSuggestionsForMemo(quickMemoId: Long): Flow<List<QuickMemoSuggestionEntity>>

    @Query("SELECT * FROM quick_memo_suggestions WHERE quick_memo_id = :quickMemoId ORDER BY created_at DESC")
    suspend fun getSuggestionsForMemo(quickMemoId: Long): List<QuickMemoSuggestionEntity>

    @Query("SELECT * FROM quick_memo_suggestions WHERE id = :id LIMIT 1")
    suspend fun getSuggestion(id: Long): QuickMemoSuggestionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSuggestion(suggestion: QuickMemoSuggestionEntity): Long

    @Update
    suspend fun updateSuggestion(suggestion: QuickMemoSuggestionEntity)

    @Query("DELETE FROM quick_memo_suggestions WHERE quick_memo_id = :quickMemoId")
    suspend fun deleteSuggestionsForMemo(quickMemoId: Long)
}
