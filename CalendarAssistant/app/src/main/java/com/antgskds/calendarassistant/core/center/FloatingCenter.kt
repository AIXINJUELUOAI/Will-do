package com.antgskds.calendarassistant.core.center

import android.content.Context
import android.content.Intent
import android.util.Log
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.core.service.voice.VoiceCaptureHandleActivity
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

    fun startVoiceCaptureService(): Boolean {
        if (!canDrawOverlays()) return false
        return try {
            if (!permissionCenter.hasRecordAudioPermission(appContext)) {
                appContext.startActivity(Intent(appContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(MainActivity.EXTRA_REQUEST_RECORD_AUDIO_PERMISSION, true)
                })
                return false
            }
            VoiceCaptureHandleActivity.prepareForLaunch()
            appContext.startActivity(Intent(appContext, VoiceCaptureHandleActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION
            })
            true
        } catch (e: Exception) {
            VoiceCaptureHandleActivity.clearPendingLaunch()
            Log.e(TAG, "Start voice capture service failed", e)
            false
        }
    }

    fun stopVoiceCaptureService(): Boolean {
        if (!canDrawOverlays()) return false
        return try {
            if (VoiceCaptureHandleActivity.stopActiveCapture()) return true
            appContext.startService(Intent(appContext, FloatingScheduleService::class.java).apply {
                action = FloatingScheduleService.ACTION_STOP_VOICE_CAPTURE
            })
            true
        } catch (e: Exception) {
            Log.e(TAG, "Stop voice capture service failed", e)
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
