package com.antgskds.calendarassistant.data.query

import com.antgskds.calendarassistant.core.query.EventActionButton
import com.antgskds.calendarassistant.core.query.EventActionQueryApi
import com.antgskds.calendarassistant.core.rule.RuleMatchingEngine
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.platform.receiver.EventActionReceiver

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
            EventTags.FOOD -> RuleMatchingEngine.RULE_FOOD
            EventTags.TRAIN -> RuleMatchingEngine.RULE_TRAIN
            EventTags.TAXI -> RuleMatchingEngine.RULE_TAXI
            EventTags.FLIGHT -> RuleMatchingEngine.RULE_FLIGHT
            EventTags.TICKET -> RuleMatchingEngine.RULE_TICKET
            EventTags.SENDER -> RuleMatchingEngine.RULE_SENDER
            else -> RuleMatchingEngine.RULE_GENERAL
        }
    }

    override fun actionTextForRule(ruleId: String): String {
        return when (ruleId) {
            RuleMatchingEngine.RULE_PICKUP -> "请前往取件"
            RuleMatchingEngine.RULE_FOOD -> "请前往取餐"
            RuleMatchingEngine.RULE_TRAIN -> "请准备检票"
            RuleMatchingEngine.RULE_TAXI -> "请准备上车"
            RuleMatchingEngine.RULE_FLIGHT -> "请准备登机"
            RuleMatchingEngine.RULE_TICKET -> "请前往取票"
            RuleMatchingEngine.RULE_SENDER -> "请准备寄件"
            else -> ""
        }
    }

    override fun buildActionButton(ruleId: String, event: Event?): EventActionButton? {
        if (event == null) return null

        val shouldShow = when (ruleId) {
            RuleMatchingEngine.RULE_TRAIN,
            RuleMatchingEngine.RULE_FLIGHT -> !event.isCheckedIn && !event.isCompleted
            RuleMatchingEngine.RULE_PICKUP,
            RuleMatchingEngine.RULE_FOOD,
            RuleMatchingEngine.RULE_TAXI,
            RuleMatchingEngine.RULE_TICKET,
            RuleMatchingEngine.RULE_SENDER,
            RuleMatchingEngine.RULE_GENERAL -> !event.isCompleted
            else -> false
        }
        if (!shouldShow) return null

        val buttonText = when (ruleId) {
            RuleMatchingEngine.RULE_PICKUP -> "已取"
            RuleMatchingEngine.RULE_FOOD -> "已取餐"
            RuleMatchingEngine.RULE_TAXI -> "已用车"
            RuleMatchingEngine.RULE_TRAIN -> "已检票"
            RuleMatchingEngine.RULE_FLIGHT -> "已登机"
            RuleMatchingEngine.RULE_TICKET -> "已取票"
            RuleMatchingEngine.RULE_SENDER -> "已寄件"
            else -> "已完成"
        }
        val action = when (ruleId) {
            RuleMatchingEngine.RULE_TRAIN,
            RuleMatchingEngine.RULE_FLIGHT -> EventActionReceiver.ACTION_CHECKIN
            else -> EventActionReceiver.ACTION_COMPLETE_SCHEDULE
        }
        return EventActionButton(text = buttonText, intentAction = action)
    }
}
