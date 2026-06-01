package com.antgskds.calendarassistant.core.note

import android.content.Context
import android.net.Uri
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class NoteTransferData(
    val version: Int = 1,
    val title: String = "",
    val document: NoteDocument = NoteDocument(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

class NoteTransferManager(context: Context) {
    private val appContext = context.applicationContext
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun exportNote(note: NoteEntity, uri: Uri) {
        val data = NoteTransferData(
            title = note.title,
            document = note.document(),
            createdAt = note.createdAt,
            updatedAt = note.updatedAt
        )
        if (data.document.hasAttachments()) {
            exportZip(data, uri)
        } else {
            appContext.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(json.encodeToString(data).toByteArray())
            } ?: error("无法打开导出文件")
        }
    }

    fun importNote(uri: Uri): NoteTransferData {
        val type = appContext.contentResolver.getType(uri).orEmpty()
        val name = uri.lastPathSegment.orEmpty()
        return if (type.contains("zip", ignoreCase = true) || name.endsWith(".zip", ignoreCase = true)) {
            importZip(uri)
        } else {
            val content = appContext.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("无法读取便签文件")
            json.decodeFromString(content)
        }
    }

    private fun exportZip(data: NoteTransferData, uri: Uri) {
        appContext.contentResolver.openOutputStream(uri)?.use { output ->
            ZipOutputStream(output.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry("note.json"))
                zip.write(json.encodeToString(data).toByteArray())
                zip.closeEntry()

                data.document.paragraphs.forEach { paragraph ->
                    val path = paragraph.attachmentPath.takeIf { it.isNotBlank() } ?: return@forEach
                    val file = NoteAttachmentStore.fileForRelativePath(appContext, path)
                    if (!file.exists()) return@forEach
                    zip.putNextEntry(ZipEntry("attachments/${File(path).name}"))
                    file.inputStream().use { input -> input.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        } ?: error("无法打开导出文件")
    }

    private fun importZip(uri: Uri): NoteTransferData {
        val tempDir = File(appContext.cacheDir, "note_import_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            var noteJson = ""
            appContext.contentResolver.openInputStream(uri)?.use { input ->
                ZipInputStream(input.buffered()).use { zip ->
                    var entry = zip.nextEntry
                    while (entry != null) {
                        if (!entry.isDirectory) {
                            val safeName = entry.name.replace('\\', '/')
                            if (safeName == "note.json") {
                                noteJson = zip.readBytes().toString(Charsets.UTF_8)
                            } else if (safeName.startsWith("attachments/")) {
                                val file = File(tempDir, File(safeName).name)
                                file.outputStream().use { output -> zip.copyTo(output) }
                            }
                        }
                        zip.closeEntry()
                        entry = zip.nextEntry
                    }
                }
            } ?: error("无法读取便签文件")
            if (noteJson.isBlank()) error("便签包缺少 note.json")
            val data = json.decodeFromString<NoteTransferData>(noteJson)
            val document = data.document.copy(
                paragraphs = data.document.paragraphs.map { paragraph ->
                    val path = paragraph.attachmentPath
                    if (path.isBlank()) {
                        paragraph
                    } else {
                        val source = File(tempDir, File(path).name)
                        if (!source.exists()) paragraph else {
                            val relative = "notes/attachments/${UUID.randomUUID()}_${source.name}"
                            val target = File(appContext.filesDir, relative).apply { parentFile?.mkdirs() }
                            source.copyTo(target, overwrite = true)
                            paragraph.copy(attachmentPath = relative)
                        }
                    }
                }
            )
            return data.copy(document = document)
        } finally {
            runCatching { tempDir.deleteRecursively() }
        }
    }

    private fun NoteDocument.hasAttachments(): Boolean = paragraphs.any { it.attachmentPath.isNotBlank() }
}
