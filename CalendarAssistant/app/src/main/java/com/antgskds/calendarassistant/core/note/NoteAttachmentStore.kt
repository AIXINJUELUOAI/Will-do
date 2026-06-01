package com.antgskds.calendarassistant.core.note

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.UUID

data class StoredNoteAttachment(
    val relativePath: String,
    val displayName: String,
    val mimeType: String,
    val isImage: Boolean
)

object NoteAttachmentStore {
    private const val ROOT_DIR = "notes"
    private const val ATTACHMENTS_DIR = "attachments"

    fun copyFromUri(context: Context, uri: Uri): StoredNoteAttachment {
        val resolver = context.contentResolver
        val mimeType = resolver.getType(uri).orEmpty()
        val displayName = queryDisplayName(context, uri).ifBlank { "attachment" }
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "").takeIf { it.isNotBlank() }
        val fileName = buildString {
            append(UUID.randomUUID().toString())
            if (extension != null) append('.').append(extension)
        }
        val relativePath = "$ROOT_DIR/$ATTACHMENTS_DIR/$fileName"
        val outputFile = File(context.filesDir, relativePath).apply {
            parentFile?.mkdirs()
        }

        resolver.openInputStream(uri)?.use { input ->
            outputFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Unable to open attachment stream")

        return StoredNoteAttachment(
            relativePath = relativePath,
            displayName = displayName,
            mimeType = mimeType,
            isImage = isImage(mimeType, displayName)
        )
    }

    fun fileForRelativePath(context: Context, relativePath: String): File = File(context.filesDir, relativePath)

    fun delete(context: Context, relativePath: String) {
        if (relativePath.isBlank()) return
        runCatching { fileForRelativePath(context, relativePath).delete() }
    }

    fun isImage(mimeType: String, displayName: String): Boolean {
        if (mimeType.startsWith("image/", ignoreCase = true)) return true
        val lower = displayName.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp") ||
            lower.endsWith(".heic") || lower.endsWith(".heif")
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index).orEmpty()
        }
        return uri.lastPathSegment.orEmpty().substringAfterLast('/')
    }
}
