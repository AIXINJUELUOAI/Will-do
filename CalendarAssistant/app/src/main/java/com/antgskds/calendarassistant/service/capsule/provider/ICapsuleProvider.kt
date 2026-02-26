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
        eventType: String,  // 事件类型：event=日程, temp=取件码, course=课程
        actualStartTime: Long = -1,  // 实际开始时间（毫秒），用于计算"还有x分钟开始"
        actualEndTime: Long = -1  // 实际结束时间（毫秒），用于判断取件码是否过期
    ): Notification
}