package com.antgskds.calendarassistant.feature.linkanalysis.application
import android.content.Context
import androidx.work.*
import com.antgskds.calendarassistant.feature.linkanalysis.api.LinkAnalysisApi
import com.antgskds.calendarassistant.feature.linkanalysis.data.*
import com.antgskds.calendarassistant.feature.linkanalysis.domain.*
import com.antgskds.calendarassistant.feature.notification.api.NotificationApi
import com.antgskds.calendarassistant.feature.notification.model.*
import com.antgskds.calendarassistant.feature.notification.policy.LinkSummaryNotificationPolicy
import com.antgskds.calendarassistant.platform.linkanalysis.*
import com.antgskds.calendarassistant.shared.query.SettingsQueryApi
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog as Limits
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.io.InputStream
import java.util.UUID

class LinkAnalysisCoordinator(
    private val context: Context, private val repository: LinkAnalysisRepository, private val store: LinkSourceStore,
    private val settings: SettingsQueryApi, private val notifications: NotificationApi, private val scope: CoroutineScope,
) : LinkAnalysisApi {
    override val sources = store.sources
    override val records = repository.records.stateIn(scope,SharingStarted.Eagerly,emptyList())
    private val gate = Mutex()
    private val work get() = WorkManager.getInstance(context)
    override suspend fun importSource(input: InputStream) = gate.withLock {
        val imported = store.import(input)
        work.cancelAllWorkByTag(sourceTag(imported.manifest.id))
        cancelSourceRecords(imported.manifest.id)
    }
    override suspend fun setSourceEnabled(id: String, enabled: Boolean) = gate.withLock {
        store.setEnabled(id,enabled)
        if (!enabled) { work.cancelAllWorkByTag(sourceTag(id)); cancelSourceRecords(id) }
    }
    override suspend fun deleteSource(id: String) = gate.withLock {
        store.remove(id); work.cancelAllWorkByTag(sourceTag(id)); cancelSourceRecords(id)
    }
    private suspend fun cancelSourceRecords(id: String) {
        repository.all().filter { it.sourceId == id }.forEach {
            if(it.state!="DONE") repository.state(it.memoId,it.token,"CANCELLED")
            notifications.cancel(LinkSummaryNotificationPolicy.key(it.memoId))
        }
    }
    override suspend fun queue(memoId: Long, force: Boolean) = gate.withLock {
        if (!LinkAnalysisPolicy.enabled(settings.settings.value)) {
            if (force) error("请先开启链接自动摘要")
            return@withLock
        }
        val memo = repository.memo(memoId) ?: return@withLock
        val url = memo.sourceUrl ?: return@withLock
        val previous = repository.get(memoId)
        // New access parameters retry an unfinished record; successful summaries remain valid.
        if (!force && previous != null && (previous.sourceUrl == url || previous.state == "DONE")) return@withLock
        val source = store.find(url)
        if (source == null) {
            if (force) error("没有匹配且启用的解析源")
            return@withLock
        }
        val token = UUID.randomUUID().toString()
        val record = LinkAnalysisEntity(memoId,token,url,source.manifest.id,source.digest,
            localAudio = settings.settings.value.linkAudioLocalTranscription,updatedAt = System.currentTimeMillis())
        if (!repository.queue(record)) return@withLock
        notifications.cancel(LinkSummaryNotificationPolicy.key(memoId))
        val request = OneTimeWorkRequestBuilder<LinkAnalysisWorker>()
            .setInputData(workDataOf("memoId" to memoId, "token" to token))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .addTag("link-analysis").addTag(sourceTag(source.manifest.id)).build()
        try { work.enqueueUniqueWork(workName(memoId),ExistingWorkPolicy.REPLACE,request).await() }
        catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { repository.state(memoId,token,"FAILED","无法安排后台任务，可重试") }
        Unit
    }
    override suspend fun dismissNotification(memoId: Long) {
        notifications.cancel(LinkSummaryNotificationPolicy.key(memoId))
    }
    override suspend fun cancel(memoId: Long) = gate.withLock { cancelLocked(memoId) }
    private suspend fun cancelLocked(id: Long) {
        repository.get(id)?.let { repository.state(id,it.token,"CANCELLED") }
        work.cancelUniqueWork(workName(id))
        notifications.cancel(LinkSummaryNotificationPolicy.key(id))
    }
    override suspend fun cancelAll() = gate.withLock {
        work.cancelAllWorkByTag("link-analysis")
        repository.all().forEach { record ->
            if(record.state!="DONE") repository.state(record.memoId,record.token,"CANCELLED")
            notifications.cancel(LinkSummaryNotificationPolicy.key(record.memoId))
        }
    }
    suspend fun run(id: Long, token: String) {
        val directory = File(context.cacheDir,"link-analysis/$token")
        require(token.matches(Regex("[a-f0-9-]{36}")))
        var stage = "STARTING"
        val startedAt = System.currentTimeMillis()
        fun progress(next: String) {
            stage = next
            Log.i("LinkAnalysis", "memoId=$id trace=$token stage=$stage elapsed_ms=" + (System.currentTimeMillis()-startedAt))
        }
        try {
            progress("STARTING")
            withTimeout(Limits.LINK_TASK_TIMEOUT_MS.toLong()) {
                val record = repository.current(id,token) ?: return@withTimeout
                if(record.state=="DONE") {
                    gate.withLock {
                        if(repository.current(id,token)?.state=="DONE") {
                            val saved = LinkAnalysisRepository.summary(record)
                            val memo = repository.memo(id)
                            if(saved!=null && memo!=null && LinkAnalysisPolicy.enabled(settings.settings.value)) publishCompleted(id,memo.title.ifBlank { saved.title })
                        }
                    }
                    return@withTimeout
                }
                if (!LinkAnalysisPolicy.enabled(settings.settings.value)) { repository.state(id,token,"CANCELLED"); return@withTimeout }
                val pack = store.load(record.sourceId,record.sourceDigest) ?: error("解析源已停用、删除或替换，请重新分析")
                directory.mkdirs()
                progress("EXTRACTING")
                repository.state(id,token,"EXTRACTING")
                val memo = repository.memo(id) ?: return@withTimeout
                val result = LinkScriptRuntime(context).extract(pack,LinkSourceInput(requestId=token,url=record.sourceUrl,shareText=memo.bodyText))
                if (result.status=="error") throw LinkAnalysisFailure(LinkFailureCode.fromSource(result.error?.code))
                Log.i("LinkAnalysis", "memoId=$id trace=$token stage=EXTRACTED status=" + result.status + " text_chars=" + result.body.text.length + " media_count=" + result.media.size)
                check(repository.current(id,token)!=null)
                progress(if (record.localAudio) "TRANSCRIBING" else "PREPARING")
                repository.state(id,token,stage)
                val content = LinkMaterialProcessor(context).prepare(pack,result,directory,record.localAudio,token)
                check(repository.current(id,token)!=null)
                progress("SUMMARIZING")
                repository.state(id,token,"SUMMARIZING")
                val summary = LinkSummaryGenerator().summarize(result,content,settings.settings.value)
                currentCoroutineContext().ensureActive()
                progress("SAVING")
                gate.withLock {
                    if (!LinkAnalysisPolicy.enabled(settings.settings.value) || store.load(record.sourceId,record.sourceDigest)==null) return@withLock
                    val data = LinkSummaryData(result.title,result.author,result.contentType,content.text,summary,content.warnings,
                        pack.manifest.id,pack.manifest.version,System.currentTimeMillis())
                    if (repository.complete(id,token,data)) {
                        val currentMemo = repository.memo(id) ?: return@withLock
                        progress("DONE")
                        publishCompleted(id,currentMemo.title.ifBlank { result.title })
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            Log.w("LinkAnalysis","memoId=$id trace=$token stage=$stage code=TASK_TIMEOUT")
            withContext(NonCancellable) { repository.state(id,token,"FAILED",LinkFailureCode.TASK_TIMEOUT.userMessage) }
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) { repository.state(id,token,"CANCELLED") }
            throw cancelled
        } catch (error: Exception) {
            val failure = LinkAnalysisFailure.describe(error, stage)
            repository.state(id,token,"FAILED",failure.code.userMessage)
            Log.w("LinkAnalysis","memoId=$id trace=$token stage=$stage code=" + failure.code.name + " http_status=" + (failure.statusCode ?: 0) + " error_type=" + error.javaClass.simpleName)
            // Never print source messages, URLs, headers, page text or provider response bodies.
        } finally { withContext(NonCancellable + Dispatchers.IO) { if (directory.canonicalFile.toPath().startsWith(File(context.cacheDir,"link-analysis").canonicalFile.toPath())) directory.deleteRecursively() } }
    }
    private suspend fun publishCompleted(id: Long,title: String) {
        try {
        val request = LinkSummaryNotificationPolicy.request(id,title,settings.settings.value)
        // A process restart after persistence may finish delivery without paying for another AI request.
        val existing = notifications.get(request.key)
        if(existing?.state==NotificationState.POSTED) return
        val created = notifications.create(request)
        val delivered = if(created is NotificationResult.Failure) created else notifications.trigger(NotificationTrigger.ByKey(request.key))
        Log.i("WillDoNotify","link summary memoId=$id posted=" + (delivered is NotificationResult.Success && delivered.state==NotificationState.POSTED))
        } catch(cancelled: CancellationException) { throw cancelled }
        catch(_: Exception) { Log.w("WillDoNotify","link summary delivery failed memoId=$id") }
    }
    companion object {
        fun workName(id: Long) = "link-analysis:$id"
        fun sourceTag(id: String) = "link-source:$id"
    }
}
