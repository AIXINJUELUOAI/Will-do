package com.antgskds.calendarassistant.feature.quickmemo.application

import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoReminderEntity
import com.antgskds.calendarassistant.feature.linkanalysis.domain.*
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

data class QuickMemoExportSnapshot(val memo: QuickMemoEntity, val summary: LinkSummaryData?, val reminders: List<QuickMemoReminderEntity> = emptyList())
data class QuickMemoExportFile(val file: File, val mimeType: String)

object QuickMemoExportWriter {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    private fun time(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(dateFormat)

    fun fileName(memo: QuickMemoEntity): String {
        val title = memo.title.trim().ifBlank { "随口记-" + time(memo.createdAt).replace(':', '-') }
        var safe = title.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]+"), "_").trim(' ', '.')
            .take(ConfigCatalog.QUICK_MEMO_EXPORT_NAME_MAX_CHARS)
        if (safe.lastOrNull()?.isHighSurrogate() == true) safe = safe.dropLast(1)
        if (safe.isBlank()) safe = "随口记"
        if (safe.uppercase().matches(Regex("(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])"))) safe = "随口记-$safe"
        return safe
    }

    fun attachmentFiles(memo: QuickMemoEntity): List<Pair<String, File>> = buildList {
        fun addAttachment(path: String?, stem: String) {
            if (path.isNullOrBlank()) return
            val file = File(path)
            require(file.isFile && file.length() > 0) { "${if (stem == "image") "图片" else "录音"}附件不存在，无法完整导出" }
            val extension = file.extension.lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,8}")) }.orEmpty()
            add(("attachments/$stem" + if (extension.isBlank()) "" else ".$extension") to file)
        }
        addAttachment(memo.imagePath, "image")
        addAttachment(memo.audioPath, "audio")
    }

    fun markdown(snapshot: QuickMemoExportSnapshot, attachments: List<Pair<String, File>>): String = buildString {
        val memo = snapshot.memo
        append("# ").append(memo.title.trim().replace(Regex("[\\r\\n]+"), " ").ifBlank { "随口记" }).append("\n\n")
        append("创建时间：").append(time(memo.createdAt)).append("\n\n")
        append(memo.bodyText.trim()).append("\n\n")
        memo.sourceUrl?.takeIf { it.isNotBlank() && !memo.bodyText.contains(it) }?.let { append("来源：").append(it).append("\n\n") }
        snapshot.summary?.let { data ->
            val view = LinkSummaryPresentationPolicy.present(data)
            if (view.markdown.isNotBlank()) append("## 摘要\n\n").append(view.markdown).append("\n\n")
            view.warnings.forEach { append("> ").append(it.replace("\n", " ")).append('\n') }
            if (view.warnings.isNotEmpty()) append('\n')
            val transcript = LinkTranscriptPolicy.paragraphs(data.transcript)
            if (transcript.isNotBlank() && !memo.bodyText.contains(transcript))
                append("## 转写原文\n\n").append(transcript).append("\n\n")
        }
        if (attachments.isNotEmpty()) {
            append("## 附件\n\n")
            attachments.forEach { (name, _) ->
                if (name.startsWith("attachments/image")) append("![图片](").append(name).append(")\n\n")
                else append("[录音](").append(name).append(")\n\n")
            }
        }
        if (snapshot.reminders.isNotEmpty()) {
            append("## 提醒\n\n")
            snapshot.reminders.sortedBy { it.triggerAt }.forEach {
                append("- ").append(time(it.triggerAt))
                if (it.rrule.isNotBlank()) append("（重复规则：").append(it.rrule).append('）')
                append('\n')
            }
        }
    }.trimEnd() + "\n"

    fun write(snapshot: QuickMemoExportSnapshot, directory: File): QuickMemoExportFile {
        directory.mkdirs()
        val attachments = attachmentFiles(snapshot.memo)
        val name = fileName(snapshot.memo)
        val target = File(directory, name + if (attachments.isEmpty()) ".md" else ".zip")
        try {
            val markdown = markdown(snapshot, attachments).toByteArray(Charsets.UTF_8)
            if (attachments.isEmpty()) target.writeBytes(markdown)
            else ZipOutputStream(target.outputStream().buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("$name.md")); zip.write(markdown); zip.closeEntry()
                attachments.forEach { (entry, file) ->
                    zip.putNextEntry(ZipEntry(entry))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
            return QuickMemoExportFile(target, if (attachments.isEmpty()) "text/markdown" else "application/zip")
        } catch (error: Exception) { target.delete(); throw error }
    }
}
