package com.antgskds.calendarassistant.feature.recognition.application.ai.provider

import android.graphics.Bitmap

interface OcrProvider {
    suspend fun recognizeText(bitmap: Bitmap): String
}
