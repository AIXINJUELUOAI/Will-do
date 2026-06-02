package com.antgskds.calendarassistant.core.note

import android.content.Context
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.antgskds.calendarassistant.BuildConfig
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

    fun openAttachmentIntent(context: Context, paragraph: NoteParagraph): Intent? {
        val file = fileForRelativePath(context, paragraph.attachmentPath)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        val mime = paragraph.attachmentMime.ifBlank { inferMimeType(file.name) }.ifBlank { "*/*" }
        val intent = viewIntent(context, uri, mime)
        val resolvedIntent = if (context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()) {
            intent
        } else {
            viewIntent(context, uri, "*/*")
        }
        val targets = context.packageManager.queryIntentActivities(resolvedIntent, 0)
        if (targets.isEmpty()) return null
        targets.forEach { info ->
            context.grantUriPermission(info.activityInfo.packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(resolvedIntent, "打开附件").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun viewIntent(context: Context, uri: Uri, mime: String): Intent {
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            clipData = ClipData.newUri(context.contentResolver, "attachment", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun isImage(mimeType: String, displayName: String): Boolean {
        if (mimeType.startsWith("image/", ignoreCase = true)) return true
        val lower = displayName.lowercase()
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") ||
            lower.endsWith(".webp") || lower.endsWith(".gif") || lower.endsWith(".bmp") ||
            lower.endsWith(".heic") || lower.endsWith(".heif")
    }

    fun inferMimeType(fileName: String): String {
        return android.webkit.MimeTypeMap.getSingleton()
            .getMimeTypeFromExtension(fileName.substringAfterLast('.', "").lowercase())
            .orEmpty()
    }

    private fun queryDisplayName(context: Context, uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) return cursor.getString(index).orEmpty()
        }
        return uri.lastPathSegment.orEmpty().substringAfterLast('/')
    }
}
