package com.antgskds.calendarassistant.core.util

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.data.node.diagnostic.WillDoDownloadLogNode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object AppLogger {
    private const val PREF_NAME = "app_logger_prefs"
    private const val KEY_LAST_DATE = "last_log_date"
    private const val MAX_LOG_BYTES = 1024 * 1024

    private val lock = Any()
    private val lineFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    @Volatile private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        i("AppLogger", "app logger initialized")
    }

    fun d(tag: String, message: String) = write(Log.DEBUG, "DEBUG", tag, message, null)

    fun i(tag: String, message: String) = write(Log.INFO, "INFO", tag, message, null)

    fun w(tag: String, message: String, throwable: Throwable? = null) = write(Log.WARN, "WARN", tag, message, throwable)

    fun e(tag: String, message: String, throwable: Throwable? = null) = write(Log.ERROR, "ERROR", tag, message, throwable)

    private fun write(priority: Int, level: String, tag: String, message: String, throwable: Throwable?) {
        if (throwable == null) {
            Log.println(priority, tag, message)
        } else {
            Log.println(priority, tag, "$message\n${Log.getStackTraceString(throwable)}")
        }
        val context = appContext ?: return
        synchronized(lock) {
            runCatching {
                rotateIfNeeded(context)
                val line = buildString {
                    append(LocalDateTime.now().format(lineFormatter))
                    append(' ')
                    append(level)
                    append('/')
                    append(tag)
                    append(": ")
                    append(message.replace('\r', ' ').trimEnd())
                    append('\n')
                    if (throwable != null) {
                        append(Log.getStackTraceString(throwable))
                        append('\n')
                    }
                }
                val current = WillDoDownloadLogNode.readText(context, WillDoDownloadLogNode.APP_LOG_DIR, WillDoDownloadLogNode.APP_LOG_FILE).orEmpty()
                if (current.toByteArray().size + line.toByteArray().size > MAX_LOG_BYTES) {
                    WillDoDownloadLogNode.writeText(
                        context,
                        WillDoDownloadLogNode.APP_LOG_DIR,
                        WillDoDownloadLogNode.APP_LOG_FILE,
                        "# Will do app log ${LocalDate.now()}\n"
                    )
                }
                WillDoDownloadLogNode.appendText(context, WillDoDownloadLogNode.APP_LOG_DIR, WillDoDownloadLogNode.APP_LOG_FILE, line)
            }.onFailure {
                Log.e("AppLogger", "write app log failed", it)
            }
        }
    }

    private fun rotateIfNeeded(context: Context) {
        val today = LocalDate.now().toString()
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val lastDate = prefs.getString(KEY_LAST_DATE, null)
        if (lastDate != today) {
            WillDoDownloadLogNode.writeText(
                context,
                WillDoDownloadLogNode.APP_LOG_DIR,
                WillDoDownloadLogNode.APP_LOG_FILE,
                "# Will do app log $today\n"
            )
            prefs.edit().putString(KEY_LAST_DATE, today).apply()
        }
    }
}
