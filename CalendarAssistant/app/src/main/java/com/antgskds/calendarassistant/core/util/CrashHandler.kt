package com.antgskds.calendarassistant.core.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CrashHandler private constructor(private val context: Context) : Thread.UncaughtExceptionHandler {

    companion object {
        private const val TAG = "CrashHandler"
        private const val PREF_NAME = "crash_prefs"
        private const val KEY_CRASHED = "is_crashed"
        private const val KEY_CLEANUP_INFO = "cleanup_info"

        @Volatile
        private var INSTANCE: CrashHandler? = null

        fun getInstance(context: Context): CrashHandler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CrashHandler(context.applicationContext).also { INSTANCE = it }
            }
        }

        fun init(context: Context) {
            val handler = getInstance(context)
            Thread.setDefaultUncaughtExceptionHandler(handler)
            Log.d(TAG, "CrashHandler 已初始化")
        }

        fun isCrashedLastTime(context: Context): Boolean {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getBoolean(KEY_CRASHED, false)
        }

        fun getCleanupInfo(context: Context): String? {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            return prefs.getString(KEY_CLEANUP_INFO, null)
        }

        fun clearCrashState(context: Context) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean(KEY_CRASHED, false)
                .remove(KEY_CLEANUP_INFO)
                .apply()
        }

        fun markCrashed(context: Context) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putBoolean(KEY_CRASHED, true).apply()
        }

        fun saveCleanupInfo(context: Context, info: String) {
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_CLEANUP_INFO, info).apply()
        }
    }

    private val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

    override fun uncaughtException(thread: Thread, exception: Throwable) {
        Log.e(TAG, "捕获到未处理异常", exception)

        try {
            val crashLog = buildCrashLog(exception)
            ExceptionLogStore.append(context, TAG, crashLog)
            markCrashed(context)
        } catch (e: Exception) {
            Log.e(TAG, "保存崩溃日志失败", e)
        }

        defaultHandler?.uncaughtException(thread, exception)
    }

    private fun buildCrashLog(exception: Throwable): String {
        val sb = StringBuilder()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timestamp = System.currentTimeMillis()

        sb.append("========== Crash Report ==========\n")
        sb.append("Time: ${dateFormat.format(Date(timestamp))}\n")
        sb.append("App Version: ${getAppVersion()}\n")
        sb.append("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        sb.append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
        sb.append("==================================\n\n")

        sb.append("Exception: ${exception.javaClass.name}\n")
        sb.append("Message: ${exception.message}\n\n")

        sb.append("Stack Trace:\n")
        sb.append(Log.getStackTraceString(exception))

        return sb.toString()
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.versionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }
}
