package com.antgskds.calendarassistant.feature.note.ui.connector

import com.antgskds.calendarassistant.feature.note.domain.model.NoteDocument
import com.antgskds.calendarassistant.feature.note.domain.model.NoteDocumentCodec
import com.antgskds.calendarassistant.feature.note.data.local.NoteEntity
import com.antgskds.calendarassistant.feature.note.domain.model.NoteParagraph
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteEditorConnectorTest {
    @Test
    fun `maps persisted note into pure editor initial state`() {
        val document = NoteDocument(paragraphs = listOf(NoteParagraph(text = "Body")))
        val note = NoteEntity(
            id = 42L,
            title = "Title",
            documentJson = NoteDocumentCodec.encode(document),
            createdAt = 1234L,
            updatedAt = 5678L,
            pinnedAt = 999L
        )
        val settings = MySettings(
            hapticFeedbackEnabled = false,
            appBackgroundImagePath = "/wallpaper.jpg",
            appBackgroundMiuiBlurTestEnabled = true,
            appBackgroundCardAlphaPercent = 77,
            predictiveBackEnabled = false
        )

        val state = buildNoteEditorUiState(note, editorSessionKey = 8, settings = settings)

        assertEquals(8, state.editorSessionKey)
        assertEquals(42L, state.initialNoteId)
        assertEquals("Title", state.initialTitle)
        assertEquals("Body", state.initialDocument.paragraphs.single().text)
        assertEquals(1234L, state.initialCreatedAt)
        assertTrue(state.initiallyPinned)
        assertFalse(state.hapticEnabled)
        assertTrue(state.backgroundEnabled)
        assertTrue(state.backgroundBlurEnabled)
        assertEquals(77, state.backgroundCardAlphaPercent)
        assertFalse(state.predictiveBackEnabled)
    }

    @Test
    fun `maps new note without persistence identity`() {
        val state = buildNoteEditorUiState(
            initialNote = null,
            editorSessionKey = -1,
            settings = MySettings()
        )

        assertNull(state.initialNoteId)
        assertEquals("", state.initialTitle)
        assertTrue(state.initialDocument.paragraphs.isEmpty())
        assertFalse(state.initiallyPinned)
    }
}
