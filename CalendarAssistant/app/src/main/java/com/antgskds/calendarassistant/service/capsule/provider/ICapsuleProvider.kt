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
        capsuleType: Int  // 新增参数：1=日程, 2=取件码
    ): Notification
}