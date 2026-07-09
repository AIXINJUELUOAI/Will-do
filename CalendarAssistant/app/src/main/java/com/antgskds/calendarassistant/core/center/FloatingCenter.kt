package com.antgskds.calendarassistant.core.center

import android.content.Context
import android.content.Intent
import android.util.Log
import com.antgskds.calendarassistant.MainActivity
import com.antgskds.calendarassistant.core.query.SettingsQueryApi
import com.antgskds.calendarassistant.data.model.QuickMemoRecordingDisplayMode
import com.antgskds.calendarassistant.platform.floating.EdgeBarService
import com.antgskds.calendarassistant.platform.floating.FloatingBallService
import com.antgskds.calendarassistant.platform.floating.FloatingScheduleService
import com.antgskds.calendarassistant.platform.floating.QuickMemoVoiceCaptureService

class FloatingCenter(
    private val appContext: Context,
    private val permissionCenter: PermissionCenter,
    private val settingsQueryApi: SettingsQueryApi
) {
    companion object {
        private const val TAG = "FloatingCenter"
    }

    fun canDrawOverlays(context: Context = appContext): Boolean {
        return permissionCenter.canDrawOverlays(context)
    }

    fun startFloatingService(initialInputMode: String? = null): Boolean {
        if (!canDrawOverlays()) return false
        return try {
            appContext.startService(Intent(appContext, FloatingScheduleService::class.java).apply {
                if (initialInputMode != null) {
                    putExtra(FloatingScheduleService.EXTRA_INITIAL_INPUT_MODE, initialInputMode)
                }
            })
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
            val target = voiceCaptureServiceIntent(FloatingScheduleService.ACTION_START_VOICE_CAPTURE, QuickMemoVoiceCaptureService.ACTION_START)
            appContext.startService(target)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Start voice capture service failed", e)
            false
        }
    }

    fun stopVoiceCaptureService(): Boolean {
        if (!canDrawOverlays()) return false
        return try {
            appContext.startService(voiceCaptureServiceIntent(FloatingScheduleService.ACTION_STOP_VOICE_CAPTURE, QuickMemoVoiceCaptureService.ACTION_STOP))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Stop voice capture service failed", e)
            false
        }
    }

    fun startPickupQrCard(eventId: Long): Boolean {
        if (eventId <= 0L || !canDrawOverlays()) return false
        return try {
            appContext.startService(Intent(appContext, FloatingScheduleService::class.java).apply {
                action = FloatingScheduleService.ACTION_SHOW_PICKUP_QR_CARD
                putExtra(FloatingScheduleService.EXTRA_PICKUP_EVENT_ID, eventId)
            })
            true
        } catch (e: Exception) {
            Log.e(TAG, "Start pickup QR card failed", e)
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

    fun startFloatingBallServiceIfPermitted(): Boolean {
        if (!canDrawOverlays()) return false
        return try {
            appContext.startService(Intent(appContext, FloatingBallService::class.java))
            true
        } catch (e: Exception) {
            Log.e(TAG, "Start floating ball service failed", e)
            false
        }
    }

    fun stopFloatingBallService() {
        try {
            appContext.stopService(Intent(appContext, FloatingBallService::class.java))
        } catch (e: Exception) {
            Log.w(TAG, "Stop floating ball service failed", e)
        }
    }

    private fun voiceCaptureServiceIntent(floatingAction: String, liveAction: String): Intent {
        val settings = settingsQueryApi.settings.value
        return if (QuickMemoRecordingDisplayMode.normalize(settings.quickMemoRecordingDisplayMode) == QuickMemoRecordingDisplayMode.FLOATING_WINDOW) {
            Intent(appContext, FloatingScheduleService::class.java).apply {
                action = floatingAction
            }
        } else {
            Intent(appContext, QuickMemoVoiceCaptureService::class.java).apply {
                action = liveAction
            }
        }
    }
}
