package com.antgskds.calendarassistant.core.center

import android.content.Context
import android.graphics.Bitmap
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.core.event.DomainEventBus
import com.antgskds.calendarassistant.core.event.DomainEventType
import com.antgskds.calendarassistant.core.event.EventIdentity
import com.antgskds.calendarassistant.core.event.events.RecognitionCompletedEvent
import com.antgskds.calendarassistant.core.event.events.RecognitionFailedEvent
import com.antgskds.calendarassistant.core.model.RecognitionDraft
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.node.recognition.RecognitionMultimodalNode
import com.antgskds.calendarassistant.data.node.recognition.RecognitionOcrNode
import com.antgskds.calendarassistant.data.node.recognition.RecognitionTextNode
class RecognitionCenter(
    private val domainEventBus: DomainEventBus
) : com.antgskds.calendarassistant.core.operation.RecognitionApi {
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
        val result = RecognitionTextNode.parseUserText(text, settings, context)
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
        val result = RecognitionTextNode.analyzeTextEvents(text, settings, context)
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
        val result = RecognitionMultimodalNode.analyzeImage(bitmap, settings, context)
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

}

private fun String.isLocalModelStatusCode(): Boolean {
    return matches(Regex("[A-Z_]+")) && (contains("ENGINE") || contains("MODEL") || contains("TIMEOUT") || startsWith("INVALID") || this == "EMPTY_EVENTS" || this == "USER_CANCELLED" || this == "FOREGROUND_START_FAILED" || this == "UNKNOWN_ERROR")
}
