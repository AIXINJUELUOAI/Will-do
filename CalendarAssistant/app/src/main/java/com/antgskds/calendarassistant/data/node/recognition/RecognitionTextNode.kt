package com.antgskds.calendarassistant.data.node.recognition

import android.content.Context
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.core.ai.RecognitionProcessor
import com.antgskds.calendarassistant.data.model.CalendarEventData
import com.antgskds.calendarassistant.data.model.MySettings

internal object RecognitionTextNode {
    suspend fun parseUserText(
        text: String,
        settings: MySettings,
        context: Context
    ): AnalysisResult<CalendarEventData> {
        return RecognitionProcessor.parseUserText(text, settings, context)
    }
}
