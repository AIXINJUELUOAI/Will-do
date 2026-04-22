package com.antgskds.calendarassistant.core.ai.provider

import android.graphics.Bitmap

interface OcrProvider {
    suspend fun recognizeText(bitmap: Bitmap): String
}
