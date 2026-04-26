package com.antgskds.calendarassistant.data.node.recognition

import android.content.Context
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.core.ai.provider.RecognitionProviderFactory
import com.antgskds.calendarassistant.core.model.RecognitionDraft
import com.antgskds.calendarassistant.data.model.MySettings

internal object RecognitionTextNode {
    suspend fun parseUserText(
        text: String,
        settings: MySettings,
        context: Context
    ): AnalysisResult<RecognitionDraft> {
        return RecognitionProviderFactory.semanticProvider(settings).parseUserText(text, settings, context)
    }
}
