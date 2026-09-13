package com.antgskds.calendarassistant.feature.quickmemo.application

import com.antgskds.calendarassistant.App
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.feature.notification.api.NotificationApi
import com.antgskds.calendarassistant.feature.notification.model.NotificationBehavior
import com.antgskds.calendarassistant.feature.notification.model.NotificationDisplaySnapshot
import com.antgskds.calendarassistant.feature.notification.model.NotificationKey
import com.antgskds.calendarassistant.feature.notification.model.NotificationKind
import com.antgskds.calendarassistant.feature.notification.model.NotificationPriority
import com.antgskds.calendarassistant.feature.notification.model.NotificationRequest
import com.antgskds.calendarassistant.feature.notification.model.NotificationResult
import com.antgskds.calendarassistant.feature.notification.model.NotificationRoute
import com.antgskds.calendarassistant.feature.notification.model.NotificationTapTarget
import com.antgskds.calendarassistant.feature.notification.model.NotificationTapTargetType
import com.antgskds.calendarassistant.feature.notification.model.NotificationTrigger
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.platform.notification.alarmlegacy.NotificationIds

/** 到期提醒只组装请求；登记、权限检查和发布交给统一通知链路。 */
class QuickMemoReminderNotificationBridge(private val notificationApi: NotificationApi) {
    suspend fun publish(memo: QuickMemoEntity, reminderId: Long): NotificationResult {
        val memoId = requireNotNull(memo.id)
        val key = NotificationKey.quickMemoReminder(reminderId)
        val body = memo.bodyText.trim().ifBlank {
            when {
                !memo.imagePath.isNullOrBlank() -> "图片随口记"
                !memo.audioPath.isNullOrBlank() -> "语音随口记"
                else -> "点击查看随口记"
            }
        }
        val created = notificationApi.create(
            NotificationRequest(
                key = key,
                kind = NotificationKind.QUICK_MEMO_REMINDER,
                route = NotificationRoute.AUTO,
                notificationId = NotificationIds.quickMemoReminder(reminderId),
                smallIconResId = R.drawable.ic_stat_quickmemo,
                channelKey = App.CHANNEL_ID_POPUP,
                category = "reminder",
                display = NotificationDisplaySnapshot(
                    shortText = "随口记提醒",
                    primaryText = "随口记提醒",
                    secondaryText = body,
                    expandedText = body,
                ),
                behavior = NotificationBehavior(
                    autoCancel = true,
                    onlyAlertOnce = false,
                    priority = NotificationPriority.HIGH,
                ),
                tapTarget = NotificationTapTarget(
                    NotificationTapTargetType.QUICK_MEMO_DETAIL,
                    mapOf("quickMemoId" to memoId.toString()),
                ),
                source = "quick-memo-reminder",
            )
        )
        // create 仅登记 READY 快照；必须 trigger 才会实际发布。
        if (created is NotificationResult.Failure) return created
        return notificationApi.trigger(NotificationTrigger.ByKey(key))
    }
}
