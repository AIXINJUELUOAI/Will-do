package com.antgskds.calendarassistant.feature.recognition.application.node

import android.content.Context
import com.antgskds.calendarassistant.feature.recognition.application.ai.AnalysisResult
import com.antgskds.calendarassistant.feature.recognition.data.preferences.RegexScheduleRulePrefs
import com.antgskds.calendarassistant.feature.recognition.domain.model.RecognitionDraft
import com.antgskds.calendarassistant.feature.recognition.domain.rule.RegexScheduleRecognizer
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings

internal object RecognitionRegexNode {
    fun analyzeTextEvents(
        text: String,
        settings: MySettings,
        context: Context,
    ): AnalysisResult<List<RecognitionDraft>> {
        val rules = RegexScheduleRulePrefs.loadRules(context)
        val results = RegexScheduleRecognizer.analyze(
            text = text,
            rules = rules,
            defaultDurationMinutes = settings.defaultEventDurationMinutes,
        )
        return if (results.isNotEmpty()) {
            AnalysisResult.Success(results.map { it.draft })
        } else {
            AnalysisResult.Empty("正则未匹配到明确日程")
        }
    }
}
