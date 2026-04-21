package com.antgskds.calendarassistant.core.center

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.antgskds.calendarassistant.core.calendar.CalendarPermissionHelper

class PermissionCenter {
    fun hasCalendarPermissions(context: Context): Boolean {
        return CalendarPermissionHelper.hasAllPermissions(context)
    }

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun hasSmsReadPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    }
}
