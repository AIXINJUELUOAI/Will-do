package com.antgskds.calendarassistant.shared.management.resource.notification.display.normal

object SystemNormalDisplay {
    fun accessibilityServiceDisabled(): NormalNotificationContent {
        return NormalNotificationContent(
            title = "服务未开启",
            contentText = "点击此处前往设置开启“无障碍服务”以使用识别功能"
        )
    }

    fun floatingPermissionDenied(): NormalNotificationContent {
        return NormalNotificationContent(
            title = "悬浮窗权限未授予",
            contentText = "请在设置中开启悬浮窗权限"
        )
    }

    fun androidVersionTooLow(): NormalNotificationContent {
        return NormalNotificationContent(
            title = "系统版本过低",
            contentText = "截图功能需要 Android 11+"
        )
    }

    fun voiceCaptureRunning(): NormalNotificationContent {
        return NormalNotificationContent(
            title = "正在录制随口记",
            contentText = "松开音量+保存录音"
        )
    }
}
