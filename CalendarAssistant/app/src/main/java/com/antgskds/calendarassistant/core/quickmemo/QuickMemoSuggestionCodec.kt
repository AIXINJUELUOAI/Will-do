package com.antgskds.calendarassistant.core.quickmemo

import com.antgskds.calendarassistant.core.model.RecognitionDraft
import com.google.gson.Gson

object QuickMemoSuggestionCodec {
    private val gson = Gson()

    fun encode(draft: RecognitionDraft): String = gson.toJson(draft)

    fun decode(json: String): RecognitionDraft? {
        return runCatching { gson.fromJson(json, RecognitionDraft::class.java) }.getOrNull()
    }
}
