package com.antgskds.calendarassistant.core.center

import android.content.Context
import android.content.Intent
import android.util.Log
import com.antgskds.calendarassistant.service.floating.EdgeBarService
import com.antgskds.calendarassistant.service.floating.FloatingScheduleService

class FloatingCenter(
    private val appContext: Context,
    private val permissionCenter: PermissionCenter
) {
    companion object {
        private const val TAG = "FloatingCenter"
    }

    fun canDrawOverlays(context: Context = appContext): Boolean {
        return permissionCenter.canDrawOverlays(context)
    }

    fun startFloatingService(): Boolean {
        if (!canDrawOverlays()) return false
        return try {
            appContext.startService(Intent(appContext, FloatingScheduleService::class.java))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Start floating service failed", e)
            false
        }
    }

    fun startEdgeBarServiceIfPermitted(): Boolean {
        if (!canDrawOverlays()) return false
        return try {
            appContext.startService(Intent(appContext, EdgeBarService::class.java))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Start edge bar service failed", e)
            false
        }
    }

    fun stopEdgeBarService() {
        try {
            appContext.stopService(Intent(appContext, EdgeBarService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "Stop edge bar service failed", e)
        }
    }
}
