package com.antgskds.calendarassistant.core.ai.provider

import android.graphics.Bitmap

object CustomOcrProvider : OcrProvider {
    override suspend fun recognizeText(bitmap: Bitmap): String {
        return MlKitOcrProvider.recognizeText(bitmap)
    }
}
