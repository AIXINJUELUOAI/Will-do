package com.antgskds.calendarassistant.core.calendar

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 日历权限辅助类
 * 负责检查和请求日历读写权限
 */
object CalendarPermissionHelper {

    /**
     * 需要的日历权限
     */
    val REQUIRED_PERMISSIONS: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            arrayOf(
                android.Manifest.permission.READ_CALENDAR,
                android.Manifest.permission.WRITE_CALENDAR
            )
        } else {
            emptyArray() // Android 6.0 以下无需运行时权限
        }

    /**
     * 检查是否拥有所有必需的日历权限
     */
    fun hasAllPermissions(context: Context): Boolean {
        return REQUIRED_PERMISSIONS.all { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * 获取未授予的权限列表
     */
    fun getDeniedPermissions(context: Context): Array<String> {
        return REQUIRED_PERMISSIONS.filter { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()
    }
}
