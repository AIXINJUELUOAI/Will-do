package com.antgskds.calendarassistant.core.center

import android.content.Context
import android.os.Environment
import android.os.Process as AndroidProcess
import com.antgskds.calendarassistant.core.util.AppLogger
import com.antgskds.calendarassistant.core.util.PrivilegeManager
import com.antgskds.calendarassistant.data.node.diagnostic.WillDoDownloadLogNode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class DiagnosticLogCenter(private val context: Context) {
    private val appContext = context.applicationContext
    private val exportNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    private val logTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
    private val logcatTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    suspend fun migrateLegacyLogs(): List<String> = withContext(Dispatchers.IO) {
        val result = WillDoDownloadLogNode.migrateLegacyLogs(appContext)
        AppLogger.i("DiagnosticLogCenter", "legacy log migration result=${result.joinToString()}")
        result
    }

    suspend fun exportLogBundle(minutes: Int? = null): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            migrateLegacyLogs()
            val timestamp = LocalDateTime.now().format(exportNameFormatter)
            val suffix = minutes?.let { "_${it}min" } ?: "_all"
            val fileName = "willdo_log_${timestamp}$suffix.txt"
            val text = buildMergedLogText(minutes)
            check(WillDoDownloadLogNode.writeText(appContext, WillDoDownloadLogNode.EXPORT_DIR, fileName, text)) {
                "写入日志失败"
            }
            AppLogger.i("DiagnosticLogCenter", "exported diagnostic log file=$fileName minutes=${minutes ?: -1}")
            WillDoDownloadLogNode.publicPath(WillDoDownloadLogNode.EXPORT_DIR, fileName)
        }
    }

    fun logDirectoryHint(): String {
        return "${Environment.DIRECTORY_DOWNLOADS}/${WillDoDownloadLogNode.ROOT_DIR}"
    }

    private suspend fun buildMergedLogText(minutes: Int?): String {
        val now = LocalDateTime.now()
        val cutoff = minutes?.let { now.minusMinutes(it.toLong()) }
        val logcatDump = collectLogcatDump(minutes)
        val sections = listOf(
            LogSection(
                title = "APP",
                category = WillDoDownloadLogNode.APP_LOG_DIR,
                fileName = WillDoDownloadLogNode.APP_LOG_FILE,
                emptyMessage = "No app runtime log found."
            ),
            LogSection(
                title = "CRASH",
                category = WillDoDownloadLogNode.CRASH_DIR,
                fileName = WillDoDownloadLogNode.CRASH_LOG_FILE,
                emptyMessage = "No crash log found."
            ),
            LogSection(
                title = "AI_ENGINE",
                category = WillDoDownloadLogNode.AI_ENGINE_DIR,
                fileName = WillDoDownloadLogNode.AI_ENGINE_LOG_FILE,
                emptyMessage = "No local recognition log found."
            )
        )
        return buildString {
            appendLine("Will do diagnostic log")
            appendLine("Export range: ${minutes?.let { "最近 $it 分钟" } ?: "全部"}")
            appendLine("Exported at: $now")
            appendLine("Package: ${appContext.packageName}")
            appendLine("UID: ${appContext.applicationInfo.uid}")
            appendLine("Notice: logs may contain recognized text, prompts, model responses, API responses, and runtime logcat lines.")
            appendLine()
            sections.forEach { section ->
                appendLine("===== ${section.title} / ${section.fileName} =====")
                val raw = WillDoDownloadLogNode.readText(appContext, section.category, section.fileName).orEmpty()
                val filtered = filterLogByTime(raw, cutoff).trimEnd()
                if (filtered.isBlank()) {
                    appendLine(section.emptyMessage)
                } else {
                    filtered.lineSequence().forEach { line ->
                        appendLine("[${section.title}] $line")
                    }
                }
                appendLine()
            }
            appendLine("===== LOGCAT / ${logcatDump.source} =====")
            appendLine("Command: ${logcatDump.command}")
            if (logcatDump.note.isNotBlank()) {
                appendLine("Note: ${logcatDump.note}")
            }
            if (logcatDump.text.isBlank()) {
                appendLine("No package logcat lines found or logcat is unavailable.")
            } else {
                logcatDump.text.trimEnd().lineSequence().forEach { line ->
                    appendLine("[LOGCAT] $line")
                }
            }
            appendLine()
        }
    }

    private suspend fun collectLogcatDump(minutes: Int?): LogcatDump {
        val since = minutes?.let { LocalDateTime.now().minusMinutes(it.toLong()).format(logcatTimeFormatter) }
        val uid = appContext.applicationInfo.uid
        val pid = AndroidProcess.myPid()
        val baseArgs = buildLogcatArgs(since)
        val uidArgs = baseArgs + listOf("--uid=$uid")
        val pidArgs = baseArgs + listOf("--pid=$pid")

        PrivilegeManager.refreshPrivilege()
        if (PrivilegeManager.hasPrivilege) {
            runLogcatPrivileged(uidArgs)?.let { result ->
                if (result.success) {
                    return LogcatDump(
                        source = "logcat uid=$uid via ${PrivilegeManager.privilegeType}",
                        command = result.command,
                        text = result.output,
                        note = "按应用 UID 过滤，包含同一包名/UID 下的运行时日志。"
                    )
                }
            }
        }

        runLogcatLocal(uidArgs)?.let { result ->
            if (result.success) {
                return LogcatDump(
                    source = "logcat uid=$uid",
                    command = result.command,
                    text = result.output,
                    note = "按应用 UID 过滤；普通权限下 Android 可能只返回本应用可见日志。"
                )
            }
        }

        runLogcatLocal(pidArgs)?.let { result ->
            if (result.success) {
                return LogcatDump(
                    source = "logcat pid=$pid",
                    command = result.command,
                    text = result.output,
                    note = "设备不支持或不允许 UID 过滤，已退回当前进程 PID。历史进程日志可能不完整。"
                )
            }
        }

        val rawResult = runLogcatLocal(baseArgs)
        val filtered = rawResult?.output?.let { filterRawLogcat(it, pid) }.orEmpty()
        return LogcatDump(
            source = "logcat package fallback",
            command = rawResult?.command ?: baseArgs.joinCommand(),
            text = filtered,
            note = rawResult?.error?.ifBlank {
                "设备不支持 UID/PID 过滤，已按包名文本和当前 PID 尽量过滤。"
            } ?: "logcat command failed."
        )
    }

    private fun buildLogcatArgs(since: String?): List<String> {
        return buildList {
            add("logcat")
            add("-d")
            add("-v")
            add("threadtime")
            if (since != null) {
                add("-T")
                add(since)
            }
        }
    }

    private suspend fun runLogcatPrivileged(args: List<String>): CommandResult? {
        return when (PrivilegeManager.privilegeType) {
            PrivilegeManager.PrivilegeType.ROOT -> runLogcatRoot(args)
            PrivilegeManager.PrivilegeType.SHIZUKU -> runLogcatShizuku(args)
            PrivilegeManager.PrivilegeType.NONE -> null
        }
    }

    private suspend fun runLogcatRoot(args: List<String>): CommandResult? = withContext(Dispatchers.IO) {
        val command = args.joinCommand()
        var process: Process? = null
        withTimeoutOrNull(LOGCAT_TIMEOUT_MS) {
            runCatching {
                val currentProcess = ProcessBuilder(listOf("su", "-c", command))
                    .redirectErrorStream(true)
                    .start()
                process = currentProcess
                val output = currentProcess.inputStream.bufferedReader().use { it.readText() }
                val finished = currentProcess.waitFor(1, TimeUnit.SECONDS)
                val exitCode = if (finished) currentProcess.exitValue() else -1
                if (!finished) currentProcess.destroy()
                CommandResult(
                    success = finished && exitCode == 0,
                    command = command,
                    output = if (finished && exitCode == 0) output else "",
                    error = if (finished && exitCode == 0) "" else output.ifBlank { "exitCode=$exitCode" }
                )
            }.getOrElse { error ->
                CommandResult(
                    success = false,
                    command = command,
                    output = "",
                    error = error.message.orEmpty()
                )
            }
        }.also {
            if (it == null) process?.destroy()
        }
    }

    private suspend fun runLogcatShizuku(args: List<String>): CommandResult? {
        val command = args.joinCommand()
        var handle: PrivilegeManager.ProcessHandle? = null
        return withTimeoutOrNull(LOGCAT_TIMEOUT_MS) {
            runCatching {
                val currentHandle = PrivilegeManager.startPrivilegedProcess(args.toTypedArray())
                    ?: return@runCatching CommandResult(
                        success = false,
                        command = command,
                        output = "",
                        error = "Unable to start Shizuku process"
                    )
                handle = currentHandle
                val output = currentHandle.inputStream.bufferedReader().use { it.readText() }
                val error = currentHandle.errorStream.bufferedReader().use { it.readText() }
                CommandResult(
                    success = error.isBlank(),
                    command = command,
                    output = output,
                    error = error
                )
            }.getOrElse { error ->
                CommandResult(
                    success = false,
                    command = command,
                    output = "",
                    error = error.message.orEmpty()
                )
            }
        }.also {
            handle?.destroy()
        }
    }

    private suspend fun runLogcatLocal(args: List<String>): CommandResult? = withContext(Dispatchers.IO) {
        val command = args.joinCommand()
        var process: Process? = null
        withTimeoutOrNull(LOGCAT_TIMEOUT_MS) {
            runCatching {
                val currentProcess = ProcessBuilder(args)
                    .redirectErrorStream(true)
                    .start()
                process = currentProcess
                val output = currentProcess.inputStream.bufferedReader().use { it.readText() }
                val finished = currentProcess.waitFor(1, TimeUnit.SECONDS)
                val exitCode = if (finished) currentProcess.exitValue() else -1
                if (!finished) currentProcess.destroy()
                CommandResult(
                    success = finished && exitCode == 0,
                    command = command,
                    output = if (finished && exitCode == 0) output else "",
                    error = if (finished && exitCode == 0) "" else output.ifBlank { "exitCode=$exitCode" }
                )
            }.getOrElse { error ->
                CommandResult(
                    success = false,
                    command = command,
                    output = "",
                    error = error.message.orEmpty()
                )
            }
        }.also {
            if (it == null) process?.destroy()
        }
    }

    private fun filterRawLogcat(raw: String, currentPid: Int): String {
        val packageName = appContext.packageName
        return raw.lineSequence()
            .filter { line ->
                line.contains(packageName) || line.threadtimePid() == currentPid
            }
            .joinToString(separator = "\n")
    }

    private fun String.threadtimePid(): Int? {
        val parts = trimStart().split(Regex("\\s+"), limit = 5)
        if (parts.size < 3) return null
        return parts[2].toIntOrNull()
    }

    private fun List<String>.joinCommand(): String = joinToString(" ") { it.shellQuote() }

    private fun String.shellQuote(): String {
        if (matches(Regex("[A-Za-z0-9_@%+=:,./-]+"))) return this
        return "'" + replace("'", "'\\''") + "'"
    }

    private fun filterLogByTime(raw: String, cutoff: LocalDateTime?): String {
        if (raw.isBlank() || cutoff == null) return raw
        return raw.lineSequence()
            .filter { line ->
                val time = parseLineTime(line)
                time == null || !time.isBefore(cutoff)
            }
            .joinToString(separator = "\n", postfix = "\n")
    }

    private fun parseLineTime(line: String): LocalDateTime? {
        val candidate = when {
            line.length >= 24 && line[0] == '[' -> line.substring(1, 24)
            line.length >= 23 -> line.substring(0, 23)
            else -> return null
        }
        return runCatching { LocalDateTime.parse(candidate, logTimeFormatter) }.getOrNull()
    }

    private data class LogSection(
        val title: String,
        val category: String,
        val fileName: String,
        val emptyMessage: String
    )

    private data class LogcatDump(
        val source: String,
        val command: String,
        val text: String,
        val note: String
    )

    private data class CommandResult(
        val success: Boolean,
        val command: String,
        val output: String,
        val error: String
    )

    companion object {
        private const val LOGCAT_TIMEOUT_MS = 15_000L
    }
}
