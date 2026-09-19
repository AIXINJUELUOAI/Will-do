package com.antgskds.calendarassistant.platform.permission

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.antgskds.calendarassistant.platform.accessibility.TextAccessibilityService
import com.antgskds.calendarassistant.platform.receiver.SmsNotificationListenerService
import com.antgskds.calendarassistant.shared.util.PrivilegeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

enum class PrivilegedPermissionKey {
    NOTIFICATION,
    OVERLAY,
    MICROPHONE,
    ACCESSIBILITY,
    CALENDAR,
    EXACT_ALARM,
    BATTERY,
    LOCATION,
    NOTIFICATION_LISTENER,
    SMS,
}

data class PrivilegedPermissionResult(
    val key: PrivilegedPermissionKey,
    val requestedEnabled: Boolean,
    val actualEnabled: Boolean,
    val message: String = "",
) {
    val success: Boolean get() = requestedEnabled == actualEnabled
}

object PrivilegedPermissionController {
    private val simulatedStates = ConcurrentHashMap<PrivilegedPermissionKey, Boolean>()

    fun clearSimulation() {
        simulatedStates.clear()
    }

    fun isEnabled(
        context: Context,
        key: PrivilegedPermissionKey,
        simulateRoot: Boolean = false,
    ): Boolean {
        if (simulateRoot) {
            return simulatedStates.getOrPut(key) { readActualState(context, key) }
        }
        return readActualState(context, key)
    }

    suspend fun setEnabled(
        context: Context,
        key: PrivilegedPermissionKey,
        enabled: Boolean,
        simulateRoot: Boolean = false,
    ): PrivilegedPermissionResult = withContext(Dispatchers.IO) {
        if (simulateRoot) {
            simulatedStates[key] = enabled
            return@withContext PrivilegedPermissionResult(key, enabled, enabled, "模拟 Root")
        }
        if (PrivilegeManager.privilegeType != PrivilegeManager.PrivilegeType.ROOT) {
            return@withContext PrivilegedPermissionResult(
                key = key,
                requestedEnabled = enabled,
                actualEnabled = readActualState(context, key),
                message = "未获得 Root 权限",
            )
        }

        val commands = commandsFor(context, key, enabled)
        var failure = ""
        for (command in commands) {
            val result = PrivilegeManager.executeShell(command)
            if (!result.first) {
                failure = result.second.ifBlank { "系统命令执行失败" }
                break
            }
        }
        delay(150)
        val actual = readActualState(context, key)
        PrivilegedPermissionResult(
            key = key,
            requestedEnabled = enabled,
            actualEnabled = actual,
            message = if (actual == enabled) "" else failure.ifBlank { "系统未接受权限变更" },
        )
    }

    suspend fun enableAll(
        context: Context,
        simulateRoot: Boolean = false,
    ): List<PrivilegedPermissionResult> {
        return PrivilegedPermissionKey.entries.map { key ->
            setEnabled(context, key, enabled = true, simulateRoot = simulateRoot)
        }
    }

    private fun commandsFor(context: Context, key: PrivilegedPermissionKey, enabled: Boolean): List<String> {
        val packageName = context.packageName
        val grantAction = if (enabled) "grant" else "revoke"
        val appOpMode = if (enabled) "allow" else "default"
        return when (key) {
            PrivilegedPermissionKey.NOTIFICATION -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                listOf(
                    "pm $grantAction $packageName ${Manifest.permission.POST_NOTIFICATIONS}",
                    "appops set $packageName POST_NOTIFICATION $appOpMode",
                )
            } else {
                emptyList()
            }
            PrivilegedPermissionKey.OVERLAY ->
                listOf("appops set $packageName SYSTEM_ALERT_WINDOW $appOpMode")
            PrivilegedPermissionKey.MICROPHONE ->
                listOf("pm $grantAction $packageName ${Manifest.permission.RECORD_AUDIO}")
            PrivilegedPermissionKey.CALENDAR -> listOf(
                "pm $grantAction $packageName ${Manifest.permission.READ_CALENDAR}",
                "pm $grantAction $packageName ${Manifest.permission.WRITE_CALENDAR}",
            )
            PrivilegedPermissionKey.EXACT_ALARM -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf("appops set $packageName SCHEDULE_EXACT_ALARM $appOpMode")
            } else {
                emptyList()
            }
            PrivilegedPermissionKey.BATTERY ->
                listOf("dumpsys deviceidle whitelist ${if (enabled) "+" else "-"}$packageName")
            PrivilegedPermissionKey.LOCATION -> listOf(
                "pm $grantAction $packageName ${Manifest.permission.ACCESS_COARSE_LOCATION}",
                "pm $grantAction $packageName ${Manifest.permission.ACCESS_FINE_LOCATION}",
            )
            PrivilegedPermissionKey.NOTIFICATION_LISTENER -> {
                val component = ComponentName(context, SmsNotificationListenerService::class.java).flattenToString()
                listOf("cmd notification ${if (enabled) "allow_listener" else "disallow_listener"} $component")
            }
            PrivilegedPermissionKey.SMS -> listOf(
                "pm $grantAction $packageName ${Manifest.permission.RECEIVE_SMS}",
                "pm $grantAction $packageName ${Manifest.permission.READ_SMS}",
            )
            PrivilegedPermissionKey.ACCESSIBILITY -> accessibilityCommands(context, enabled)
        }
    }

    private fun accessibilityCommands(context: Context, enabled: Boolean): List<String> {
        val component = ComponentName(context, TextAccessibilityService::class.java).flattenToString()
        val current = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        val services = current.split(':').filter { it.isNotBlank() }.toMutableSet()
        if (enabled) services += component else services -= component
        val merged = services.joinToString(":")
        return buildList {
            add("settings put secure enabled_accessibility_services ${shellQuote(merged)}")
            add("settings put secure accessibility_enabled ${if (services.isEmpty()) 0 else 1}")
        }
    }

    private fun readActualState(context: Context, key: PrivilegedPermissionKey): Boolean = when (key) {
        PrivilegedPermissionKey.NOTIFICATION -> {
            val runtimeGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            runtimeGranted && context.getSystemService(NotificationManager::class.java)?.areNotificationsEnabled() == true
        }
        PrivilegedPermissionKey.OVERLAY -> Settings.canDrawOverlays(context)
        PrivilegedPermissionKey.MICROPHONE -> hasPermission(context, Manifest.permission.RECORD_AUDIO)
        PrivilegedPermissionKey.ACCESSIBILITY -> {
            val component = ComponentName(context, TextAccessibilityService::class.java).flattenToString()
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty()
                .split(':')
                .any { it.equals(component, ignoreCase = true) }
        }
        PrivilegedPermissionKey.CALENDAR ->
            hasPermission(context, Manifest.permission.READ_CALENDAR) &&
                hasPermission(context, Manifest.permission.WRITE_CALENDAR)
        PrivilegedPermissionKey.EXACT_ALARM -> Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        PrivilegedPermissionKey.BATTERY ->
            context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
        PrivilegedPermissionKey.LOCATION ->
            hasPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
                hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        PrivilegedPermissionKey.NOTIFICATION_LISTENER ->
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
        PrivilegedPermissionKey.SMS ->
            hasPermission(context, Manifest.permission.READ_SMS) &&
                hasPermission(context, Manifest.permission.RECEIVE_SMS)
    }

    private fun hasPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"
}
