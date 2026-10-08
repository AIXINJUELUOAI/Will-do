package com.antgskds.calendarassistant.feature.linkanalysis.api
import com.antgskds.calendarassistant.feature.linkanalysis.data.*
import kotlinx.coroutines.flow.StateFlow
import java.io.InputStream

interface LinkAnalysisApi {
    val sources: StateFlow<List<InstalledLinkSource>>
    val records: StateFlow<List<LinkAnalysisEntity>>
    suspend fun importSource(input: InputStream)
    suspend fun setSourceEnabled(id: String, enabled: Boolean)
    suspend fun deleteSource(id: String)
    suspend fun queue(memoId: Long, force: Boolean = false)
    suspend fun dismissNotification(memoId: Long)
    suspend fun cancel(memoId: Long)
    suspend fun cancelAll()
}
