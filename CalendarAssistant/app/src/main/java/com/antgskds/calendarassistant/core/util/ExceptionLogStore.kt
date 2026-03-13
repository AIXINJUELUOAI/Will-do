package com.antgskds.calendarassistant.core.util

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

object ExceptionLogStore {
    private const val TAG = "ExceptionLogStore"
    private const val LOG_DIR = "CrashLogs"
    private const val LOG_FILE_NAME = "exception.log"
    private const val PREF_NAME = "exception_log_prefs"
    private const val KEY_LAST_DATE = "last_log_date"
    private const val MAX_LOG_BYTES = 512 * 1024L

    private val lock = Any()
    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val timestampFormatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())

    private enum class AppendResult {
        SUCCESS,
        SIZE_LIMIT,
        FAILED
    }

    fun append(context: Context, tag: String, message: String, throwable: Throwable? = null) {
        val entry = buildEntry(tag, message, throwable)
        synchronized(lock) {
            val today = LocalDate.now().format(dateFormatter)
            val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            val lastDate = prefs.getString(KEY_LAST_DATE, null)
            if (lastDate != today) {
                clearLogFiles(context)
                prefs.edit().putString(KEY_LAST_DATE, today).apply()
            }

            when (appendToDownload(context, entry)) {
                AppendResult.SUCCESS -> return
                AppendResult.SIZE_LIMIT -> return
                AppendResult.FAILED -> appendToInternal(context, entry)
            }
        }
    }

    private fun buildEntry(tag: String, message: String, throwable: Throwable?): String {
        val timestamp = timestampFormatter.format(Date())
        val sb = StringBuilder()
        sb.append('[').append(timestamp).append(']')
            .append('[').append(tag).append("] ")
            .append(message)
            .append('\n')
        if (throwable != null) {
            sb.append(Log.getStackTraceString(throwable)).append('\n')
        }
        return sb.toString()
    }

    private fun appendToDownload(context: Context, entry: String): AppendResult {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = context.contentResolver
                val uri = getOrCreateLogUri(context) ?: return AppendResult.FAILED
                val size = queryContentSize(resolver, uri)
                val bytes = entry.toByteArray()
                if (size >= 0 && size + bytes.size > MAX_LOG_BYTES) {
                    Log.w(TAG, "Log size limit reached, skip append")
                    return AppendResult.SIZE_LIMIT
                }

                resolver.openOutputStream(uri, "wa")?.use { outputStream ->
                    outputStream.write(bytes)
                } ?: return AppendResult.FAILED

                AppendResult.SUCCESS
            } else {
                val file = getLegacyLogFile()
                val bytes = entry.toByteArray()
                if (file.exists() && file.length() + bytes.size > MAX_LOG_BYTES) {
                    Log.w(TAG, "Log size limit reached, skip append")
                    return AppendResult.SIZE_LIMIT
                }
                FileOutputStream(file, true).use { fos ->
                    fos.write(bytes)
                }
                AppendResult.SUCCESS
            }
        } catch (e: Exception) {
            Log.e(TAG, "Append log failed", e)
            AppendResult.FAILED
        }
    }

    private fun appendToInternal(context: Context, entry: String) {
        try {
            val file = getInternalLogFile(context)
            val bytes = entry.toByteArray()
            if (file.exists() && file.length() + bytes.size > MAX_LOG_BYTES) {
                Log.w(TAG, "Internal log size limit reached, skip append")
                return
            }
            FileOutputStream(file, true).use { fos ->
                fos.write(bytes)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Append internal log failed", e)
        }
    }

    private fun clearLogFiles(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val resolver = context.contentResolver
                val uri = queryLogUri(context)
                if (uri != null) {
                    resolver.delete(uri, null, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Clear download log failed", e)
            }
        } else {
            try {
                val file = getLegacyLogFile()
                if (file.exists()) file.delete()
            } catch (e: Exception) {
                Log.e(TAG, "Clear legacy log failed", e)
            }
        }

        try {
            val internalFile = getInternalLogFile(context)
            if (internalFile.exists()) internalFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "Clear internal log failed", e)
        }
    }

    private fun getLegacyLogFile(): File {
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val crashDir = File(downloadDir, LOG_DIR)
        if (!crashDir.exists()) {
            crashDir.mkdirs()
        }
        return File(crashDir, LOG_FILE_NAME)
    }

    private fun getInternalLogFile(context: Context): File {
        val crashDir = File(context.filesDir, LOG_DIR)
        if (!crashDir.exists()) {
            crashDir.mkdirs()
        }
        return File(crashDir, LOG_FILE_NAME)
    }

    private fun queryLogUri(context: Context): android.net.Uri? {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val selectionArgs = arrayOf(LOG_FILE_NAME, getRelativePath())

        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(0)
                return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id)
            }
        }
        return null
    }

    private fun getOrCreateLogUri(context: Context): android.net.Uri? {
        val existing = queryLogUri(context)
        if (existing != null) return existing

        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, LOG_FILE_NAME)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            put(MediaStore.MediaColumns.RELATIVE_PATH, getRelativePath())
        }
        return context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
    }

    private fun getRelativePath(): String {
        return "${Environment.DIRECTORY_DOWNLOADS}/$LOG_DIR/"
    }

    private fun queryContentSize(resolver: android.content.ContentResolver, uri: android.net.Uri): Long {
        val projection = arrayOf(MediaStore.MediaColumns.SIZE)
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getLong(0)
            }
        }
        return -1L
    }
}
