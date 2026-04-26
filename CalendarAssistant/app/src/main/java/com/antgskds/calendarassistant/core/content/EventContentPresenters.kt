package com.antgskds.calendarassistant.core.content

import android.content.Context
import com.antgskds.calendarassistant.core.rule.EventPresenter
import com.antgskds.calendarassistant.core.rule.EventRenderModel
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.service.capsule.CapsuleDisplayModel

data class EventTimelineItem(
    val event: Event,
    val renderModel: EventRenderModel
) : TimelineItem {
    override val stableId: String = event.idString
    override val sourceType: ContentSourceType = ContentSourceType.SCHEDULE
    override val title: String = renderModel.title
    override val subtitle: String? = renderModel.subtitle
    override val detail: String? = renderModel.detail
    override val timeRange: String? = renderModel.timeRange
}

data class EventCapsuleItem(
    val eventIds: List<String>,
    val displayModel: CapsuleDisplayModel
) : CapsuleContentItem {
    override val stableId: String = eventIds.joinToString(",")
    override val sourceType: ContentSourceType = ContentSourceType.SCHEDULE
    override val shortText: String = displayModel.shortText
    override val primaryText: String = displayModel.primaryText
    override val secondaryText: String? = displayModel.secondaryText
    override val expandedText: String? = displayModel.expandedText
}

object EventTimelinePresenter {
    fun present(context: Context, event: Event): EventTimelineItem {
        return EventTimelineItem(
            event = event,
            renderModel = EventPresenter.present(context, event)
        )
    }
}

object EventCapsulePresenter {
    fun present(context: Context, event: Event, isExpired: Boolean): EventCapsuleItem {
        return EventCapsuleItem(
            eventIds = listOf(event.idString),
            displayModel = EventPresenter.presentCapsule(context, event, isExpired)
        )
    }

    fun present(context: Context, events: List<Event>): EventCapsuleItem {
        return EventCapsuleItem(
            eventIds = events.map { it.idString },
            displayModel = EventPresenter.presentCapsule(context, events)
        )
    }
}
