package com.antgskds.calendarassistant.feature.recognition.application.ai.provider

import com.antgskds.calendarassistant.feature.accounting.domain.WechatRedPacketSessionPolicy
import com.antgskds.calendarassistant.feature.accounting.domain.WechatIncomingPaymentPolicy
import android.content.Context
import android.graphics.Bitmap
import com.antgskds.calendarassistant.feature.recognition.application.ai.AnalysisResult
import com.antgskds.calendarassistant.feature.recognition.application.ai.RecognitionProcessor
import com.antgskds.calendarassistant.feature.recognition.domain.model.RecognitionDraft
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings

object RemoteSemanticProvider : SemanticProvider {
    override suspend fun parseUserText(
        text: String,
        settings: MySettings,
        context: Context
    ): AnalysisResult<RecognitionDraft> {
        return RecognitionProcessor.parseUserText(text, settings, context)
    }

    override suspend fun analyzeImage(
        bitmap: Bitmap,
        settings: MySettings,
        context: Context,
        redPacketSent: WechatRedPacketSessionPolicy.SentEvidence?,
        wechatIncoming: WechatIncomingPaymentPolicy.Evidence?
    ): AnalysisResult<List<RecognitionDraft>> {
        return RecognitionProcessor.analyzeImage(bitmap, settings, context, redPacketSent, wechatIncoming)
    }
}
