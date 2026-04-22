package com.antgskds.calendarassistant.core.ai.provider

import android.content.Context
import android.graphics.Bitmap
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.core.ai.RecognitionProcessor
import com.antgskds.calendarassistant.data.model.CalendarEventData
import com.antgskds.calendarassistant.data.model.MySettings

object RemoteSemanticProvider : SemanticProvider {
    override suspend fun parseUserText(
        text: String,
        settings: MySettings,
        context: Context
    ): AnalysisResult<CalendarEventData> {
        return RecognitionProcessor.parseUserText(text, settings, context)
    }

    override suspend fun analyzeImage(
        bitmap: Bitmap,
        settings: MySettings,
        context: Context
    ): AnalysisResult<List<CalendarEventData>> {
        return RecognitionProcessor.analyzeImage(bitmap, settings, context)
    }
}
