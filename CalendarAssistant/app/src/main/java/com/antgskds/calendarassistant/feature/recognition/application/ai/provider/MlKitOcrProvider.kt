package com.antgskds.calendarassistant.feature.recognition.application.ai.provider

import android.graphics.Bitmap
import com.antgskds.calendarassistant.feature.recognition.application.ai.RecognitionProcessor

object MlKitOcrProvider : OcrProvider {
    override suspend fun recognizeText(bitmap: Bitmap): String {
        return RecognitionProcessor.recognizeText(bitmap)
    }
}
