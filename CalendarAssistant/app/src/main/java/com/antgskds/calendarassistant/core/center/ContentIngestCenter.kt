package com.antgskds.calendarassistant.core.center

import android.util.Log
import com.antgskds.calendarassistant.core.event.DomainEventBus
import com.antgskds.calendarassistant.core.event.DomainEventType
import com.antgskds.calendarassistant.core.event.EventIdentity
import com.antgskds.calendarassistant.core.event.events.IngestFailedEvent
import com.antgskds.calendarassistant.core.event.events.IngestSucceededEvent
import com.antgskds.calendarassistant.core.event.events.RecognitionCompletedEvent
import com.antgskds.calendarassistant.core.rule.RegexAiReviewCoordinator
import com.antgskds.calendarassistant.core.operation.IngestCommandApi
import com.antgskds.calendarassistant.core.model.RecognitionDraft
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.core.rule.RecognitionModePolicy
import com.antgskds.calendarassistant.data.model.MySettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
class ContentIngestCenter(
    private val importCenter: ImportCenter,
    private val domainEventBus: DomainEventBus,
    private val appScope: CoroutineScope,
    private val notificationCenter: NotificationCenter? = null,
    private val settingsProvider: (() -> MySettings)? = null,
    private val regexAiReviewCoordinator: RegexAiReviewCoordinator? = null,
) : IngestCommandApi {
    private sealed interface IngestTask {
        val traceId: String
        val sourceType: String
        val sourceId: String
    }

    private data class SmsIngestTask(
        val eventData: RecognitionDraft,
        override val traceId: String,
        override val sourceType: String,
        override val sourceId: String,
        val result: CompletableDeferred<Event?>
    ) : IngestTask

    private data class RecognizedIngestTask(
        val events: List<RecognitionDraft>,
        val sourceImagePath: String?,
        val regexReviewContext: RegexAiReviewCoordinator.ReviewContext?,
        override val traceId: String,
        override val sourceType: String,
        override val sourceId: String,
        val result: CompletableDeferred<List<Event>>? = null
    ) : IngestTask

    private val ingestChannel = Channel<IngestTask>(capacity = 64)

    init {
        appScope.launch {
            for (task in ingestChannel) {
                try {
                    when (task) {
                        is SmsIngestTask -> processSmsTask(task)
                        is RecognizedIngestTask -> processRecognizedTask(task)
                    }
                } catch (e: Exception) {
                    Log.e("ContentIngestCenter", "处理入库任务失败", e)
                    when (task) {
                        is SmsIngestTask -> {
                            task.result.completeExceptionally(e)
                            emitIngestFailed(
                                traceId = task.traceId,
                                sourceType = task.sourceType,
                                sourceId = task.sourceId,
                                stage = "sms_ingest",
                                errorCode = "INGEST_EXCEPTION",
                                retryable = true,
                                message = e.message ?: "入库失败"
                            )
                        }

                        is RecognizedIngestTask -> {
                            task.result?.completeExceptionally(e)
                            emitIngestFailed(
                                traceId = task.traceId,
                                sourceType = task.sourceType,
                                sourceId = task.sourceId,
                                stage = "recognized_ingest",
                                errorCode = "INGEST_EXCEPTION",
                                retryable = true,
                                message = e.message ?: "入库失败"
                            )
                        }
                    }
                }
            }
        }

        appScope.launch {
            domainEventBus
                .eventsOfType<RecognitionCompletedEvent>(DomainEventType.RECOGNITION_COMPLETED)
                .collect { event ->
                    val payload = event.payload
                    if (!payload.ingestRequested || payload.candidates.isEmpty()) {
                        Log.d(
                            "ContentIngestCenter",
                            "跳过识别结果入库: ingestRequested=${payload.ingestRequested}, candidates=${payload.candidates.size}"
                        )
                        return@collect
                    }

                    Log.d(
                        "ContentIngestCenter",
                        "收到识别结果准备入库: candidates=${payload.candidates.size}, traceId=${event.traceId}"
                    )

                    ingestChannel.send(
                        RecognizedIngestTask(
                            events = payload.candidates,
                            sourceImagePath = payload.sourceImagePath,
                            regexReviewContext = buildRegexReviewContext(payload),
                            traceId = event.traceId,
                            sourceType = payload.sourceType,
                            sourceId = payload.sourceId,
                            result = null
                        )
                    )
                }
        }
    }

    override suspend fun ingestSmsPickup(eventData: RecognitionDraft): Event? {
        val deferred = CompletableDeferred<Event?>()
        val traceId = EventIdentity.newTraceId()
        ingestChannel.send(
            SmsIngestTask(
                eventData = eventData,
                traceId = traceId,
                sourceType = "sms",
                sourceId = eventData.description.ifBlank { "sms_input" },
                result = deferred
            )
        )
        return deferred.await()
    }

    override suspend fun ingestInstantCode(eventData: RecognitionDraft, sourceType: String): Event? {
        val deferred = CompletableDeferred<Event?>()
        val traceId = EventIdentity.newTraceId(sourceType)
        ingestChannel.send(
            SmsIngestTask(
                eventData = eventData,
                traceId = traceId,
                sourceType = sourceType,
                sourceId = eventData.description.ifBlank { "instant_code_input" },
                result = deferred
            )
        )
        return deferred.await()
    }

    override suspend fun ingestRecognizedEvents(
        events: List<RecognitionDraft>,
        sourceImagePath: String?
    ): List<Event> {
        val deferred = CompletableDeferred<List<Event>>()
        val traceId = EventIdentity.newTraceId()
        ingestChannel.send(
            RecognizedIngestTask(
                events = events,
                sourceImagePath = sourceImagePath,
                regexReviewContext = null,
                traceId = traceId,
                sourceType = "recognized",
                sourceId = sourceImagePath ?: "recognized_input",
                result = deferred
            )
        )
        return deferred.await()
    }

    private suspend fun processSmsTask(task: SmsIngestTask) {
        val created = importCenter.ingestSmsPickup(task.eventData)
        task.result.complete(created)

        if (created != null) {
            emitIngestSucceeded(
                traceId = task.traceId,
                sourceType = task.sourceType,
                sourceId = task.sourceId,
                createdEventIds = listOf(created.id?.toString() ?: ""),
                dedupedCount = 0,
                createdCount = 1
            )
        } else {
            emitIngestFailed(
                traceId = task.traceId,
                sourceType = task.sourceType,
                sourceId = task.sourceId,
                stage = "sms_ingest",
                errorCode = "DUPLICATE_OR_SKIPPED",
                retryable = false,
                message = "短信内容已存在或不满足入库条件"
            )
        }
    }

    private suspend fun processRecognizedTask(task: RecognizedIngestTask) {
        Log.d(
            "ContentIngestCenter",
            "开始入库识别结果: events=${task.events.size}, sourceType=${task.sourceType}, traceId=${task.traceId}"
        )
        val created = importCenter.ingestRecognizedEvents(task.events, task.sourceImagePath)
        task.result?.complete(created)

        val dedupedCount = (task.events.size - created.size).coerceAtLeast(0)
        if (created.isNotEmpty()) {
            Log.d(
                "ContentIngestCenter",
                "识别结果入库成功: created=${created.size}, deduped=$dedupedCount, traceId=${task.traceId}"
            )
            emitIngestSucceeded(
                traceId = task.traceId,
                sourceType = task.sourceType,
                sourceId = task.sourceId,
                createdEventIds = created.mapNotNull { it.id?.toString() },
                dedupedCount = dedupedCount,
                createdCount = created.size
            )
            maybeStartRegexAiReview(task, created)
            notificationCenter?.showCreatedEventResultNotifications(task.sourceType, created)
        } else {
            Log.d(
                "ContentIngestCenter",
                "识别结果未入库: candidates=${task.events.size}, traceId=${task.traceId}"
            )
            emitIngestFailed(
                traceId = task.traceId,
                sourceType = task.sourceType,
                sourceId = task.sourceId,
                stage = "recognized_ingest",
                errorCode = "EMPTY_OR_DUPLICATE",
                retryable = false,
                message = "没有可入库的新事件"
            )
        }
    }

    private fun buildRegexReviewContext(payload: RecognitionCompletedEvent): RegexAiReviewCoordinator.ReviewContext? {
        val settings = settingsProvider?.invoke() ?: return null
        if (!RecognitionModePolicy.shouldReviewRegexResultAfterIngest(settings)) return null
        val rawText = payload.rawText?.trim().orEmpty()
        if (rawText.isBlank() || payload.candidates.size != 1) return null
        return RegexAiReviewCoordinator.ReviewContext(
            rawText = rawText,
            regexDraft = payload.candidates.single(),
            settings = settings,
        )
    }

    private fun maybeStartRegexAiReview(task: RecognizedIngestTask, created: List<Event>) {
        val reviewContext = task.regexReviewContext ?: return
        val eventId = created.singleOrNull()?.id ?: return
        regexAiReviewCoordinator?.reviewAfterIngest(
            traceId = task.traceId,
            eventId = eventId,
            context = reviewContext,
        )
    }

    private suspend fun emitIngestSucceeded(
        traceId: String,
        sourceType: String,
        sourceId: String,
        createdEventIds: List<String>,
        dedupedCount: Int,
        createdCount: Int
    ) {
        domainEventBus.emit(
            eventType = DomainEventType.INGEST_SUCCEEDED,
            traceId = traceId,
            source = "content_ingest_center",
            entityKey = EventIdentity.entityKey(sourceType, sourceId, createdCount.toString()),
            payload = IngestSucceededEvent(
                sourceType = sourceType,
                sourceId = sourceId,
                createdEventIds = createdEventIds,
                dedupedCount = dedupedCount,
                createdCount = createdCount
            )
        )
    }

    private suspend fun emitIngestFailed(
        traceId: String,
        sourceType: String,
        sourceId: String,
        stage: String,
        errorCode: String,
        retryable: Boolean,
        message: String
    ) {
        domainEventBus.emit(
            eventType = DomainEventType.INGEST_FAILED,
            traceId = traceId,
            source = "content_ingest_center",
            entityKey = EventIdentity.entityKey(sourceType, sourceId, errorCode),
            payload = IngestFailedEvent(
                sourceType = sourceType,
                sourceId = sourceId,
                stage = stage,
                errorCode = errorCode,
                retryable = retryable,
                message = message
            )
        )
    }
}
