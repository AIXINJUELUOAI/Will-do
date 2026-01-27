package com.antgskds.calendarassistant.core.util

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log

/**
 * 魅族 Flyme 系统适配工具
 */
object FlymeUtils {
    private const val TAG = "FlymeUtils"

    /**
     * 判断是否为 Flyme 系统
     */
    fun isFlyme(): Boolean {
        return try {
            val displayId = Build.DISPLAY
            val manufacturer = Build.MANUFACTURER
            manufacturer.contains("Meizu", ignoreCase = true) ||
                    displayId.contains("Flyme", ignoreCase = true)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 检测实况通知是否可用 (系统开关 + 权限)
     */
    fun isLiveNotificationEnabled(context: Context): Boolean {
        if (!isFlyme()) return false

        if (context.checkSelfPermission("flyme.permission.READ_NOTIFICATION_LIVE_STATE") !=
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            return false
        }

        return try {
            val uri = Uri.parse("content://com.android.systemui.notification.provider")
            val call: Bundle? = context.contentResolver.call(
                uri,
                "isNotificationLiveEnabled",
                null,
                null
            )
            call?.getBoolean("result", false) ?: false
        } catch (e: Exception) {
            // 如果检测失败，默认认为开启，避免误判导致功能不可用
            true
        }
    }
}