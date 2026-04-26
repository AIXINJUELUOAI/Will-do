package com.antgskds.calendarassistant.core.ai.provider

import android.content.Context
import android.graphics.Bitmap
import com.antgskds.calendarassistant.core.ai.AnalysisFailure
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.core.model.RecognitionDraft
import com.antgskds.calendarassistant.data.model.MySettings

object LocalSemanticProvider : SemanticProvider {
    private val notReadyFailure = AnalysisFailure(
        title = "分析失败",
        detail = "本地语义模型未就绪"
    )

    override suspend fun parseUserText(
        text: String,
        settings: MySettings,
        context: Context
    ): AnalysisResult<RecognitionDraft> {
        return AnalysisResult.Failure(notReadyFailure)
    }

    override suspend fun analyzeImage(
        bitmap: Bitmap,
        settings: MySettings,
        context: Context
    ): AnalysisResult<List<RecognitionDraft>> {
        return AnalysisResult.Failure(notReadyFailure)
    }
}
