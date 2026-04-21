package com.antgskds.calendarassistant.core.center

import android.content.Context
import android.graphics.Bitmap
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.core.event.DomainEventBus
import com.antgskds.calendarassistant.core.event.DomainEventType
import com.antgskds.calendarassistant.core.event.EventIdentity
import com.antgskds.calendarassistant.core.event.events.RecognitionCompletedEvent
import com.antgskds.calendarassistant.core.event.events.RecognitionFailedEvent
import com.antgskds.calendarassistant.data.model.CalendarEventData
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.node.recognition.RecognitionMultimodalNode
import com.antgskds.calendarassistant.data.node.recognition.RecognitionOcrNode
import com.antgskds.calendarassistant.data.node.recognition.RecognitionTextNode
class RecognitionCenter(
    private val domainEventBus: DomainEventBus
) {
    suspend fun recognizeText(bitmap: Bitmap): String {
        return RecognitionOcrNode.recognizeText(bitmap)
    }

    suspend fun parseUserText(
        text: String,
        settings: MySettings,
        context: Context,
        sourceType: String = "text",
        sourceId: String = "manual_input",
        sourceImagePath: String? = null,
        ingestRequested: Boolean = false,
        traceId: String = EventIdentity.newTraceId()
    ): AnalysisResult<CalendarEventData> {
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
                        ingestRequested = ingestRequested
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
                        errorCode = "EMPTY_RESULT",
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
                        errorCode = "ANALYSIS_FAILURE",
                        retryable = true,
                        message = result.failure.fullMessage()
                    )
                )
            }
        }
        return result
    }

    suspend fun analyzeImage(
        bitmap: Bitmap,
        settings: MySettings,
        context: Context,
        sourceType: String = "image",
        sourceId: String = "image_input",
        sourceImagePath: String? = null,
        ingestRequested: Boolean = false,
        traceId: String = EventIdentity.newTraceId()
    ): AnalysisResult<List<CalendarEventData>> {
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
                        errorCode = "EMPTY_RESULT",
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
                        errorCode = "ANALYSIS_FAILURE",
                        retryable = true,
                        message = result.failure.fullMessage()
                    )
                )
            }
        }
        return result
    }

}
