package com.antgskds.calendarassistant.feature.accounting.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountingDao {
    @Query("SELECT * FROM accounting_drafts ORDER BY createdAt DESC, id")
    fun observeDrafts(): Flow<List<AccountingDraft>>

    @Query("SELECT * FROM accounting_drafts WHERE id = :id")
    suspend fun findDraft(id: String): AccountingDraft?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDraft(draft: AccountingDraft)

    @Query("DELETE FROM accounting_drafts WHERE id = :id")
    suspend fun deleteDraft(id: String)

    @Query("SELECT * FROM accounting_entries WHERE amountMinor = :amount AND direction = :direction AND currency = :currency AND occurredAt BETWEEN :start AND :end")
    suspend fun duplicateCandidates(amount: Long, direction: String, currency: String, start: Long, end: Long): List<AccountingEntry>

    @Query("SELECT * FROM accounting_entries WHERE deletedAt IS NULL ORDER BY occurredAt DESC, id")
    fun observeEntries(): Flow<List<AccountingEntry>>

    @Query("SELECT * FROM accounting_entries WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): AccountingEntry?

    // 含删除标记：重复导入不得使已删除账单复活。
    @Query("SELECT * FROM accounting_entries WHERE id = :id OR dedupKey = :key LIMIT 1")
    suspend fun findDuplicate(id: String, key: String): AccountingEntry?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: AccountingEntry)

    @Update
    suspend fun update(entry: AccountingEntry)
}
