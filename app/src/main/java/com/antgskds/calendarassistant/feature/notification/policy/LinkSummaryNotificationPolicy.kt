package com.antgskds.calendarassistant.feature.notification.policy
import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.feature.notification.model.*
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationIds
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.LinkSummaryDisplay

object LinkSummaryNotificationPolicy {
    fun key(id: Long) = NotificationKey("link:summary:$id")
    fun owns(key: NotificationKey) = key.value.startsWith("link:summary:")
    fun route(kind: NotificationKind, live: Boolean): NotificationRoute? =
        if (kind==NotificationKind.LINK_SUMMARY_READY) { if (live) NotificationRoute.LIVE else NotificationRoute.NORMAL } else null
    fun request(id: Long,title: String,settings: MySettings) = NotificationRequest(
        key=key(id),kind=NotificationKind.LINK_SUMMARY_READY,
        route=NotificationRoute.AUTO, notificationId=NotificationIds.createdEventResult("link-summary",id.toString()),
        smallIconResId=R.drawable.ic_stat_quickmemo,channelKey=App.CHANNEL_ID_POPUP,category="status",
        display=LinkSummaryDisplay.snapshot(title),
        behavior=NotificationBehavior(timeoutAfterMillis=com.antgskds.calendarassistant.feature.capsule.domain.QuickMemoCapsuleDurationPolicy.durationMillis(settings.defaultEventDurationMinutes),
            autoCancel=true,onlyAlertOnce=false,priority=NotificationPriority.HIGH),
        tapTarget=NotificationTapTarget(NotificationTapTargetType.QUICK_MEMO_DETAIL,mapOf("quickMemoId" to id.toString())),
        actions=listOf(NotificationAction.viewQuickMemo(id)), source="link-summary",
    )
}
