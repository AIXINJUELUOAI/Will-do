package com.antgskds.calendarassistant.data.node.recognition

import android.graphics.Bitmap
import com.antgskds.calendarassistant.core.ai.RecognitionProcessor

internal object RecognitionOcrNode {
    suspend fun recognizeText(bitmap: Bitmap): String {
        return RecognitionProcessor.recognizeText(bitmap)
    }
}
