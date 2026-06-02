package com.antgskds.calendarassistant.core.note

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "notes",
    indices = [Index(value = ["updated_at"]), Index(value = ["pinned_at"])]
)
data class NoteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
    @ColumnInfo(name = "title")
    val title: String = "",
    @ColumnInfo(name = "plain_text")
    val plainText: String = "",
    @ColumnInfo(name = "document_json")
    val documentJson: String = "",
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "pinned_at")
    val pinnedAt: Long? = null
) {
    val displayTitle: String get() = title.trim().ifBlank { "无标题" }

    fun document(): NoteDocument = NoteDocumentCodec.decode(documentJson.ifBlank { plainText })
}
