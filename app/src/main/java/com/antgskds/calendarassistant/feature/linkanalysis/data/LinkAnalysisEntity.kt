package com.antgskds.calendarassistant.feature.linkanalysis.data

import androidx.room.*
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.linkanalysis.domain.LinkSummaryData
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "quick_memo_link_analysis", foreignKeys = [
    ForeignKey(entity = QuickMemoEntity::class, parentColumns = ["id"], childColumns = ["memoId"], onDelete = ForeignKey.CASCADE)
])
data class LinkAnalysisEntity(
    @PrimaryKey val memoId: Long,
    val token: String = "", val sourceUrl: String = "", val sourceId: String = "", val sourceDigest: String = "",
    val localAudio: Boolean = true, val state: String = "QUEUED", val error: String = "", val resultJson: String = "", val updatedAt: Long = 0,
)
@Dao interface LinkAnalysisDao {
    @Query("SELECT * FROM quick_memo_link_analysis") fun observe(): Flow<List<LinkAnalysisEntity>>
    @Query("SELECT * FROM quick_memo_link_analysis") suspend fun all(): List<LinkAnalysisEntity>
    @Query("SELECT * FROM quick_memo_link_analysis WHERE memoId = :id") suspend fun get(id: Long): LinkAnalysisEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(entity: LinkAnalysisEntity)
    @Query("UPDATE quick_memo_link_analysis SET state = :state, error = :error, updatedAt = :now WHERE memoId = :id AND token = :token AND state NOT IN ('CANCELLED', 'DONE')")
    suspend fun state(id: Long, token: String, state: String, error: String, now: Long): Int
    @Query("UPDATE quick_memo_link_analysis SET resultJson = :result, state = 'DONE', error = '', updatedAt = :now WHERE memoId = :id AND token = :token AND state != 'CANCELLED'")
    suspend fun complete(id: Long, token: String, result: String, now: Long): Int
}
