package com.antgskds.calendarassistant.core.query

import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*

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
