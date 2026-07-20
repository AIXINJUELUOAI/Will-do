package com.antgskds.calendarassistant.feature.recognition.application.node

import android.content.Context
import android.graphics.Bitmap
import com.antgskds.calendarassistant.feature.recognition.application.ai.AnalysisResult
import com.antgskds.calendarassistant.feature.recognition.application.ai.provider.RecognitionProviderFactory
import com.antgskds.calendarassistant.feature.recognition.domain.model.RecognitionDraft
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings

internal object RecognitionMultimodalNode {
    suspend fun analyzeImage(
        bitmap: Bitmap,
        settings: MySettings,
        context: Context
    ): AnalysisResult<List<RecognitionDraft>> {
        return RecognitionProviderFactory.semanticProvider(settings).analyzeImage(bitmap, settings, context)
    }
}
