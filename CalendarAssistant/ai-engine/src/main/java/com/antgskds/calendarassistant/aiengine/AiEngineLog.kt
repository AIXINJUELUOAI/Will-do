package com.antgskds.calendarassistant.aiengine

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object AiEngineLog {
    private const val PREFS_NAME = "local_model_log"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_DATE = "date"
    private const val FILE_NAME = "local-model.log.txt"
    private const val TAG = "AiEngineLog"

    private val lineFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    fun setEnabled(context: Context, enabled: Boolean) {
        val appContext = context.applicationContext
        prefs(appContext).edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) {
            write(appContext, "INFO", "AiEngineLog", "local model log enabled")
        }
    }

    fun isEnabled(context: Context): Boolean {
        return prefs(context.applicationContext).getBoolean(KEY_ENABLED, false)
    }

    fun write(context: Context, level: String, tag: String, message: String) {
        val appContext = context.applicationContext
        if (!isEnabled(appContext)) return
        synchronized(this) {
            runCatching {
                val reset = rotateIfNeeded(appContext)
                val line = buildString {
                    append(LocalDateTime.now().format(lineFormatter))
                    append(' ')
                    append(level)
                    append('/')
                    append(tag)
                    append(": ")
                    append(message.replace('\r', ' ').trimEnd())
                    append('\n')
                }
                appendLine(appContext, line, reset)
            }.onFailure { e ->
                Log.e(TAG, "write log failed", e)
            }
        }
    }

    fun resetForToday(context: Context) {
        val appContext = context.applicationContext
        if (!isEnabled(appContext)) return
        synchronized(this) {
            runCatching {
                prefs(appContext).edit().putString(KEY_DATE, LocalDate.now().toString()).apply()
                val line = "${LocalDateTime.now().format(lineFormatter)} INFO/$TAG: daily log reset\n"
                appendLine(appContext, line, reset = true)
            }.onFailure { e ->
                Log.e(TAG, "reset log failed", e)
            }
        }
    }

    private fun rotateIfNeeded(context: Context): Boolean {
        val today = LocalDate.now().toString()
        val prefs = prefs(context)
        val lastDate = prefs.getString(KEY_DATE, null)
        val reset = lastDate != today
        if (reset) {
            prefs.edit().putString(KEY_DATE, today).apply()
        }
        return reset
    }

    private fun appendLine(context: Context, line: String, reset: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            writeToAppExternalDownloads(context, line, reset)
            return
        }

        runCatching {
            val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "WillDo")
            publicDir.mkdirs()
            writeLegacyFile(File(publicDir, FILE_NAME), line, reset)
        }.getOrElse {
            val fallback = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "WillDo/$FILE_NAME")
            writeLegacyFile(fallback, line, reset)
        }
    }

    private fun writeLegacyFile(target: File, line: String, reset: Boolean) {
        target.parentFile?.mkdirs()
        if (reset) {
            target.writeText("# Will do local model log ${LocalDate.now()}\n")
        }
        target.appendText(line)
    }

    private fun writeToAppExternalDownloads(context: Context, line: String, reset: Boolean) {
        val target = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "WillDo/$FILE_NAME")
        writeLegacyFile(target, line, reset)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
