package com.antgskds.calendarassistant.feature.recognition.application.node

import android.graphics.Bitmap
import com.antgskds.calendarassistant.feature.recognition.application.ai.provider.RecognitionProviderFactory

internal object RecognitionOcrNode {
    suspend fun recognizeText(bitmap: Bitmap): String {
        return RecognitionProviderFactory.ocrProvider().recognizeText(bitmap)
    }
}
