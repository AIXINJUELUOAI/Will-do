package com.antgskds.calendarassistant.data.node.diagnostic

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object WillDoDownloadLogNode {
    const val ROOT_DIR = "WillDo"
    const val CRASH_DIR = "crash"
    const val APP_LOG_DIR = "logs"
    const val AI_ENGINE_DIR = "ai-engine"
    const val EXPORT_DIR = "exports"
    const val CRASH_LOG_FILE = "exception.log"
    const val AI_ENGINE_LOG_FILE = "local-model.log.txt"
    const val APP_LOG_FILE = "app.log"

    private const val TAG = "WillDoDownloadLogNode"
    private val migrationFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun appendText(context: Context, category: String, fileName: String, text: String, reset: Boolean = false): Boolean {
        return runCatching {
            val appContext = context.applicationContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = getOrCreateDownloadUri(appContext, category, fileName) ?: return false
                val mode = if (reset) "wt" else "wa"
                appContext.contentResolver.openOutputStream(uri, mode)?.use { output ->
                    output.write(text.toByteArray())
                } ?: return false
                true
            } else {
                val file = legacyPublicFile(category, fileName)
                file.parentFile?.mkdirs()
                if (reset) file.writeText(text) else file.appendText(text)
                true
            }
        }.onFailure { Log.e(TAG, "appendText failed category=$category file=$fileName", it) }
            .getOrDefault(false)
    }

    fun writeText(context: Context, category: String, fileName: String, text: String): Boolean {
        return appendText(context, category, fileName, text, reset = true)
    }

    fun writeBytes(context: Context, category: String, fileName: String, bytes: ByteArray): Boolean {
        return runCatching {
            val appContext = context.applicationContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = getOrCreateDownloadUri(appContext, category, fileName) ?: return false
                appContext.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                    output.write(bytes)
                } ?: return false
                true
            } else {
                val file = legacyPublicFile(category, fileName)
                file.parentFile?.mkdirs()
                file.writeBytes(bytes)
                true
            }
        }.onFailure { Log.e(TAG, "writeBytes failed category=$category file=$fileName", it) }
            .getOrDefault(false)
    }

    fun readText(context: Context, category: String, fileName: String): String? {
        return runCatching {
            val appContext = context.applicationContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = queryDownloadUri(appContext, category, fileName) ?: return null
                appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } else {
                legacyPublicFile(category, fileName).takeIf { it.exists() }?.readText()
            }
        }.onFailure { Log.e(TAG, "readText failed category=$category file=$fileName", it) }
            .getOrNull()
    }

    fun delete(context: Context, category: String, fileName: String): Boolean {
        return runCatching {
            val appContext = context.applicationContext
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val uri = queryDownloadUri(appContext, category, fileName) ?: return true
                appContext.contentResolver.delete(uri, null, null) > 0
            } else {
                val file = legacyPublicFile(category, fileName)
                !file.exists() || file.delete()
            }
        }.onFailure { Log.e(TAG, "delete failed category=$category file=$fileName", it) }
            .getOrDefault(false)
    }

    fun queryDownloadUri(context: Context, category: String, fileName: String): Uri? {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return legacyPublicFile(category, fileName).takeIf { it.exists() }?.let { Uri.fromFile(it) }
        }
        val resolver = appContext.contentResolver
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val selectionArgs = arrayOf(fileName, relativePath(category))

        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0))
            }
        }
        return null
    }

    fun getOrCreateDownloadUri(context: Context, category: String, fileName: String): Uri? {
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val file = legacyPublicFile(category, fileName)
            file.parentFile?.mkdirs()
            return Uri.fromFile(file)
        }
        queryDownloadUri(appContext, category, fileName)?.let { return it }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType(fileName))
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath(category))
        }
        return appContext.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
    }

    fun migrateLegacyLogs(context: Context): List<String> {
        val appContext = context.applicationContext
        val results = mutableListOf<String>()
        results += migrateLegacyCrashLog(appContext)
        results += migrateLegacyAiEngineLog(appContext)
        return results.filter { it.isNotBlank() }
    }

    private fun migrateLegacyCrashLog(context: Context): String {
        val legacyText = readLegacyCrashLog(context) ?: return "未发现旧崩溃日志"
        return if (appendMigratedText(context, CRASH_DIR, CRASH_LOG_FILE, legacyText, "Download/CrashLogs/exception.log")) {
            if (deleteLegacyCrashLog(context)) "已迁移旧崩溃日志" else "已迁移旧崩溃日志，旧文件删除失败"
        } else {
            "旧崩溃日志迁移失败"
        }
    }

    private fun migrateLegacyAiEngineLog(context: Context): String {
        val legacyFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "$ROOT_DIR/$AI_ENGINE_LOG_FILE")
        if (!legacyFile.exists()) return "未发现旧本地识别日志"
        val legacyText = runCatching { legacyFile.readText() }.getOrNull().orEmpty()
        if (legacyText.isBlank()) {
            runCatching { legacyFile.delete() }
            return "旧本地识别日志为空"
        }
        return if (appendMigratedText(context, AI_ENGINE_DIR, AI_ENGINE_LOG_FILE, legacyText, legacyFile.absolutePath)) {
            if (runCatching { legacyFile.delete() }.getOrDefault(false)) "已迁移旧本地识别日志" else "已迁移旧本地识别日志，旧文件删除失败"
        } else {
            "旧本地识别日志迁移失败"
        }
    }

    private fun appendMigratedText(context: Context, category: String, fileName: String, text: String, source: String): Boolean {
        val before = readText(context, category, fileName).orEmpty()
        val header = buildString {
            if (before.isNotBlank()) append('\n')
            append("===== migrated from ")
            append(source)
            append(" at ")
            append(LocalDateTime.now().format(migrationFormatter))
            append(" =====\n")
        }
        if (!appendText(context, category, fileName, header + text.trimEnd() + "\n")) return false
        val after = readText(context, category, fileName).orEmpty()
        return after.length >= before.length + text.trimEnd().length
    }

    private fun readLegacyCrashLog(context: Context): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            queryLegacyCrashUri(context)?.let { uri ->
                return runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()?.takeIf { it.isNotBlank() }
            }
        }
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CrashLogs/$CRASH_LOG_FILE")
        return runCatching { file.takeIf { it.exists() }?.readText() }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun deleteLegacyCrashLog(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val uri = queryLegacyCrashUri(context) ?: return true
            return runCatching { context.contentResolver.delete(uri, null, null) > 0 }.getOrDefault(false)
        }
        val file = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "CrashLogs/$CRASH_LOG_FILE")
        return runCatching { !file.exists() || file.delete() }.getOrDefault(false)
    }

    private fun queryLegacyCrashUri(context: Context): Uri? {
        val resolver = context.contentResolver
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?"
        val selectionArgs = arrayOf(CRASH_LOG_FILE, "${Environment.DIRECTORY_DOWNLOADS}/CrashLogs/")
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                return ContentUris.withAppendedId(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cursor.getLong(0))
            }
        }
        return null
    }

    fun relativePath(category: String): String {
        return "${Environment.DIRECTORY_DOWNLOADS}/$ROOT_DIR/$category/"
    }

    fun publicPath(category: String, fileName: String): String {
        return "${Environment.DIRECTORY_DOWNLOADS}/$ROOT_DIR/$category/$fileName"
    }

    private fun legacyPublicFile(category: String, fileName: String): File {
        return File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "$ROOT_DIR/$category/$fileName")
    }

    private fun mimeType(fileName: String): String {
        return when {
            fileName.endsWith(".zip", ignoreCase = true) -> "application/zip"
            fileName.endsWith(".json", ignoreCase = true) -> "application/json"
            else -> "text/plain"
        }
    }
}
