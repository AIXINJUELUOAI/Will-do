package com.antgskds.calendarassistant.ui.connector

import com.antgskds.calendarassistant.core.note.NoteDocument
import com.antgskds.calendarassistant.core.note.NoteDocumentCodec
import com.antgskds.calendarassistant.core.note.NoteEntity
import com.antgskds.calendarassistant.core.note.NoteParagraph
import com.antgskds.calendarassistant.core.note.NoteParagraphType
import com.antgskds.calendarassistant.core.note.NoteTextSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteListConnectorTest {
    @Test
    fun `filters notes and builds presentation state without exposing entities`() {
        val matching = note(
            id = 7L,
            title = "Project Alpha",
            paragraphs = listOf(
                NoteParagraph(text = "Body preview"),
                NoteParagraph(id = "todo-1", text = "Ship it", type = NoteParagraphType.TODO)
            )
        )
        val other = note(
            id = 8L,
            title = "Personal",
            paragraphs = listOf(NoteParagraph(text = "Buy milk"))
        )

        val connection = buildNoteListConnection(
            notes = listOf(matching, other),
            searchQuery = "alpha",
            hapticEnabled = false
        )

        assertEquals(1, connection.state.items.size)
        assertEquals(1, connection.state.pendingTaskCount)
        assertFalse(connection.state.hapticEnabled)
        assertEquals("Project Alpha", connection.state.items.single().title)
        assertEquals("Body preview", connection.state.items.single().previewText)
        assertSame(matching, connection.notesByKey.getValue(connection.state.items.single().key))
    }

    @Test
    fun `maps task preview styling remaining count and completion`() {
        val tasks = (1..4).map { index ->
            NoteParagraph(
                id = "todo-$index",
                text = "Task $index",
                type = NoteParagraphType.TODO,
                checked = true,
                spans = if (index == 1) {
                    listOf(NoteTextSpan(start = 0, end = 4, bold = true, strike = true))
                } else {
                    emptyList()
                }
            )
        }
        val source = note(id = 42L, title = "", paragraphs = tasks, pinnedAt = 123L)

        val item = buildNoteListConnection(listOf(source), "", true).state.items.single()

        assertEquals("无标题", item.title)
        assertEquals(3, item.previewTasks.size)
        assertEquals(1, item.remainingTaskCount)
        assertTrue(item.pinned)
        assertTrue(item.allTodosCompleted)
        assertTrue(item.previewTasks.first().spans.single().bold)
        assertTrue(item.previewTasks.first().spans.single().strike)
    }

    @Test
    fun `counts pending tasks only in filtered notes`() {
        val visible = note(
            id = 1L,
            title = "Visible",
            paragraphs = listOf(
                NoteParagraph(text = "needle"),
                NoteParagraph(type = NoteParagraphType.TODO, checked = false),
                NoteParagraph(type = NoteParagraphType.TODO, checked = true)
            )
        )
        val hidden = note(
            id = 2L,
            title = "Hidden",
            paragraphs = listOf(NoteParagraph(type = NoteParagraphType.TODO, checked = false))
        )

        val state = buildNoteListConnection(listOf(visible, hidden), "needle", true).state

        assertEquals(1, state.items.size)
        assertEquals(1, state.pendingTaskCount)
    }

    private fun note(
        id: Long,
        title: String,
        paragraphs: List<NoteParagraph>,
        pinnedAt: Long? = null
    ): NoteEntity = NoteEntity(
        id = id,
        title = title,
        documentJson = NoteDocumentCodec.encode(NoteDocument(paragraphs = paragraphs)),
        createdAt = 100L,
        updatedAt = 200L,
        pinnedAt = pinnedAt
    )
}
