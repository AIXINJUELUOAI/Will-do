package com.antgskds.calendarassistant.shared.util

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.feature.settings.data.SettingsDataSource
import com.antgskds.calendarassistant.feature.settings.diagnostics.data.WillDoDownloadLogNode
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import java.io.File
import java.io.RandomAccessFile
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** 应用日志统一按天落盘，系统 Logcat 继续用于即时调试。 */
object AppLogger {
    private val lock = Any()
    private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    @Volatile private var appContext: Context? = null
    @Volatile private var enabled = true

    fun init(context: Context) = synchronized(lock) {
        appContext = context.applicationContext
        enabled = SettingsDataSource(context).loadSettings().autoRecordLogs
        // 子进程读取同一个标记，开关无需等待语音进程重启即可生效。
        if (android.app.Application.getProcessName() != context.packageName) return@synchronized
        setEnabled(enabled)
        runCatching {
            cleanExpired(directory(context), LocalDate.now())
            migrateLegacyLogs(context)
        }
            .onFailure { Log.e("AppLogger", "log cleanup failed", it) }
        Unit
    }
    fun setEnabled(value: Boolean) = synchronized(lock) {
        appContext?.let { context ->
            val marker = File(context.filesDir, "diagnostics-recording-disabled")
            if (value) check(!marker.exists() || marker.delete()) else {
                check(marker.exists() || marker.createNewFile())
            }
        }
        enabled = value
    }
    fun d(tag: String, message: String, throwable: Throwable? = null): Int = write(Log.DEBUG, "DEBUG", tag, message, throwable)
    fun i(tag: String, message: String, throwable: Throwable? = null): Int = write(Log.INFO, "INFO", tag, message, throwable)
    fun w(tag: String, message: String, throwable: Throwable? = null): Int = write(Log.WARN, "WARN", tag, message, throwable)
    fun w(tag: String, throwable: Throwable): Int = w(tag, throwable.toString(), throwable)
    fun e(tag: String, message: String, throwable: Throwable? = null): Int = write(Log.ERROR, "ERROR", tag, message, throwable)
    fun getStackTraceString(throwable: Throwable?): String = Log.getStackTraceString(throwable)

    private fun write(priority: Int, level: String, tag: String, message: String, throwable: Throwable?): Int {
        val body = message.trimEnd() + (throwable?.let { "\n${Log.getStackTraceString(it)}" } ?: "")
        val result = Log.println(priority, tag, body)
        synchronized(lock) {
            val context = appContext ?: return result
            runCatching {
                val now = LocalDateTime.now()
                val dir = directory(context)
                val bytes = ("${now.format(formatter)} $level/$tag: $body\n").toByteArray(Charsets.UTF_8)
                appendRecord(dir, now.toLocalDate(), bytes, !File(context.filesDir, "diagnostics-recording-disabled").exists())
            }.onFailure { Log.e("AppLogger", "write app log failed", it) }
        }
        return result
    }

    internal fun appendRecord(dir: File, today: LocalDate, bytes: ByteArray, recording: Boolean) {
        cleanExpired(dir, today)
        if (!recording) return
        check(dir.isDirectory || dir.mkdirs()) { "Cannot create log directory" }
        RandomAccessFile(File(dir, "log-$today.log"), "rw").use { output ->
            output.channel.lock().use {
                // 满额后停止当天追加，避免清空先前故障记录。
                if (output.length() + bytes.size <= ConfigCatalog.LOG_MAX_BYTES) {
                    output.seek(output.length())
                    output.write(bytes)
                }
            }
        }
    }

    fun readText(): String = synchronized(lock) {
        val context = appContext ?: return@synchronized ""
        val dir = directory(context)
        cleanExpired(dir, LocalDate.now())
        dir.listFiles().orEmpty().filter { logDate(it) != null }.sortedBy { it.name }
            .joinToString("\n") { file ->
                RandomAccessFile(file, "r").use { input ->
                    input.channel.lock(0L, Long.MAX_VALUE, true).use {
                        ByteArray(input.length().toInt()).also { input.readFully(it) }.toString(Charsets.UTF_8)
                    }
                }
            }
    }

    fun clear(): Boolean = synchronized(lock) {
        val context = appContext ?: return@synchronized false
        directory(context).listFiles().orEmpty().filter { logDate(it) != null }
            .map { it.delete() }.all { it }
    }

    /** 只迁移自动日志，exports 中用户导出的副本不参与迁移或清理。 */
    fun migrateLegacyLogs(context: Context): List<String> = synchronized(lock) {
        val result = mutableListOf<String>()
        val privateFiles = listOf(
            File(context.filesDir, "diagnostics/app.log"),
            File(context.filesDir, "crash/exception.log"),
            File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), "WillDo/local-model.log.txt"),
        )
        privateFiles.filter { it.exists() }.forEach { file ->
            val date = java.time.Instant.ofEpochMilli(file.lastModified()).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            importLegacyText(context, file.readText(), date)
            check(file.delete()) { "Cannot remove migrated log ${file.name}" }
            result += "已合并 ${file.name}"
        }
        WillDoDownloadLogNode.runtimeLogNames(context).forEach { (category, name) ->
            WillDoDownloadLogNode.readText(context, category, name)?.let { text ->
                importLegacyText(context, text, LocalDate.now())
                check(WillDoDownloadLogNode.delete(context, category, name)) { "Cannot remove migrated public log $name" }
                result += "已合并 $category/$name"
            }
        }
        result
    }

    private fun importLegacyText(context: Context, text: String, fallbackDate: LocalDate) {
        var date = fallbackDate
        val cutoff = LocalDate.now().minusDays(ConfigCatalog.LOG_RETENTION_DAYS - 1)
        val blocks = linkedMapOf<LocalDate, StringBuilder>()
        text.lineSequence().forEach { line ->
            val match = Regex("^\\[?(\\d{4}-\\d{2}-\\d{2})[ T]").find(line)
            match?.groupValues?.get(1)?.let { value -> runCatching { LocalDate.parse(value) }.getOrNull()?.let { date = it } }
            if (!date.isBefore(cutoff) && !date.isAfter(LocalDate.now())) {
                blocks.getOrPut(date) { StringBuilder() }.appendLine(line)
            }
        }
        val dir = directory(context)
        check(dir.isDirectory || dir.mkdirs())
        blocks.forEach { (day, content) ->
            RandomAccessFile(File(dir, "log-$day.log"), "rw").use { output ->
                output.channel.lock().use {
                    val bytes = content.toString().toByteArray(Charsets.UTF_8)
                    // 删除旧文件失败后重试，不重复合并同一段记录。
                    val previous = ByteArray(output.length().toInt())
                    output.readFully(previous)
                    if (previous.toString(Charsets.UTF_8).contains(content.toString())) return@forEach
                    output.seek(output.length())
                    check(output.length() + bytes.size <= ConfigCatalog.LOG_MAX_BYTES) { "旧日志超过当天容量，保留原文件待导出" }
                    output.write(bytes)
                }
            }
        }
    }

    private fun directory(context: Context) = File(context.filesDir, "diagnostics")
    private fun logDate(file: File): LocalDate? =
        if (file.name.matches(Regex("log-\\d{4}-\\d{2}-\\d{2}\\.log")))
            runCatching { LocalDate.parse(file.name.removePrefix("log-").removeSuffix(".log")) }.getOrNull()
        else null

    private fun cleanExpired(directory: File, today: LocalDate) {
        val cutoff = today.minusDays(ConfigCatalog.LOG_RETENTION_DAYS - 1)
        directory.listFiles().orEmpty().forEach { file ->
            val date = logDate(file) ?: return@forEach
            if (date.isBefore(cutoff)) check(file.delete() || !file.exists()) { "Cannot delete ${file.name}" }
        }
    }
}
