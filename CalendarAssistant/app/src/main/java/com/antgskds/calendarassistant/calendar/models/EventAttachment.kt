package com.antgskds.calendarassistant.calendar.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

const val ATTACHMENT_SOURCE_MANUAL = "manual"
const val ATTACHMENT_SOURCE_RECOGNITION_IMAGE = "recognition_image"

@Entity(
    tableName = "event_attachments",
    indices = [
        Index(value = ["event_id"]),
        Index(value = ["event_key"]),
        Index(value = ["local_path"])
    ]
)
data class EventAttachment(
    @PrimaryKey(autoGenerate = true) val id: Long? = null,
    @ColumnInfo(name = "event_id") val eventId: Long? = null,
    @ColumnInfo(name = "event_key") val eventKey: String = "",
    @ColumnInfo(name = "local_path") val localPath: String,
    @ColumnInfo(name = "display_name") val displayName: String = "",
    @ColumnInfo(name = "mime_type") val mimeType: String = "",
    @ColumnInfo(name = "size_bytes") val sizeBytes: Long = 0L,
    @ColumnInfo(name = "source") val source: String = ATTACHMENT_SOURCE_MANUAL,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis() / 1000L
)

val EventAttachment.isImage: Boolean
    get() = mimeType.startsWith("image/", ignoreCase = true) ||
        localPath.endsWith(".jpg", ignoreCase = true) ||
        localPath.endsWith(".jpeg", ignoreCase = true) ||
        localPath.endsWith(".png", ignoreCase = true) ||
        localPath.endsWith(".webp", ignoreCase = true)
