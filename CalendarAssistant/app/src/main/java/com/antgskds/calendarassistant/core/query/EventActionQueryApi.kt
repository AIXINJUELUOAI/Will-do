package com.antgskds.calendarassistant.core.query

import com.antgskds.calendarassistant.data.model.MyEvent

data class EventActionButton(
    val text: String,
    val intentAction: String
)

interface EventActionQueryApi {
    fun isEventStillValid(events: List<MyEvent>, eventId: String): Boolean

    fun resolveEffectiveRuleId(
        intentRuleId: String?,
        fallbackTag: String,
        event: MyEvent?
    ): String

    fun actionTextForRule(ruleId: String): String

    fun buildActionButton(ruleId: String, event: MyEvent?): EventActionButton?
}
