package com.antgskds.calendarassistant.feature.recognition.application

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.ensureActive
import com.antgskds.calendarassistant.shared.util.AppLogger as Log
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
import com.antgskds.calendarassistant.feature.recognition.application.node.RecognitionTextNode
class RecognitionOrchestrator(
    private val domainEventBus: DomainEventBus,
    private val ingestProvider: () -> com.antgskds.calendarassistant.shared.operation.IngestCommandApi,
    private val notificationApi: com.antgskds.calendarassistant.feature.notification.api.NotificationApi,
    private val capsuleProvider: () -> com.antgskds.calendarassistant.shared.operation.CapsuleCommandApi,
    private val automaticAccountingEnabled: () -> Boolean,
) : com.antgskds.calendarassistant.shared.operation.RecognitionApi {
    override suspend fun analyzeAutomaticAccountingImage(bitmap: Bitmap, settings: MySettings, context: Context,
        sourcePackage: String, traceId: String, isDetailPage: Boolean): AnalysisResult<List<RecognitionDraft>> {
        if (!automaticAccountingEnabled() || !com.antgskds.calendarassistant.feature.accounting.domain.AutomaticAccountingPolicy.supports(sourcePackage))
            return AnalysisResult.Empty("自动记账已关闭或来源不支持")
        val result = RecognitionMultimodalNode.analyzeImage(bitmap, settings, context)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (!automaticAccountingEnabled()) return AnalysisResult.Empty("自动记账已关闭")
        if (isDetailPage && result is AnalysisResult.Success &&
            !com.antgskds.calendarassistant.feature.accounting.domain.AutomaticAccountingPolicy.acceptsDetailResult(result.bills, result.billIssues)) {
            return AnalysisResult.Empty("详情页未识别到时间明确的单笔已完成交易，暂不入库")
        }
        // 识别结果中的日程不参与自动记账；支付完成现场缺时间才使用本次识别时间。
        val billsOnly = if (result is AnalysisResult.Success) result.copy(data = emptyList()) else result
        return ingestImageBills(billsOnly, bitmap, context, "accounting.accessibility", sourcePackage, traceId,
            !isDetailPage, settings.isLiveCapsuleEnabled)
    }

    override suspend fun analyzeAutomaticPaymentMessage(sourcePackage: String, payload: String, receivedAt: Long,
        settings: MySettings, traceId: String): AnalysisResult<List<RecognitionDraft>> {
        if (!automaticAccountingEnabled()) return AnalysisResult.Empty("自动记账已关闭")
        val bills = com.antgskds.calendarassistant.feature.accounting.domain.PaymentMessageParser.parse(sourcePackage, payload, receivedAt)
        if (bills.isEmpty()) return AnalysisResult.Empty("未匹配已完成支付消息")
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        if (!automaticAccountingEnabled()) return AnalysisResult.Empty("自动记账已关闭")
        return ingestBills(AnalysisResult.Success(emptyList(), bills = bills), "accounting.xposed", sourcePackage,
            traceId, false, settings.isLiveCapsuleEnabled)
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
                        errorCode = result.failure.errorCode ?: result.failure.detail.takeIf { it.isLocalModelStatusCode() } ?: "ANALYSIS_FAILURE",
                        retryable = result.failure.retryable,
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
        val result = ingestBills(RecognitionTextNode.analyzeTextEvents(text, settings, context), sourceType, sourceId, traceId, true, settings.isLiveCapsuleEnabled)
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
                        errorCode = result.failure.errorCode ?: result.failure.detail.takeIf { it.isLocalModelStatusCode() } ?: "ANALYSIS_FAILURE",
                        retryable = result.failure.retryable,
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
                "multimodal=true ingest=$ingestRequested"
        )
        val result = ingestImageBills(RecognitionMultimodalNode.analyzeImage(bitmap, settings, context), bitmap, context,
            sourceType, sourceId, traceId, false, settings.isLiveCapsuleEnabled)
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
                        errorCode = result.failure.errorCode ?: result.failure.detail.takeIf { it.isLocalModelStatusCode() } ?: "ANALYSIS_FAILURE",
                        retryable = result.failure.retryable,
                        message = result.failure.fullMessage()
                    )
                )
            }
        }
        return result
    }

    /** 只为识别到账单的图片留存独立副本，日程附件删除不会影响账单原图。 */
    private suspend fun ingestImageBills(
        result: AnalysisResult<List<RecognitionDraft>>, bitmap: Bitmap, context: Context,
        sourceType: String, sourceId: String, traceId: String, useCurrentTimeForMissing: Boolean, liveEnabled: Boolean,
    ): AnalysisResult<List<RecognitionDraft>> {
        if (result !is AnalysisResult.Success || result.bills.isEmpty()) return result
        val image = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val directory = java.io.File(context.filesDir, "accounting_images").apply { mkdirs() }
            java.io.File(directory, "${java.util.UUID.randomUUID()}.jpg").also { file ->
                try {
                    file.outputStream().use { output -> check(bitmap.compress(Bitmap.CompressFormat.JPEG, 80, output)) }
                } catch (e: Exception) { file.delete(); throw e }
            }
        }
        val outcome = ingestBills(result, sourceType, sourceId, traceId, useCurrentTimeForMissing, liveEnabled, image.absolutePath)
        val saved = (outcome as? AnalysisResult.Success)?.accountingResult
        if (saved != null && saved.saved.isEmpty() && saved.pending == 0 && saved.suspectedDuplicates == 0) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { image.delete() }
        }
        return outcome
    }

    private suspend fun ingestBills(
        result: AnalysisResult<List<RecognitionDraft>>, sourceType: String, sourceId: String, traceId: String,
        useCurrentTimeForMissing: Boolean, liveEnabled: Boolean, sourceImagePath: String? = null,
    ): AnalysisResult<List<RecognitionDraft>> {
        if (result !is AnalysisResult.Success || result.bills.isEmpty()) return result
        val drafts = result.bills.mapIndexed { index, draft ->
            draft.copy(id = java.util.UUID.nameUUIDFromBytes("$traceId:bill:$index".toByteArray()).toString(),
                sourceType = sourceType, sourceId = sourceId, sourceImagePath = sourceImagePath)
        }
        val accounting = try {
            ingestProvider().ingestRecognizedBills(drafts, useCurrentTimeForMissing)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(RECOGNITION_LOG_TAG, "账单入库失败", e)
            if (result.data.isEmpty()) return AnalysisResult.Failure(
                com.antgskds.calendarassistant.feature.recognition.application.ai.AnalysisFailure("账单保存失败", "本次账单未能入库，请重试"))
            return result.copy(bills = emptyList(), billIssues = result.billIssues + "账单未能入库，请重试；日程继续保存")
        }
        // 事务完成后才展示成功金额；胶囊与普通通知均使用同一份实际入库汇总。
        try {
            val display = com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.AccountingRecognitionDisplay.create(accounting)
            if (liveEnabled) {
                capsuleProvider().showAccountingResult(display)
            } else {
            val key = com.antgskds.calendarassistant.feature.notification.model.NotificationKey("accounting:recognition")
            notificationApi.create(com.antgskds.calendarassistant.feature.notification.model.NotificationRequest(
                key = key,
                kind = com.antgskds.calendarassistant.feature.notification.model.NotificationKind.RECOGNITION_STATUS,
                route = com.antgskds.calendarassistant.feature.notification.model.NotificationRoute.NORMAL,
                display = com.antgskds.calendarassistant.feature.notification.model.NotificationDisplaySnapshot(
                    shortText = display.shortText, primaryText = display.primaryText, secondaryText = display.expandedText,
                    expandedText = display.expandedText,
                ),
                tapTarget = com.antgskds.calendarassistant.feature.notification.model.NotificationTapTarget(
                    com.antgskds.calendarassistant.feature.notification.model.NotificationTapTargetType.APP_HOME,
                    mapOf("open_accounting" to "true")),
                source = "accounting_recognition",
                behavior = com.antgskds.calendarassistant.feature.notification.model.NotificationBehavior(
                    onlyAlertOnce = false, priority = com.antgskds.calendarassistant.feature.notification.model.NotificationPriority.HIGH),
            ))
            notificationApi.trigger(com.antgskds.calendarassistant.feature.notification.model.NotificationTrigger.ByKey(key,
                com.antgskds.calendarassistant.feature.notification.model.NotificationTriggerReason.USER_ACTION))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.w(RECOGNITION_LOG_TAG, "账单处理完成，结果反馈发布失败", e)
        }
        return result.copy(bills = drafts, accountingResult = accounting)
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
