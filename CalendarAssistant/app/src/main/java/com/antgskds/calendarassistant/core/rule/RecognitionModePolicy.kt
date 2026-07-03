package com.antgskds.calendarassistant.core.rule

import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.RecognitionMode

object RecognitionModePolicy {
    fun mode(settings: MySettings): Int = RecognitionMode.normalize(settings.recognitionMode)

    fun shouldUseAiOnly(settings: MySettings): Boolean = mode(settings) == RecognitionMode.AI_ONLY


    fun shouldTryRegexFirst(settings: MySettings): Boolean {
        return when (mode(settings)) {
            RecognitionMode.REGEX_ONLY,
            RecognitionMode.REGEX_THEN_AI_ON_EMPTY,
            RecognitionMode.REGEX_THEN_AI_REVIEW -> true
            else -> false
        }
    }

    fun shouldFallbackToAiOnEmpty(settings: MySettings): Boolean {
        return mode(settings) == RecognitionMode.REGEX_THEN_AI_ON_EMPTY
    }

    fun shouldReviewRegexResultAfterIngest(settings: MySettings): Boolean {
        return mode(settings) == RecognitionMode.REGEX_THEN_AI_REVIEW
    }
}
