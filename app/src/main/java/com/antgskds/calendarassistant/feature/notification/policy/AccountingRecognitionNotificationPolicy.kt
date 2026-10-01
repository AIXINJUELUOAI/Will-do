package com.antgskds.calendarassistant.feature.notification.policy

import com.antgskds.calendarassistant.feature.notification.model.*
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings

/** 队列任务拥有独立发布生命周期，不依赖日程胶囊的状态计算来补发。 */
object AccountingRecognitionNotificationPolicy {
    fun owns(key: NotificationKey) = key.value.startsWith("recognition:accounting:")

    fun route(kind: NotificationKind, key: NotificationKey, liveEnabled: Boolean): NotificationRoute? {
        if (kind != NotificationKind.RECOGNITION_STATUS || !owns(key)) return null
        return if (liveEnabled) NotificationRoute.LIVE else NotificationRoute.NORMAL
    }

    fun timeout(ongoing: Boolean, settings: MySettings): Long? =
        if (ongoing) null else settings.resultNotificationTimeoutMs.toLong().coerceAtLeast(1L)
}
