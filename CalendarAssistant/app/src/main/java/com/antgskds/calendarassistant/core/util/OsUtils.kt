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
}
