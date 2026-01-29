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
        capsuleType: Int,  // 1=日程, 2=取件码
        eventType: String  // 事件类型：event=日程, temp=取件码, course=课程
    ): Notification
}