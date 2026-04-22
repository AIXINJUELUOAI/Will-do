package com.antgskds.calendarassistant.data.node.recognition

import android.graphics.Bitmap
import com.antgskds.calendarassistant.core.ai.provider.RecognitionProviderFactory

internal object RecognitionOcrNode {
    suspend fun recognizeText(bitmap: Bitmap): String {
        return RecognitionProviderFactory.ocrProvider().recognizeText(bitmap)
    }
}
