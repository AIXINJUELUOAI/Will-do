package com.antgskds.calendarassistant.core.rule

import android.content.Context
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.data.model.LiveNotificationTemplateMode
import com.antgskds.calendarassistant.service.capsule.CapsuleDisplayModel
import java.time.LocalDateTime

data class EventRenderModel(
    val eventId: String,
    val ruleId: String,
    val ruleName: String,
    val title: String,
    val subtitle: String?,
    val detail: String?,
    val timeRange: String?,
    val statusLabel: String?,
    val statusColor: StatusColor,
    val isTerminal: Boolean,
    val primaryAction: EventAction?,
    val undoAction: EventAction?,
    val iconResId: Int?,
    val actionIcon: ActionIconSpec,
    val isExpired: Boolean,
    val isInProgress: Boolean,
    val isComingSoon: Boolean,
    val isRecurring: Boolean,
    val canEdit: Boolean,
    val isAggregatePickup: Boolean,
    val isAggregate: Boolean = false,
    val subItems: List<EventRenderModel> = emptyList()
)

data class EventAction(
    val actionLabel: String,
    val receiverAction: String,
    val isUndo: Boolean
)

enum class StatusColor { PRIMARY, SUCCESS, WARNING, MUTED }

enum class ActionIconType { UNDO, CHECKIN, RIDE, PICKUP, COMPLETE }

data class ActionIconSpec(
    val type: ActionIconType,
    val color: Long
)

object EventPresenter {
    fun present(context: Context, event: Event): EventRenderModel {
        val ruleId = EventPresentationInternals.resolveRuleId(event)
        val ruleName = RuleRegistry.getRule(ruleId)?.name ?: ruleId
        val now = LocalDateTime.now()
        val isExpired = EventPresentationInternals.computeIsExpired(event, now)
        val isInProgress = EventPresentationInternals.computeIsInProgress(event, now)
        val isComingSoon = EventPresentationInternals.computeIsComingSoon(event, now)
        val isTerminal = event.isCompleted || event.isCheckedIn
        val isCourse = event.tag == EventTags.COURSE || event.tag == "__removed_course__"
        val isAggregatePickup = EventPresentationInternals.isFoodPickup(event.description)
        val (title, subtitle, detail) = EventPresentationInternals.resolveDisplayContent(event, ruleId, isExpired, isTerminal)

        return EventRenderModel(
            eventId = event.idString,
            ruleId = ruleId,
            ruleName = ruleName,
            title = title,
            subtitle = subtitle,
            detail = detail,
            timeRange = EventPresentationInternals.resolveTimeRange(event, ruleId, isCourse),
            statusLabel = EventPresentationInternals.resolveStatusLabel(event, ruleId, isExpired, isInProgress, isComingSoon),
            statusColor = EventPresentationInternals.resolveStatusColor(event, isExpired, isInProgress, isComingSoon),
            isTerminal = isTerminal,
            primaryAction = EventPresentationInternals.resolvePrimaryAction(ruleId, event, isExpired, isCourse),
            undoAction = EventPresentationInternals.resolveUndoAction(ruleId, isTerminal),
            iconResId = EventPresentationInternals.resolveIconResId(ruleId, event),
            actionIcon = EventPresentationInternals.resolveActionIcon(ruleId, isTerminal),
            isExpired = isExpired,
            isInProgress = isInProgress,
            isComingSoon = isComingSoon,
            isRecurring = event.isRecurring,
            canEdit = !event.isRecurring,
            isAggregatePickup = isAggregatePickup
        )
    }

    fun presentCapsule(
        context: Context,
        event: Event,
        isExpired: Boolean,
        templateMode: String = LiveNotificationTemplateMode.AUTO
    ): CapsuleDisplayModel {
        return EventPresentationInternals.composeCapsule(present(context, event), event, isExpired, templateMode)
    }

    fun presentCapsule(context: Context, events: List<Event>): CapsuleDisplayModel {
        if (events.size == 1) {
            val event = events[0]
            return presentCapsule(context, event, EventPresentationInternals.computeIsExpired(event, LocalDateTime.now()))
        }
        return EventPresentationInternals.composeAggregatePickupCapsule(events)
    }

    fun resolveRuleId(event: Event): String = EventPresentationInternals.resolveRuleId(event)
}
