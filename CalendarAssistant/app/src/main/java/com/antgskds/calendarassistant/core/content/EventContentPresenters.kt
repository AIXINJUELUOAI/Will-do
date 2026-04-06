package com.antgskds.calendarassistant.core.content

import android.content.Context
import com.antgskds.calendarassistant.core.rule.EventPresenter
import com.antgskds.calendarassistant.core.rule.EventRenderModel
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.service.capsule.CapsuleDisplayModel

data class EventTimelineItem(
    val event: MyEvent,
    val renderModel: EventRenderModel
) : TimelineItem {
    override val stableId: String = event.id
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
    fun present(context: Context, event: MyEvent): EventTimelineItem {
        return EventTimelineItem(
            event = event,
            renderModel = EventPresenter.present(context, event)
        )
    }
}

object EventCapsulePresenter {
    fun present(context: Context, event: MyEvent, isExpired: Boolean): EventCapsuleItem {
        return EventCapsuleItem(
            eventIds = listOf(event.id),
            displayModel = EventPresenter.presentCapsule(context, event, isExpired)
        )
    }

    fun present(context: Context, events: List<MyEvent>): EventCapsuleItem {
        return EventCapsuleItem(
            eventIds = events.map { it.id },
            displayModel = EventPresenter.presentCapsule(context, events)
        )
    }
}
