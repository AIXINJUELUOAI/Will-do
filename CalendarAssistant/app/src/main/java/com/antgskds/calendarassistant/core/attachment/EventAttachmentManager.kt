package com.antgskds.calendarassistant.core.attachment

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.antgskds.calendarassistant.BuildConfig
import com.antgskds.calendarassistant.calendar.data.EventsDatabase
import com.antgskds.calendarassistant.calendar.models.ATTACHMENT_SOURCE_MANUAL
import com.antgskds.calendarassistant.calendar.models.ATTACHMENT_SOURCE_RECOGNITION_IMAGE
import com.antgskds.calendarassistant.calendar.models.EventAttachment
import com.antgskds.calendarassistant.core.util.extractSourceImagePaths
import com.antgskds.calendarassistant.core.util.stripSourceImageMarkers
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale

class EventAttachmentManager(context: Context) {
    private val appContext = context.applicationContext
    private val db = EventsDatabase.getInstance(appContext)
    private val attachmentsDir = File(appContext.filesDir, "event_attachments")

    init {
        ensureDir()
    }

    fun getAttachments(eventId: Long): List<EventAttachment> {
        if (eventId <= 0L) return emptyList()
        val event = db.eventsDao().getEventOrTaskWithId(eventId)
        val eventKey = event?.let { eventKey(it.title, it.startTS, it.endTS, it.getTimeZoneString()) }.orEmpty()
        val byId = db.eventAttachmentsDao().getAttachmentsForEvent(eventId)
        val byKey = if (eventKey.isNotBlank()) db.eventAttachmentsDao().getAttachmentsForEventKey(eventKey) else emptyList()
        val merged = (byId + byKey).distinctBy { it.id ?: it.localPath.hashCode().toLong() }
        val toRepair = merged.mapNotNull { attachment ->
            val id = attachment.id ?: return@mapNotNull null
            if (attachment.eventId != eventId || attachment.eventKey != eventKey) id else null
        }
        if (toRepair.isNotEmpty() && eventKey.isNotBlank()) {
            db.eventAttachmentsDao().bindAttachments(toRepair, eventId, eventKey)
        }
        return merged.map { attachment ->
            if (eventKey.isNotBlank() && (attachment.eventId != eventId || attachment.eventKey != eventKey)) {
                attachment.copy(eventId = eventId, eventKey = eventKey)
            } else {
                attachment
            }
        }
    }

    fun getAttachments(eventIds: List<Long>): List<EventAttachment> {
        val ids = eventIds.filter { it > 0L }.distinct()
        if (ids.isEmpty()) return emptyList()
        return db.eventAttachmentsDao().getAttachmentsForEvents(ids)
    }

    fun getAllAttachments(): List<EventAttachment> = db.eventAttachmentsDao().getAllAttachments()

    fun getAttachmentCount(): Int = db.eventAttachmentsDao().getAttachmentCount()

    fun addManualAttachment(eventId: Long, uri: Uri): EventAttachment {
        val event = db.eventsDao().getEventOrTaskWithId(eventId)
        val key = event?.let { eventKey(it.title, it.startTS, it.endTS, it.getTimeZoneString()) }.orEmpty()
        return addAttachment(uri = uri, eventId = eventId, eventKey = key, source = ATTACHMENT_SOURCE_MANUAL)
    }

    fun addPendingManualAttachment(eventKey: String, uri: Uri): EventAttachment {
        return addAttachment(uri = uri, eventId = null, eventKey = eventKey, source = ATTACHMENT_SOURCE_MANUAL)
    }

    private fun addAttachment(uri: Uri, eventId: Long?, eventKey: String, source: String): EventAttachment {
        val meta = queryUriMeta(uri)
        val target = createUniqueAttachmentFile(meta.displayName)
        appContext.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output -> input.copyTo(output) }
        } ?: error("无法读取附件")

        val attachment = EventAttachment(
            eventId = eventId,
            eventKey = eventKey,
            localPath = target.absolutePath,
            displayName = meta.displayName.ifBlank { target.name },
            mimeType = meta.mimeType,
            sizeBytes = target.length(),
            source = source
        )
        val id = db.eventAttachmentsDao().insertOrUpdate(attachment)
        return attachment.copy(id = id)
    }

    fun bindPendingAttachments(eventId: Long, pendingEventKey: String) {
        if (eventId <= 0L || pendingEventKey.isBlank()) return
        val event = db.eventsDao().getEventOrTaskWithId(eventId) ?: return
        val finalKey = eventKey(event.title, event.startTS, event.endTS, event.getTimeZoneString())
        db.eventAttachmentsDao().bindAttachmentsByEventKey(pendingEventKey, eventId, finalKey)
    }

    fun updateEventKey(eventId: Long) {
        if (eventId <= 0L) return
        val event = db.eventsDao().getEventOrTaskWithId(eventId) ?: return
        db.eventAttachmentsDao().updateEventKeyForEvent(
            eventId = eventId,
            eventKey = eventKey(event.title, event.startTS, event.endTS, event.getTimeZoneString())
        )
    }

    fun addRecognitionImageAttachment(eventId: Long, imagePath: String): EventAttachment? {
        if (eventId <= 0L || imagePath.isBlank()) return null
        val file = File(imagePath)
        if (!file.exists()) return null
        val attachment = EventAttachment(
            eventId = eventId,
            eventKey = eventKey(eventId),
            localPath = file.absolutePath,
            displayName = file.name,
            mimeType = inferMimeType(file.name).ifBlank { "image/jpeg" },
            sizeBytes = file.length(),
            source = ATTACHMENT_SOURCE_RECOGNITION_IMAGE
        )
        val id = db.eventAttachmentsDao().insertOrUpdate(attachment)
        return attachment.copy(id = id)
    }

    fun addExistingAttachment(
        eventId: Long,
        file: File,
        displayName: String,
        mimeType: String,
        source: String,
        copyIntoStore: Boolean = true
    ): EventAttachment {
        ensureDir()
        val target = if (copyIntoStore) {
            val dest = createUniqueAttachmentFile(displayName.ifBlank { file.name })
            file.copyTo(dest, overwrite = true)
            dest
        } else {
            file
        }
        val attachment = EventAttachment(
            eventId = eventId,
            eventKey = eventKey(eventId),
            localPath = target.absolutePath,
            displayName = displayName.ifBlank { target.name },
            mimeType = mimeType.ifBlank { inferMimeType(target.name) },
            sizeBytes = target.length(),
            source = source.ifBlank { ATTACHMENT_SOURCE_MANUAL }
        )
        val id = db.eventAttachmentsDao().insertOrUpdate(attachment)
        return attachment.copy(id = id)
    }

    fun deleteAttachment(attachment: EventAttachment) {
        attachment.id?.let { db.eventAttachmentsDao().deleteAttachment(it) }
        cleanupFileIfUnused(attachment.localPath)
    }

    fun deleteAttachmentsForEvent(eventId: Long) {
        val attachments = getAttachments(eventId)
        db.eventAttachmentsDao().deleteAttachmentsForEvent(eventId)
        val eventKey = eventKey(eventId)
        if (eventKey.isNotBlank()) db.eventAttachmentsDao().deleteAttachmentsForEventKey(eventKey)
        attachments.forEach { cleanupFileIfUnused(it.localPath) }
    }

    fun deleteAttachmentsForEventKey(eventKey: String) {
        if (eventKey.isBlank()) return
        val attachments = db.eventAttachmentsDao().getAttachmentsForEventKey(eventKey)
        db.eventAttachmentsDao().deleteAttachmentsForEventKey(eventKey)
        attachments.forEach { cleanupFileIfUnused(it.localPath) }
    }

    fun openAttachmentIntent(attachment: EventAttachment): Intent? {
        val file = File(attachment.localPath)
        if (!file.exists()) return null
        val uri = FileProvider.getUriForFile(appContext, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
        val mime = attachment.mimeType.ifBlank { inferMimeType(file.name).ifBlank { "*/*" } }
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun estimateTotalSize(): Long {
        return getAllAttachments().sumOf { attachment ->
            val file = File(attachment.localPath)
            if (file.exists()) file.length() else attachment.sizeBytes.coerceAtLeast(0L)
        }
    }

    fun migrateLegacyDescriptionMarkers(): Int {
        val dao = db.eventsDao()
        val events = (dao.getAllEventsOrTasks() + dao.getArchivedEvents()).distinctBy { it.id }
        var migrated = 0
        events.forEach { event ->
            val eventId = event.id ?: return@forEach
            val paths = extractSourceImagePaths(event.description)
            if (paths.isEmpty()) return@forEach
            paths.forEach { path ->
                val file = File(path)
                if (file.exists()) {
                    addRecognitionImageAttachment(eventId, file.absolutePath)?.let { migrated++ }
                }
            }
            dao.insertOrUpdate(event.copy(description = stripSourceImageMarkers(event.description)))
        }
        return migrated
    }

    fun createUniqueAttachmentFile(displayName: String): File {
        ensureDir()
        val safeName = sanitizeFileName(displayName.ifBlank { "attachment" })
        val ext = safeName.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let { ".$it" } ?: ""
        val base = safeName.removeSuffix(ext).ifBlank { "attachment" }
        var candidate = File(attachmentsDir, "${System.currentTimeMillis()}_${base.take(36)}$ext")
        var index = 1
        while (candidate.exists()) {
            candidate = File(attachmentsDir, "${System.currentTimeMillis()}_${base.take(32)}_$index$ext")
            index++
        }
        return candidate
    }

    private fun cleanupFileIfUnused(localPath: String) {
        if (localPath.isBlank()) return
        if (db.eventAttachmentsDao().countByLocalPath(localPath) > 0) return
        val file = File(localPath)
        if (file.exists() && isManagedAttachmentFile(file)) {
            runCatching { file.delete() }
        }
    }

    private fun isManagedAttachmentFile(file: File): Boolean {
        val attachmentsRoot = attachmentsDir.canonicalFile
        val screenshotsRoot = File(appContext.filesDir, "event_screenshots").canonicalFile
        val target = file.canonicalFile
        return target.path.startsWith(attachmentsRoot.path) || target.path.startsWith(screenshotsRoot.path)
    }

    private fun queryUriMeta(uri: Uri): AttachmentMeta {
        val resolver = appContext.contentResolver
        var displayName = ""
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) displayName = cursor.getString(nameIndex).orEmpty()
                if (sizeIndex >= 0) size = cursor.getLong(sizeIndex)
            }
        }
        val mimeType = resolver.getType(uri).orEmpty().ifBlank { inferMimeType(displayName) }
        if (displayName.isBlank()) {
            displayName = "attachment_${shortHash(uri.toString())}${extensionForMime(mimeType)}"
        }
        return AttachmentMeta(displayName, mimeType, size)
    }

    private fun ensureDir() {
        if (!attachmentsDir.exists()) attachmentsDir.mkdirs()
    }

    private fun sanitizeFileName(raw: String): String {
        return raw.replace(Regex("[\\\\/:*?\"<>|\\r\\n]+"), "_").trim().ifBlank { "attachment" }
    }

    private fun shortHash(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return digest.take(6).joinToString("") { "%02x".format(it) }
    }

    private fun extensionForMime(mimeType: String): String {
        return when (mimeType.lowercase(Locale.ROOT)) {
            "image/jpeg" -> ".jpg"
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "application/pdf" -> ".pdf"
            "text/plain" -> ".txt"
            else -> ""
        }
    }

    private data class AttachmentMeta(
        val displayName: String,
        val mimeType: String,
        val sizeBytes: Long
    )

    companion object {
        fun eventKey(title: String, startTS: Long, endTS: Long, timeZone: String = ""): String {
            val normalized = listOf(
                title.trim().lowercase(Locale.ROOT),
                startTS.toString(),
                endTS.toString(),
                timeZone.ifBlank { "local" }
            ).joinToString("|")
            val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray())
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun inferMimeType(fileName: String): String {
            val lower = fileName.lowercase(Locale.ROOT)
            return when {
                lower.endsWith(".jpg") || lower.endsWith(".jpeg") -> "image/jpeg"
                lower.endsWith(".png") -> "image/png"
                lower.endsWith(".webp") -> "image/webp"
                lower.endsWith(".gif") -> "image/gif"
                lower.endsWith(".pdf") -> "application/pdf"
                lower.endsWith(".txt") -> "text/plain"
                lower.endsWith(".json") -> "application/json"
                lower.endsWith(".zip") -> "application/zip"
                else -> ""
            }
        }

        fun formatSize(bytes: Long): String {
            if (bytes <= 0L) return "未知大小"
            val kb = bytes / 1024.0
            if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
            return String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0)
        }
    }

    private fun eventKey(eventId: Long): String {
        val event = db.eventsDao().getEventOrTaskWithId(eventId) ?: return ""
        return eventKey(event.title, event.startTS, event.endTS, event.getTimeZoneString())
    }
}
