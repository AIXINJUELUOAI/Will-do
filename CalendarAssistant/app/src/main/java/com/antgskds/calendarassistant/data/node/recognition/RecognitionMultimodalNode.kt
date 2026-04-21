package com.antgskds.calendarassistant.data.node.recognition

import android.content.Context
import android.graphics.Bitmap
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.core.ai.RecognitionProcessor
import com.antgskds.calendarassistant.data.model.CalendarEventData
import com.antgskds.calendarassistant.data.model.MySettings

internal object RecognitionMultimodalNode {
    suspend fun analyzeImage(
        bitmap: Bitmap,
        settings: MySettings,
        context: Context
    ): AnalysisResult<List<CalendarEventData>> {
        return RecognitionProcessor.analyzeImage(bitmap, settings, context)
    }
}
