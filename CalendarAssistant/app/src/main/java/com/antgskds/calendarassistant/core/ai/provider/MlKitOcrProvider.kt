package com.antgskds.calendarassistant.core.ai.provider

import android.graphics.Bitmap
import com.antgskds.calendarassistant.core.ai.RecognitionProcessor

object MlKitOcrProvider : OcrProvider {
    override suspend fun recognizeText(bitmap: Bitmap): String {
        return RecognitionProcessor.recognizeText(bitmap)
    }
}
