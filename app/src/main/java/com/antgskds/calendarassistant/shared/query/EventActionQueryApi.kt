package com.antgskds.calendarassistant.shared.query

import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.schedule.domain.model.*

data class EventActionButton(
    val text: String,
    val intentAction: String
)

interface EventActionQueryApi {
    fun isEventStillValid(events: List<Event>, eventId: String): Boolean

    fun resolveEffectiveRuleId(
        intentRuleId: String?,
        fallbackTag: String,
        event: Event?
    ): String

    fun actionTextForRule(ruleId: String): String

    fun buildActionButton(ruleId: String, event: Event?): EventActionButton?
}
