package com.antgskds.calendarassistant.core.sms

import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.calendar.models.inferEventTagFromDescription
import com.antgskds.calendarassistant.core.model.RecognitionDraft
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine

object SmsPickupFingerprint {
    fun fromDraft(draft: RecognitionDraft): String? {
        return build(
            tagRaw = draft.tag,
            title = draft.title,
            description = draft.description
        )
    }

    fun fromEvent(event: Event): String? {
        return build(
            tagRaw = event.tag,
            title = event.title,
            description = event.description
        )
    }

    private fun build(tagRaw: String, title: String, description: String): String? {
        val tag = inferEventTagFromDescription(description, tagRaw).lowercase()
        if (tag !in CODE_TAGS) return null

        val code = extractCodeFromDescription(description)
            ?: extractCodeFromTitle(title)
            ?: return null
        return normalizeCodes(code)?.let { "$tag:$it" }
    }

    private fun extractCodeFromDescription(description: String): String? {
        val payload = RuleMatchingEngine.resolvePayload(description, null)?.payload
            ?: description
        return payload.substringBefore('|').trim().ifBlank { null }
    }

    private fun extractCodeFromTitle(title: String): String? {
        return Regex("[A-Za-z0-9-]{2,24}")
            .findAll(title)
            .map { it.value }
            .lastOrNull()
    }

    private fun normalizeCodes(raw: String): String? {
        val parts = raw.split(Regex("[，,、]+"))
            .mapNotNull { normalizeSingleCode(it) }
        if (parts.isNotEmpty()) return parts.sorted().joinToString("+")
        return normalizeSingleCode(raw)
    }

    private fun normalizeSingleCode(raw: String): String? {
        return raw
            .replace(Regex("[^A-Za-z0-9]"), "")
            .uppercase()
            .takeIf { it.length >= 2 }
    }

    private val CODE_TAGS = setOf(
        EventTags.PICKUP,
        EventTags.FOOD,
        EventTags.TICKET,
        EventTags.SENDER
    )
}
