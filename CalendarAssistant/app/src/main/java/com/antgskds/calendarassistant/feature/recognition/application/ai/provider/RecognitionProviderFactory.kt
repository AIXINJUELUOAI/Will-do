package com.antgskds.calendarassistant.feature.recognition.application.ai.provider

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings

object RecognitionProviderFactory {
    fun ocrProvider(): OcrProvider {
        return CustomOcrProvider
    }

    fun semanticProvider(settings: MySettings): SemanticProvider {
        return RemoteSemanticProvider
    }
}
