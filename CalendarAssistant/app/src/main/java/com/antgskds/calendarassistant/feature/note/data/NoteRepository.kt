package com.antgskds.calendarassistant.feature.note.data

import com.antgskds.calendarassistant.feature.note.domain.model.NoteDocument
import com.antgskds.calendarassistant.feature.note.domain.model.NoteDocumentCodec
import com.antgskds.calendarassistant.core.note.NoteEntity
import com.antgskds.calendarassistant.feature.note.domain.model.NoteListStyle
import com.antgskds.calendarassistant.feature.note.domain.model.NoteParagraph
import com.antgskds.calendarassistant.feature.note.domain.model.NoteParagraphStyle
import com.antgskds.calendarassistant.feature.note.domain.model.NoteParagraphType
import com.antgskds.calendarassistant.core.note.NotesDao
import com.antgskds.calendarassistant.feature.note.domain.model.effectiveListStyle
import com.antgskds.calendarassistant.feature.note.domain.model.effectiveParagraphStyle
import com.antgskds.calendarassistant.feature.note.domain.model.withMigratedParagraphStyles
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
            updatedAt = now,
            pinnedAt = id?.let { notesDao.getNote(it)?.pinnedAt }
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

    suspend fun setPinned(noteId: Long, pinned: Boolean) {
        notesDao.updatePinnedAt(noteId, if (pinned) System.currentTimeMillis() else null)
    }

    companion object {
        const val MAX_TITLE_LENGTH = 80
    }
}

fun NoteDocument.withNormalizedParagraphs(): NoteDocument {
    val migrated = withMigratedParagraphStyles()
    return migrated.copy(
        paragraphs = migrated.paragraphs.map { paragraph ->
            val normalizedText = if (paragraph.type == NoteParagraphType.DIVIDER || paragraph.type == NoteParagraphType.TABLE) "" else paragraph.text.replace("\r\n", "\n").replace('\r', '\n')
            val normalizedTable = if (paragraph.type == NoteParagraphType.TABLE) paragraph.table?.normalized() else null
            val paragraphStyle = paragraph.effectiveParagraphStyle()
            paragraph.copy(
                style = paragraphStyle,
                listStyle = if (paragraphStyle == NoteParagraphStyle.CODE || paragraphStyle == NoteParagraphStyle.QUOTE || paragraph.isBlockLineForNormalize()) NoteListStyle.NONE else paragraph.effectiveListStyle(),
                text = normalizedText,
                checked = if (paragraph.type == NoteParagraphType.TODO) paragraph.checked else false,
                spans = paragraph.spans
                    .mapNotNull { span ->
                        val start = span.start.coerceIn(0, normalizedText.length)
                        val end = span.end.coerceIn(0, normalizedText.length)
                        if (start >= end || span.isEmpty()) null else span.copy(start = start, end = end)
                    },
                attachmentPath = if (paragraph.type == NoteParagraphType.IMAGE || paragraph.type == NoteParagraphType.FILE) paragraph.attachmentPath.trim() else "",
                attachmentName = if (paragraph.type == NoteParagraphType.IMAGE || paragraph.type == NoteParagraphType.FILE) paragraph.attachmentName.trim() else "",
                attachmentMime = if (paragraph.type == NoteParagraphType.IMAGE || paragraph.type == NoteParagraphType.FILE) paragraph.attachmentMime.trim() else "",
                table = normalizedTable
            )
        }
    )
}

private fun NoteParagraph.isBlockLineForNormalize(): Boolean {
    return type == NoteParagraphType.IMAGE || type == NoteParagraphType.FILE || type == NoteParagraphType.DIVIDER || type == NoteParagraphType.TABLE
}
