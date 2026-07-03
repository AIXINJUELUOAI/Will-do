package com.antgskds.calendarassistant.data.node.recognition

import android.content.Context
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.core.model.RecognitionDraft
import com.antgskds.calendarassistant.core.rule.RegexScheduleRecognizer
import com.antgskds.calendarassistant.core.rule.RegexScheduleRulePrefs
import com.antgskds.calendarassistant.data.model.MySettings

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
