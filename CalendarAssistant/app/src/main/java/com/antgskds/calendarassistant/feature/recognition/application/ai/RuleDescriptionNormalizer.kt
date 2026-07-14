package com.antgskds.calendarassistant.feature.recognition.application.ai

import com.antgskds.calendarassistant.feature.recognition.domain.model.RecognitionDraft
import com.antgskds.calendarassistant.feature.recognition.domain.rule.RecognitionRuleCatalog
import com.antgskds.calendarassistant.feature.schedule.domain.rule.RuleMatchingEngine

object RuleDescriptionNormalizer {
    fun normalize(events: List<RecognitionDraft>): List<RecognitionDraft> {
        return events.map(::normalize)
    }

    fun normalize(event: RecognitionDraft): RecognitionDraft {
        val tag = resolveTag(event)
        val payload = resolvePayload(event, tag)
        return event.copy(
            tag = tag,
            description = RecognitionRuleCatalog.formatDescription(tag, payload)
        )
    }

    private fun resolveTag(event: RecognitionDraft): String {
        val fallbackTag = RecognitionRuleCatalog.normalizeKnownTag(event.tag)
        val payloadRule = RuleMatchingEngine.resolvePayload(event.description, fallbackTag)?.ruleId
        return RecognitionRuleCatalog.normalizeKnownTag(payloadRule)
            ?: fallbackTag
            ?: RecognitionRuleCatalog.DEFAULT_TAG
    }

    private fun resolvePayload(event: RecognitionDraft, tag: String): String {
        return RuleMatchingEngine.resolvePayload(event.description, tag)?.payload
            ?: event.description.trim()
    }
}
