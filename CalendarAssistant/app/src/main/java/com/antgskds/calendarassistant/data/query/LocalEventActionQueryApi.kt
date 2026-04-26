package com.antgskds.calendarassistant.data.query

import com.antgskds.calendarassistant.core.query.EventActionButton
import com.antgskds.calendarassistant.core.query.EventActionQueryApi
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.service.receiver.EventActionReceiver

class LocalEventActionQueryApi : EventActionQueryApi {
    override fun isEventStillValid(events: List<Event>, eventId: String): Boolean {
        if (events.isEmpty()) return true
        return events.any { it.id?.toString() == eventId }
    }

    override fun resolveEffectiveRuleId(intentRuleId: String?, fallbackTag: String, event: Event?): String {
        if (!intentRuleId.isNullOrEmpty()) return intentRuleId

        val eventRuleId = event?.let { RuleMatchingEngine.resolvePayload(it)?.ruleId }
        if (!eventRuleId.isNullOrEmpty()) return eventRuleId

        return when (fallbackTag) {
            EventTags.PICKUP -> RuleMatchingEngine.RULE_PICKUP
            EventTags.TRAIN -> RuleMatchingEngine.RULE_TRAIN
            EventTags.TAXI -> RuleMatchingEngine.RULE_TAXI
            else -> RuleMatchingEngine.RULE_GENERAL
        }
    }

    override fun actionTextForRule(ruleId: String): String {
        return when (ruleId) {
            RuleMatchingEngine.RULE_PICKUP -> "请前往取件"
            RuleMatchingEngine.RULE_TRAIN -> "请准备检票"
            RuleMatchingEngine.RULE_TAXI -> "请准备上车"
            else -> ""
        }
    }

    override fun buildActionButton(ruleId: String, event: Event?): EventActionButton? {
        if (event == null) return null

        val shouldShow = when (ruleId) {
            RuleMatchingEngine.RULE_TRAIN -> !event.isCheckedIn
            RuleMatchingEngine.RULE_PICKUP,
            RuleMatchingEngine.RULE_TAXI,
            RuleMatchingEngine.RULE_GENERAL -> !event.isCompleted
            else -> false
        }
        if (!shouldShow) return null

        val buttonText = when (ruleId) {
            RuleMatchingEngine.RULE_PICKUP -> "已取"
            RuleMatchingEngine.RULE_TAXI -> "已用车"
            RuleMatchingEngine.RULE_TRAIN -> "已检票"
            else -> "已完成"
        }
        val action = when (ruleId) {
            RuleMatchingEngine.RULE_TRAIN -> EventActionReceiver.ACTION_CHECKIN
            else -> EventActionReceiver.ACTION_COMPLETE_SCHEDULE
        }
        return EventActionButton(text = buttonText, intentAction = action)
    }
}
