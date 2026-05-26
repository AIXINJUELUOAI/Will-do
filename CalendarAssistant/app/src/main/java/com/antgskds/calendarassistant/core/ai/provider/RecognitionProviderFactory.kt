package com.antgskds.calendarassistant.core.ai.provider

import com.antgskds.calendarassistant.data.model.MySettings

object RecognitionProviderFactory {
    fun ocrProvider(): OcrProvider {
        return CustomOcrProvider
    }

    fun semanticProvider(settings: MySettings): SemanticProvider {
        return if (settings.isLocalSemanticEnabled) {
            LocalSemanticProvider
        } else {
            RemoteSemanticProvider
        }
    }
}
