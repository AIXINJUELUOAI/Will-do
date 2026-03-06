package com.antgskds.calendarassistant.service.capsule.provider

import android.app.Notification
import android.content.Context

interface ICapsuleProvider {
    fun buildNotification(
        context: Context,
        eventId: String,
        title: String,
        content: String,
        color: Int,
        capsuleType: Int,
        eventType: String,
        actualStartTime: Long,
        actualEndTime: Long,
        iconResId: Int
    ): Notification
}