package com.antgskds.calendarassistant.core.note

import kotlinx.coroutines.flow.Flow

class NoteRepository(
    private val notesDao: NotesDao
) {
    val notes: Flow<List<NoteEntity>> = notesDao.observeNotes()

    suspend fun getNote(id: Long): NoteEntity? = notesDao.getNote(id)

    suspend fun saveNote(
        id: Long?,
        title: String,
        document: NoteDocument,
        createdAt: Long? = null
    ): Long {
        val now = System.currentTimeMillis()
        val normalizedTitle = title.trim().take(MAX_TITLE_LENGTH)
        val normalizedDocument = document.withNormalizedParagraphs()
        val entity = NoteEntity(
            id = id,
            title = normalizedTitle,
            plainText = normalizedDocument.plainText(),
            documentJson = NoteDocumentCodec.encode(normalizedDocument),
            createdAt = createdAt ?: now,
            updatedAt = now
        )

        return if (id == null) {
            notesDao.insert(entity)
        } else {
            notesDao.update(entity)
            id
        }
    }

    suspend fun deleteNote(id: Long) {
        notesDao.deleteById(id)
    }

    companion object {
        const val MAX_TITLE_LENGTH = 80
    }
}

fun NoteDocument.withNormalizedParagraphs(): NoteDocument {
    return copy(
        paragraphs = paragraphs.map { paragraph ->
            val normalizedText = paragraph.text.replace("\r\n", "\n").replace('\r', '\n')
            paragraph.copy(
                text = normalizedText,
                checked = if (paragraph.type == NoteParagraphType.TODO) paragraph.checked else false,
                spans = paragraph.spans
                    .mapNotNull { span ->
                        val start = span.start.coerceIn(0, normalizedText.length)
                        val end = span.end.coerceIn(0, normalizedText.length)
                        if (start >= end || span.isEmpty()) null else span.copy(start = start, end = end)
                    },
                attachmentPath = paragraph.attachmentPath.trim(),
                attachmentName = paragraph.attachmentName.trim(),
                attachmentMime = paragraph.attachmentMime.trim()
            )
        }
    )
}
