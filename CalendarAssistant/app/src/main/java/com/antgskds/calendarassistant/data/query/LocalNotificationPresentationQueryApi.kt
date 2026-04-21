package com.antgskds.calendarassistant.data.query

import com.antgskds.calendarassistant.core.query.NotificationPresentation
import com.antgskds.calendarassistant.core.query.NotificationPresentationQueryApi

class LocalNotificationPresentationQueryApi : NotificationPresentationQueryApi {
    override fun buildPresentation(
        startTime: String,
        endTime: String,
        location: String,
        label: String,
        actionText: String
    ): NotificationPresentation {
        val timeText = formatTimeText(startTime, endTime)
        val locationText = if (location.isNotEmpty()) "【$location】" else ""
        val prefixLabel = if (label.isNotEmpty() && !label.contains("开始") && !label.contains("现在")) {
            "[$label] "
        } else {
            ""
        }

        var content = "$prefixLabel$timeText $locationText $actionText".trim()
        if (content.isEmpty()) {
            content = if (label.isNotEmpty()) label else "点击查看详情"
        }

        return NotificationPresentation(
            timeText = timeText,
            locationText = locationText,
            contentText = content
        )
    }

    private fun formatTimeText(startTime: String, endTime: String): String {
        val extractTime = { fullTime: String ->
            if (fullTime.contains(" ")) {
                fullTime.substringAfter(" ")
            } else {
                fullTime
            }
        }
        val start = extractTime(startTime)
        val end = extractTime(endTime)

        return if (start.isNotEmpty()) {
            if (end.isNotEmpty()) "$start - $end" else start
        } else {
            ""
        }
    }
}
