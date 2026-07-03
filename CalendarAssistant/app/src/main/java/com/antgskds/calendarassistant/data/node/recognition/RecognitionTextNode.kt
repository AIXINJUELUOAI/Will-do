package com.antgskds.calendarassistant.data.node.recognition

import android.content.Context
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.core.ai.RecognitionProcessor
import com.antgskds.calendarassistant.core.ai.provider.RecognitionProviderFactory
import com.antgskds.calendarassistant.core.model.RecognitionDraft
import com.antgskds.calendarassistant.core.rule.RecognitionModePolicy
import com.antgskds.calendarassistant.data.model.MySettings

internal object RecognitionTextNode {
    suspend fun parseUserText(
        text: String,
        settings: MySettings,
        context: Context
    ): AnalysisResult<RecognitionDraft> {
        if (RecognitionModePolicy.shouldUseAiOnly(settings)) {
            return parseUserTextByAi(text, settings, context)
        }

        val regexResult = RecognitionRegexNode.analyzeTextEvents(text, settings, context)
        if (regexResult is AnalysisResult.Success && regexResult.data.isNotEmpty()) {
            return AnalysisResult.Success(regexResult.data.first())
        }

        return if (RecognitionModePolicy.shouldFallbackToAiOnEmpty(settings)) {
            parseUserTextByAi(text, settings, context)
        } else {
            when (regexResult) {
                is AnalysisResult.Empty -> regexResult
                is AnalysisResult.Failure -> regexResult
                is AnalysisResult.Success -> AnalysisResult.Empty("正则未匹配到明确日程")
            }
        }
    }

    suspend fun analyzeTextEvents(
        text: String,
        settings: MySettings,
        context: Context
    ): AnalysisResult<List<RecognitionDraft>> {
        if (RecognitionModePolicy.shouldUseAiOnly(settings)) {
            return RecognitionProcessor.analyzeTextEvents(text, settings, context)
        }

        val regexResult = RecognitionRegexNode.analyzeTextEvents(text, settings, context)
        if (regexResult is AnalysisResult.Success && regexResult.data.isNotEmpty()) {
            return regexResult
        }

        return if (RecognitionModePolicy.shouldFallbackToAiOnEmpty(settings)) {
            RecognitionProcessor.analyzeTextEvents(text, settings, context)
        } else {
            regexResult
        }
    }

    suspend fun analyzeTextEventsByAi(
        text: String,
        settings: MySettings,
        context: Context
    ): AnalysisResult<List<RecognitionDraft>> {
        return RecognitionProcessor.analyzeTextEvents(text, settings, context)
    }

    private suspend fun parseUserTextByAi(
        text: String,
        settings: MySettings,
        context: Context
    ): AnalysisResult<RecognitionDraft> {
        return RecognitionProviderFactory.semanticProvider(settings).parseUserText(text, settings, context)
    }
}
