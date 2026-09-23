package com.antgskds.calendarassistant.feature.recognition.application.ai.provider

import com.antgskds.calendarassistant.feature.accounting.domain.WechatRedPacketSessionPolicy
import android.content.Context
import android.graphics.Bitmap
import com.antgskds.calendarassistant.feature.recognition.application.ai.AnalysisResult
import com.antgskds.calendarassistant.feature.recognition.domain.model.RecognitionDraft
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings

interface SemanticProvider {
    suspend fun parseUserText(
        text: String,
        settings: MySettings,
        context: Context
    ): AnalysisResult<RecognitionDraft>

    suspend fun analyzeImage(
        bitmap: Bitmap,
        settings: MySettings,
        context: Context,
        redPacketSent: WechatRedPacketSessionPolicy.SentEvidence? = null
    ): AnalysisResult<List<RecognitionDraft>>
}
