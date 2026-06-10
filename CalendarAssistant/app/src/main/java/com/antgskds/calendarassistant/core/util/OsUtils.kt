package com.antgskds.calendarassistant.core.util

import android.os.Build
import android.util.Log

object OsUtils {
    private const val TAG = "OsUtils"

    fun isHyperOS(): Boolean {
        return try {
            val buildClass = Class.forName("android.os.SystemProperties")
            val getMethod = buildClass.getMethod("get", String::class.java)
            val version = getMethod.invoke(null, "ro.miui.ui.version.name") as String
            val isMiui = version.isNotEmpty()
            val isXiaomi = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)
            val isHyper = isMiui && isXiaomi
            Log.d(TAG, "HyperOS check: miuiVersion=$version, manufacturer=${Build.MANUFACTURER}, result=$isHyper")
            isHyper
        } catch (e: Exception) {
            Log.w(TAG, "HyperOS check failed, fallback to manufacturer check", e)
            Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)
        }
    }

    fun isColorOsLike(): Boolean {
        val manufacturer = Build.MANUFACTURER.lowercase()
        val brand = Build.BRAND.lowercase()
        val display = Build.DISPLAY.lowercase()
        return manufacturer in COLOR_OS_BRANDS ||
            brand in COLOR_OS_BRANDS ||
            display.contains("coloros") ||
            getSystemProperty("ro.build.version.opporom").isNotBlank() ||
            getSystemProperty("ro.oplus.version").isNotBlank() ||
            getSystemProperty("ro.build.version.oplusrom").isNotBlank()
    }

    fun isOneUi(): Boolean {
        return Build.MANUFACTURER.equals("samsung", ignoreCase = true) ||
            getSystemProperty("ro.build.version.oneui").isNotBlank()
    }

    fun isPixelLike(): Boolean {
        val brand = Build.BRAND.lowercase()
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()
        return brand == "google" || manufacturer == "google" || model.contains("pixel")
    }

    fun isLineageLike(): Boolean {
        val display = Build.DISPLAY.lowercase()
        return display.contains("lineage") ||
            display.contains("pixelos") ||
            getSystemProperty("ro.lineage.version").isNotBlank() ||
            getSystemProperty("ro.pixelos.version").isNotBlank()
    }

    fun supportsNativeMultilineLiveNotification(): Boolean {
        return when {
            isColorOsLike() && !isColorOsMultilineAllowlisted() -> false
            isOneUi() || isPixelLike() || isLineageLike() -> true
            else -> true
        }
    }

    private fun isColorOsMultilineAllowlisted(): Boolean {
        val fingerprint = Build.FINGERPRINT.lowercase()
        return COLOR_OS_MULTILINE_ALLOWLIST.any { marker -> fingerprint.contains(marker) }
    }

    private fun getSystemProperty(name: String): String {
        return try {
            val buildClass = Class.forName("android.os.SystemProperties")
            val getMethod = buildClass.getMethod("get", String::class.java)
            getMethod.invoke(null, name) as? String ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    private val COLOR_OS_BRANDS = setOf("oppo", "oneplus", "realme", "oplus")
    private val COLOR_OS_MULTILINE_ALLOWLIST = emptySet<String>()
}
