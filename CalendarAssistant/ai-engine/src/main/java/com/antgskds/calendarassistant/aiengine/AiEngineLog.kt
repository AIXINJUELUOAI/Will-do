package com.antgskds.calendarassistant.aiengine

import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
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
    private const val ROOT_DIR = "WillDo"
    private const val AI_ENGINE_DIR = "ai-engine"

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
            writeToPublicDownloads(context, line, reset)
            return
        }

        runCatching {
            val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$ROOT_DIR/$AI_ENGINE_DIR")
            publicDir.mkdirs()
            writeLegacyFile(File(publicDir, FILE_NAME), line, reset)
        }.getOrElse {
            val fallback = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "$ROOT_DIR/$AI_ENGINE_DIR/$FILE_NAME")
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

    private fun writeToPublicDownloads(context: Context, line: String, reset: Boolean) {
        val resolver = context.contentResolver
        val uri = queryPublicLogUri(context) ?: run {
            val values = android.content.ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath())
            }
            resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        } ?: error("create public log uri failed")

        if (reset) {
            resolver.openOutputStream(uri, "wt")?.use { output ->
                output.write("# Will do local model log ${LocalDate.now()}\n".toByteArray())
            } ?: error("open public log uri failed")
        }
        resolver.openOutputStream(uri, "wa")?.use { output ->
            output.write(line.toByteArray())
        } ?: error("open public log uri failed")
    }

    private fun queryPublicLogUri(context: Context): android.net.Uri? {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val selectionArgs = arrayOf(FILE_NAME, relativePath())
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return android.content.ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0))
            }
        }
        return null
    }

    private fun relativePath(): String {
        return "${Environment.DIRECTORY_DOWNLOADS}/$ROOT_DIR/$AI_ENGINE_DIR/"
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
