package com.antgskds.calendarassistant.feature.recognition.application.ai.provider

import android.graphics.Bitmap

object CustomOcrProvider : OcrProvider {
    override suspend fun recognizeText(bitmap: Bitmap): String {
        return MlKitOcrProvider.recognizeText(bitmap)
    }
}
