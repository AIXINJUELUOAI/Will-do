package com.antgskds.calendarassistant.core.operation

import android.content.Context
import android.graphics.Bitmap
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.core.event.EventIdentity
import com.antgskds.calendarassistant.core.model.RecognitionDraft
import com.antgskds.calendarassistant.data.model.MySettings

/**
 * 识别链路的统一入口契约。
 *
 * 「入口可以很多（截图/图片/文本/语音/分享…），主流程只能有一条」——所有识别入口都应只依赖本
 * 接口，而不是直接拿 [com.antgskds.calendarassistant.core.center.RecognitionCenter] 实现类。
 * 输出统一为 [AnalysisResult]<[RecognitionDraft]>，后续入库由 [IngestCommandApi] 接手。
 *
 * 由 RecognitionCenter 实现。方法签名与其现有实现一致（纯增量契约，不改行为）。
 */
interface RecognitionApi {

    /** 纯 OCR：位图 → 文本（不产出草稿）。 */
    suspend fun recognizeText(bitmap: Bitmap): String

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

    /** 图像识别（OCR/多模态）→ 多条日程草稿。 */
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
