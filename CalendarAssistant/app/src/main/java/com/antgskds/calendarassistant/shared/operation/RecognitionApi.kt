package com.antgskds.calendarassistant.shared.operation

import android.content.Context
import android.graphics.Bitmap
import com.antgskds.calendarassistant.feature.recognition.application.ai.AnalysisResult
import com.antgskds.calendarassistant.shared.event.EventIdentity
import com.antgskds.calendarassistant.feature.recognition.domain.model.RecognitionDraft
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings

/**
 * 识别链路的统一入口契约。
 *
 * 「入口可以很多（截图/图片/文本/语音/分享…），主流程只能有一条」——所有识别入口都应只依赖本
 * 接口，而不是直接拿 [com.antgskds.calendarassistant.feature.recognition.application.RecognitionOrchestrator] 实现类。
 * Success.data 保留日程草稿，Success.bills 承载同次请求的账单候选；正常账单自动入库，
 * Success.accountingResult 是实际入库汇总，重复跳过，异常暂存；日程入库由 ingestRequested 控制。
 *
 * 由 RecognitionOrchestrator 实现。方法签名与其现有实现一致（纯增量契约，不改行为）。
 */
interface RecognitionApi {
    /** 自动入口仅处理账单；详情/收款汇总必须输出单笔及明确时间（汇总可用统计日期零点），不回填当前时间。 */
    suspend fun analyzeAutomaticAccountingImage(bitmap: Bitmap, settings: MySettings, context: Context,
        sourcePackage: String, traceId: String = EventIdentity.newTraceId(), isDetailPage: Boolean = false): AnalysisResult<List<RecognitionDraft>>

    /** 支付应用 Hook 消息只做本地解析，不自动调用付费模型。 */
    suspend fun analyzeAutomaticPaymentMessage(sourcePackage: String, payload: String, receivedAt: Long,
        settings: MySettings, traceId: String): AnalysisResult<List<RecognitionDraft>>

    /** 文本识别 → 单条日程草稿。 */
    suspend fun parseUserText(
        text: String,
        settings: MySettings,
        context: Context,
        sourceType: String = "text",
        sourceId: String = "manual_input",
        sourceImagePath: String? = null,
        ingestRequested: Boolean = false,
        traceId: String = EventIdentity.newTraceId()
    ): AnalysisResult<RecognitionDraft>

    /** 文本识别 → 多条日程草稿。 */
    suspend fun analyzeTextEvents(
        text: String,
        settings: MySettings,
        context: Context,
        sourceType: String = "text_events",
        sourceId: String = "text_events_input",
        ingestRequested: Boolean = false,
        traceId: String = EventIdentity.newTraceId()
    ): AnalysisResult<List<RecognitionDraft>>

    /** 图像识别（多模态）→ 多条日程草稿。 */
    suspend fun analyzeImage(
        bitmap: Bitmap,
        settings: MySettings,
        context: Context,
        sourceType: String = "image",
        sourceId: String = "image_input",
        sourceImagePath: String? = null,
        ingestRequested: Boolean = false,
        traceId: String = EventIdentity.newTraceId()
    ): AnalysisResult<List<RecognitionDraft>>
}
