package com.antgskds.calendarassistant.core.util

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import com.antgskds.calendarassistant.data.node.diagnostic.WillDoDownloadLogNode
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

object ExceptionLogStore {
    private const val TAG = "ExceptionLogStore"
    private const val LOG_DIR = WillDoDownloadLogNode.CRASH_DIR
    private const val LOG_FILE_NAME = WillDoDownloadLogNode.CRASH_LOG_FILE
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
                val currentText = WillDoDownloadLogNode.readText(context, LOG_DIR, LOG_FILE_NAME).orEmpty()
                if (currentText.toByteArray().size + entry.toByteArray().size > MAX_LOG_BYTES) {
                    Log.w(TAG, "Log size limit reached, skip append")
                    return AppendResult.SIZE_LIMIT
                }
                if (WillDoDownloadLogNode.appendText(context, LOG_DIR, LOG_FILE_NAME, entry)) {
                    AppendResult.SUCCESS
                } else {
                    AppendResult.FAILED
                }
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
                WillDoDownloadLogNode.delete(context, LOG_DIR, LOG_FILE_NAME)
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
        val crashDir = File(downloadDir, "${WillDoDownloadLogNode.ROOT_DIR}/$LOG_DIR")
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

}
