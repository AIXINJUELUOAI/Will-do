package com.antgskds.calendarassistant.feature.recognition.application

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.antgskds.calendarassistant.feature.recognition.application.ai.AnalysisResult
import com.antgskds.calendarassistant.feature.recognition.application.ai.isRecognitionConfigReady
import com.antgskds.calendarassistant.feature.recognition.application.ai.isTextRecognitionConfigReady
import com.antgskds.calendarassistant.shared.event.DomainEventBus
import com.antgskds.calendarassistant.shared.event.DomainEventType
import com.antgskds.calendarassistant.shared.event.EventIdentity
import com.antgskds.calendarassistant.shared.event.events.RecognitionCompletedEvent
import com.antgskds.calendarassistant.shared.event.events.RecognitionFailedEvent
import com.antgskds.calendarassistant.feature.recognition.domain.model.RecognitionDraft
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.recognition.application.node.RecognitionMultimodalNode
import com.antgskds.calendarassistant.feature.recognition.application.node.RecognitionOcrNode
import com.antgskds.calendarassistant.feature.recognition.application.node.RecognitionTextNode
class RecognitionOrchestrator(
    private val domainEventBus: DomainEventBus
) : com.antgskds.calendarassistant.shared.operation.RecognitionApi {
    override suspend fun recognizeText(bitmap: Bitmap): String {
        return RecognitionOcrNode.recognizeText(bitmap)
    }

    override suspend fun parseUserText(
        text: String,
        settings: MySettings,
        context: Context,
        sourceType: String,
        sourceId: String,
        sourceImagePath: String?,
        ingestRequested: Boolean,
        traceId: String
    ): AnalysisResult<RecognitionDraft> {
        Log.i(
            RECOGNITION_LOG_TAG,
            "parseUserText start trace=$traceId source=$sourceType/$sourceId " +
                "textLength=${text.length} configReady=${settings.isTextRecognitionConfigReady()} " +
                "mode=${settings.recognitionMode} ingest=$ingestRequested"
        )
        val result = RecognitionTextNode.parseUserText(text, settings, context)
        logResult("parseUserText", traceId, sourceType, sourceId, result)
        when (result) {
            is AnalysisResult.Success -> {
                domainEventBus.emit(
                    eventType = DomainEventType.RECOGNITION_COMPLETED,
                    traceId = traceId,
                    source = "recognition_center",
                    entityKey = EventIdentity.entityKey(sourceType, sourceId, text),
                    payload = RecognitionCompletedEvent(
                        sourceType = sourceType,
                        sourceId = sourceId,
                        candidates = listOf(result.data),
                        sourceImagePath = sourceImagePath,
                        ingestRequested = ingestRequested,
                        rawText = text,
                    )
                )
            }

            is AnalysisResult.Empty -> {
                domainEventBus.emit(
                    eventType = DomainEventType.RECOGNITION_FAILED,
                    traceId = traceId,
                    source = "recognition_center",
                    entityKey = EventIdentity.entityKey(sourceType, sourceId, text),
                    payload = RecognitionFailedEvent(
                        sourceType = sourceType,
                        sourceId = sourceId,
                        errorCode = result.message.takeIf { it.isLocalModelStatusCode() } ?: "EMPTY_RESULT",
                        retryable = false,
                        message = result.message
                    )
                )
            }

            is AnalysisResult.Failure -> {
                domainEventBus.emit(
                    eventType = DomainEventType.RECOGNITION_FAILED,
                    traceId = traceId,
                    source = "recognition_center",
                    entityKey = EventIdentity.entityKey(sourceType, sourceId, text),
                    payload = RecognitionFailedEvent(
                        sourceType = sourceType,
                        sourceId = sourceId,
                        errorCode = result.failure.detail.takeIf { it.isLocalModelStatusCode() } ?: "ANALYSIS_FAILURE",
                        retryable = true,
                        message = result.failure.fullMessage()
                    )
                )
            }
        }
        return result
    }

    override suspend fun analyzeTextEvents(
        text: String,
        settings: MySettings,
        context: Context,
        sourceType: String,
        sourceId: String,
        ingestRequested: Boolean,
        traceId: String
    ): AnalysisResult<List<RecognitionDraft>> {
        Log.i(
            RECOGNITION_LOG_TAG,
            "analyzeTextEvents start trace=$traceId source=$sourceType/$sourceId " +
                "textLength=${text.length} configReady=${settings.isTextRecognitionConfigReady()} " +
                "mode=${settings.recognitionMode} ingest=$ingestRequested"
        )
        val result = RecognitionTextNode.analyzeTextEvents(text, settings, context)
        logResult("analyzeTextEvents", traceId, sourceType, sourceId, result)
        when (result) {
            is AnalysisResult.Success -> {
                domainEventBus.emit(
                    eventType = DomainEventType.RECOGNITION_COMPLETED,
                    traceId = traceId,
                    source = "recognition_center",
                    entityKey = EventIdentity.entityKey(sourceType, sourceId, text),
                    payload = RecognitionCompletedEvent(
                        sourceType = sourceType,
                        sourceId = sourceId,
                        candidates = result.data,
                        sourceImagePath = null,
                        ingestRequested = ingestRequested,
                        rawText = text,
                    )
                )
            }
            is AnalysisResult.Empty -> Unit
            is AnalysisResult.Failure -> {
                domainEventBus.emit(
                    eventType = DomainEventType.RECOGNITION_FAILED,
                    traceId = traceId,
                    source = "recognition_center",
                    entityKey = EventIdentity.entityKey(sourceType, sourceId, text),
                    payload = RecognitionFailedEvent(
                        sourceType = sourceType,
                        sourceId = sourceId,
                        errorCode = result.failure.detail.takeIf { it.isLocalModelStatusCode() } ?: "ANALYSIS_FAILURE",
                        retryable = true,
                        message = result.failure.fullMessage()
                    )
                )
            }
        }
        return result
    }

    override suspend fun analyzeImage(
        bitmap: Bitmap,
        settings: MySettings,
        context: Context,
        sourceType: String,
        sourceId: String,
        sourceImagePath: String?,
        ingestRequested: Boolean,
        traceId: String
    ): AnalysisResult<List<RecognitionDraft>> {
        Log.i(
            RECOGNITION_LOG_TAG,
            "analyzeImage start trace=$traceId source=$sourceType/$sourceId " +
                "size=${bitmap.width}x${bitmap.height} configReady=${settings.isRecognitionConfigReady()} " +
                "multimodal=${settings.useMultimodalAi} ingest=$ingestRequested"
        )
        val result = RecognitionMultimodalNode.analyzeImage(bitmap, settings, context)
        logResult("analyzeImage", traceId, sourceType, sourceId, result)
        when (result) {
            is AnalysisResult.Success -> {
                domainEventBus.emit(
                    eventType = DomainEventType.RECOGNITION_COMPLETED,
                    traceId = traceId,
                    source = "recognition_center",
                    entityKey = EventIdentity.entityKey(sourceType, sourceId, "${bitmap.width}x${bitmap.height}"),
                    payload = RecognitionCompletedEvent(
                        sourceType = sourceType,
                        sourceId = sourceId,
                        candidates = result.data,
                        sourceImagePath = sourceImagePath,
                        ingestRequested = ingestRequested
                    )
                )
            }

            is AnalysisResult.Empty -> {
                domainEventBus.emit(
                    eventType = DomainEventType.RECOGNITION_FAILED,
                    traceId = traceId,
                    source = "recognition_center",
                    entityKey = EventIdentity.entityKey(sourceType, sourceId, "${bitmap.width}x${bitmap.height}"),
                    payload = RecognitionFailedEvent(
                        sourceType = sourceType,
                        sourceId = sourceId,
                        errorCode = result.message.takeIf { it.isLocalModelStatusCode() } ?: "EMPTY_RESULT",
                        retryable = false,
                        message = result.message
                    )
                )
            }

            is AnalysisResult.Failure -> {
                domainEventBus.emit(
                    eventType = DomainEventType.RECOGNITION_FAILED,
                    traceId = traceId,
                    source = "recognition_center",
                    entityKey = EventIdentity.entityKey(sourceType, sourceId, "${bitmap.width}x${bitmap.height}"),
                    payload = RecognitionFailedEvent(
                        sourceType = sourceType,
                        sourceId = sourceId,
                        errorCode = result.failure.detail.takeIf { it.isLocalModelStatusCode() } ?: "ANALYSIS_FAILURE",
                        retryable = true,
                        message = result.failure.fullMessage()
                    )
                )
            }
        }
        return result
    }

    private fun logResult(
        operation: String,
        traceId: String,
        sourceType: String,
        sourceId: String,
        result: AnalysisResult<*>
    ) {
        val prefix = "$operation result trace=$traceId source=$sourceType/$sourceId"
        when (result) {
            is AnalysisResult.Success -> {
                val count = (result.data as? Collection<*>)?.size ?: 1
                Log.i(RECOGNITION_LOG_TAG, "$prefix success count=$count")
            }
            is AnalysisResult.Empty -> Log.w(
                RECOGNITION_LOG_TAG,
                "$prefix empty message=${result.message}"
            )
            is AnalysisResult.Failure -> Log.e(
                RECOGNITION_LOG_TAG,
                "$prefix failure title=${result.failure.title} detail=${result.failure.detail}"
            )
        }
    }

}

private const val RECOGNITION_LOG_TAG = "WillDoRecognition"

private fun String.isLocalModelStatusCode(): Boolean {
    return matches(Regex("[A-Z_]+")) && (contains("ENGINE") || contains("MODEL") || contains("TIMEOUT") || startsWith("INVALID") || this == "EMPTY_EVENTS" || this == "USER_CANCELLED" || this == "FOREGROUND_START_FAILED" || this == "UNKNOWN_ERROR")
}
