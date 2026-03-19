package com.antgskds.calendarassistant.core.util

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AccessibilityGuardian {
    private const val TAG = "AccessibilityGuardian"

    private const val SERVICE_CLASS = "com.antgskds.calendarassistant.service.accessibility.TextAccessibilityService"
    private const val CHECK_COOLDOWN_MS = 15000L
    private const val BACKGROUND_CHECK_COOLDOWN_MS = 30 * 60 * 1000L
    private var lastForegroundCheckAt = 0L
    private var lastBackgroundCheckAt = 0L

    fun checkAndRestoreIfNeeded(
        context: Context,
        scope: CoroutineScope,
        isBackground: Boolean = false
    ) {
        scope.launch(Dispatchers.IO) {
            restoreIfNeeded(context, isBackground)
        }
    }

    suspend fun restoreIfNeeded(
        context: Context,
        isBackground: Boolean = false
    ): Boolean {
        val now = System.currentTimeMillis()
        val lastCheckAt = if (isBackground) lastBackgroundCheckAt else lastForegroundCheckAt
        val cooldown = if (isBackground) BACKGROUND_CHECK_COOLDOWN_MS else CHECK_COOLDOWN_MS
        if (now - lastCheckAt < cooldown) {
            return false
        }
        if (isBackground) {
            lastBackgroundCheckAt = now
        } else {
            lastForegroundCheckAt = now
        }

        if (isAccessibilityServiceEnabled(context)) {
            Log.d(TAG, "Accessibility service already enabled")
            return true
        }

        Log.w(TAG, "Accessibility service disabled, attempting to restore...")

        if (!PrivilegeManager.hasPrivilege) {
            PrivilegeManager.refreshPrivilege()
            if (!PrivilegeManager.hasPrivilege) {
                delay(800)
                PrivilegeManager.refreshPrivilege()
            }
        }

        if (!PrivilegeManager.hasPrivilege) {
            Log.d(TAG, "No privilege, cannot restore accessibility service")
            return false
        }

        val restored = enableAccessibilityWithPrivilege(context)
        delay(500)
        val isRestored = isAccessibilityServiceEnabled(context)
        if (isRestored) {
            Log.d(TAG, "Accessibility service restored successfully")
        } else {
            Log.w(TAG, "Accessibility service not restored, restored=$restored")
        }
        return isRestored
    }

    private suspend fun enableAccessibilityWithPrivilege(context: Context): Boolean {
        if (!PrivilegeManager.hasPrivilege) return false
        val componentName = "${context.packageName}/$SERVICE_CLASS"
        return try {
            val (readOk, readOutput) = PrivilegeManager.executeShell(
                "settings get secure enabled_accessibility_services"
            )
            if (!readOk) {
                ExceptionLogStore.append(context, TAG, "Read enabled_accessibility_services failed: $readOutput")
                return false
            }
            val raw = readOutput.trim()
            val normalized = if (raw == "null" || raw.isBlank()) "" else raw
            val services = normalized.split(":")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .toMutableList()

            if (!services.contains(componentName)) {
                services.add(componentName)
            }

            val newServices = if (services.isEmpty()) componentName else services.joinToString(":")
            val (writeOk, writeOutput) = PrivilegeManager.executeShell(
                "settings put secure enabled_accessibility_services $newServices"
            )
            if (!writeOk) {
                ExceptionLogStore.append(context, TAG, "Write enabled_accessibility_services failed: $writeOutput")
                return false
            }

            val (enableOk, enableOutput) = PrivilegeManager.executeShell(
                "settings put secure accessibility_enabled 1"
            )
            if (!enableOk) {
                ExceptionLogStore.append(context, TAG, "Enable accessibility failed: $enableOutput")
                return false
            }
            true
        } catch (e: Exception) {
            ExceptionLogStore.append(context, TAG, "Enable accessibility failed", e)
            false
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val service = "${context.packageName}/$SERVICE_CLASS"

        return try {
            val enabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )

            if (enabled != 1) {
                Log.d(TAG, "Accessibility not enabled globally")
                return false
            }

            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""

            val isEnabled = !TextUtils.isEmpty(enabledServices) && enabledServices.contains(service)
            Log.d(TAG, "Service $service enabled: $isEnabled")
            isEnabled
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check accessibility service status", e)
            false
        }
    }
}
