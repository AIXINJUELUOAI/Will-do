package com.antgskds.calendarassistant.core.util

import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.util.Log
import com.antgskds.calendarassistant.service.accessibility.TextAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object AccessibilityGuardian {
    private const val TAG = "AccessibilityGuardian"

    private const val SERVICE_CLASS = "com.antgskds.calendarassistant.service.accessibility.TextAccessibilityService"
    private const val CHECK_COOLDOWN_MS = 15000L
    private const val BACKGROUND_CHECK_COOLDOWN_MS = 30 * 60 * 1000L
    private const val RESTORE_SETTLE_DELAY_MS = 400L
    private const val REBIND_SETTLE_DELAY_MS = 250L
    private const val VERIFY_ATTEMPTS = 5
    private var lastForegroundCheckAt = 0L
    private var lastBackgroundCheckAt = 0L

    data class ServiceStatus(
        val enabledInSettings: Boolean,
        val connected: Boolean
    ) {
        val operational: Boolean get() = enabledInSettings && connected
    }

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

        val initialStatus = getServiceStatus(context)
        if (initialStatus.operational) {
            Log.d(TAG, "Accessibility service already operational")
            return true
        }

        Log.w(
            TAG,
            "Accessibility service not operational, attempting restore; " +
                "enabled=${initialStatus.enabledInSettings}, connected=${initialStatus.connected}"
        )

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

        val restored = enableAccessibilityWithPrivilege(
            context = context,
            forceRebind = initialStatus.enabledInSettings && !initialStatus.connected
        )
        val isRestored = waitForOperationalState(context)
        if (isRestored) {
            Log.d(TAG, "Accessibility service restored successfully")
        } else {
            Log.w(TAG, "Accessibility service not restored, restored=$restored")
        }
        return isRestored
    }

    private suspend fun enableAccessibilityWithPrivilege(
        context: Context,
        forceRebind: Boolean
    ): Boolean {
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

            val hasComponent = services.contains(componentName)
            if (forceRebind && hasComponent) {
                val disabledServices = services.filter { it != componentName }
                val disabledValue = disabledServices.joinToString(":")
                val disableCommand = if (disabledValue.isBlank()) {
                    "settings put secure enabled_accessibility_services \"\""
                } else {
                    "settings put secure enabled_accessibility_services $disabledValue"
                }
                val (disableOk, disableOutput) = PrivilegeManager.executeShell(disableCommand)
                if (!disableOk) {
                    ExceptionLogStore.append(context, TAG, "Disable enabled_accessibility_services failed: $disableOutput")
                    return false
                }
                delay(REBIND_SETTLE_DELAY_MS)
                services.remove(componentName)
            }

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
            delay(RESTORE_SETTLE_DELAY_MS)
            true
        } catch (e: Exception) {
            ExceptionLogStore.append(context, TAG, "Enable accessibility failed", e)
            false
        }
    }

    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return getServiceStatus(context).enabledInSettings
    }

    fun isAccessibilityServiceOperational(context: Context): Boolean {
        return getServiceStatus(context).operational
    }

    fun getServiceStatus(context: Context): ServiceStatus {
        val service = "${context.packageName}/$SERVICE_CLASS"

        return try {
            val enabled = Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED
            )

            if (enabled != 1) {
                Log.d(TAG, "Accessibility not enabled globally")
                return ServiceStatus(enabledInSettings = false, connected = TextAccessibilityService.isConnected())
            }

            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""

            val isEnabled = !TextUtils.isEmpty(enabledServices) && enabledServices.contains(service)
            val connected = TextAccessibilityService.isConnected()
            Log.d(TAG, "Service $service enabled=$isEnabled, connected=$connected")
            ServiceStatus(enabledInSettings = isEnabled, connected = connected)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to check accessibility service status", e)
            ServiceStatus(enabledInSettings = false, connected = TextAccessibilityService.isConnected())
        }
    }

    private suspend fun waitForOperationalState(context: Context): Boolean {
        repeat(VERIFY_ATTEMPTS) { index ->
            val status = getServiceStatus(context)
            if (status.operational) {
                return true
            }
            if (index < VERIFY_ATTEMPTS - 1) {
                delay(RESTORE_SETTLE_DELAY_MS)
            }
        }
        return false
    }
}
