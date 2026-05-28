package com.antgskds.calendarassistant.core.center

import android.content.Context
import android.os.Environment
import com.antgskds.calendarassistant.core.util.AppLogger
import com.antgskds.calendarassistant.data.node.diagnostic.WillDoDownloadLogNode
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DiagnosticLogCenter(private val context: Context) {
    private val appContext = context.applicationContext
    private val exportNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    private val logTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

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

    private fun buildMergedLogText(minutes: Int?): String {
        val now = LocalDateTime.now()
        val cutoff = minutes?.let { now.minusMinutes(it.toLong()) }
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
            appendLine("Notice: logs may contain recognized text, prompts, and model responses.")
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
        }
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
}
