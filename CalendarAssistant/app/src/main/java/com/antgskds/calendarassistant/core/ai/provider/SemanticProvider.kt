package com.antgskds.calendarassistant.core.ai.provider

import android.content.Context
import android.graphics.Bitmap
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.core.model.RecognitionDraft
import com.antgskds.calendarassistant.data.model.MySettings

interface SemanticProvider {
    suspend fun parseUserText(
        text: String,
        settings: MySettings,
        context: Context
    ): AnalysisResult<RecognitionDraft>

    suspend fun analyzeImage(
        bitmap: Bitmap,
        settings: MySettings,
        context: Context
    ): AnalysisResult<List<RecognitionDraft>>
}
