package com.antgskds.calendarassistant.core.center

import com.antgskds.calendarassistant.core.note.NoteDocument
import com.antgskds.calendarassistant.core.note.NoteEntity
import com.antgskds.calendarassistant.core.note.NoteRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NoteCenter(
    private val repository: NoteRepository,
    private val appScope: CoroutineScope
) {
    private val _notes = MutableStateFlow<List<NoteEntity>>(emptyList())
    val notes: StateFlow<List<NoteEntity>> = _notes.asStateFlow()

    fun start() {
        appScope.launch(Dispatchers.IO) {
            repository.notes.collect { list ->
                _notes.value = list
            }
        }
    }

    suspend fun getNote(id: Long): NoteEntity? = withContext(Dispatchers.IO) {
        repository.getNote(id)
    }

    suspend fun saveNote(
        id: Long?,
        title: String,
        document: NoteDocument,
        createdAt: Long? = null
    ): Long = withContext(Dispatchers.IO) {
        repository.saveNote(id, title, document, createdAt)
    }

    suspend fun toggleTodo(noteId: Long, paragraphId: String) = withContext(Dispatchers.IO) {
        val note = repository.getNote(noteId) ?: return@withContext
        val document = note.document()
        val updatedDocument = document.copy(
            paragraphs = document.paragraphs.map { paragraph ->
                if (paragraph.id == paragraphId) {
                    paragraph.copy(checked = !paragraph.checked)
                } else {
                    paragraph
                }
            }
        )
        repository.saveNote(note.id, note.title, updatedDocument, note.createdAt)
    }

    suspend fun deleteNote(id: Long) = withContext(Dispatchers.IO) {
        repository.deleteNote(id)
    }

    suspend fun setPinned(noteId: Long, pinned: Boolean) = withContext(Dispatchers.IO) {
        repository.setPinned(noteId, pinned)
    }
}
