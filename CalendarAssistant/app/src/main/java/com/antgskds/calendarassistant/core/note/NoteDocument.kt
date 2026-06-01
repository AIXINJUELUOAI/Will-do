package com.antgskds.calendarassistant.core.note

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class NoteDocument(
    val paragraphs: List<NoteParagraph> = emptyList(),
    val legacyMigrationKey: String = ""
) {
    fun plainText(): String = paragraphs.joinToString("\n") { it.text }

    fun todoCount(): Int = paragraphs.count { it.type == NoteParagraphType.TODO }

    fun pendingTodoCount(): Int = paragraphs.count { it.type == NoteParagraphType.TODO && !it.checked }

    fun allTodosCompleted(): Boolean {
        val todos = paragraphs.filter { it.type == NoteParagraphType.TODO }
        return todos.isNotEmpty() && todos.all { it.checked }
    }

    companion object {
        fun fromPlainText(text: String): NoteDocument {
            if (text.isEmpty()) return NoteDocument()
            return NoteDocument(
                paragraphs = text.replace("\r\n", "\n").split('\n').map { line ->
                    NoteParagraph(text = line)
                }
            )
        }
    }
}

@Serializable
data class NoteParagraph(
    val id: String = newParagraphId(),
    val text: String = "",
    val type: NoteParagraphType = NoteParagraphType.TEXT,
    val checked: Boolean = false,
    val style: NoteParagraphStyle = NoteParagraphStyle.BODY,
    val spans: List<NoteTextSpan> = emptyList(),
    val attachmentPath: String = "",
    val attachmentName: String = "",
    val attachmentMime: String = ""
)

@Serializable
enum class NoteParagraphType {
    TEXT,
    TODO,
    IMAGE,
    FILE
}

@Serializable
enum class NoteParagraphStyle {
    BODY,
    H1,
    H2,
    H3,
    H4,
    H5,
    HEADING,
    QUOTE,
    CODE,
    BULLET,
    ORDERED
}

@Serializable
enum class NoteTextStyle {
    BOLD,
    ITALIC,
    UNDERLINE,
    STRIKE
}

@Serializable
data class NoteTextSpan(
    val start: Int,
    val end: Int,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false
) {
    fun has(style: NoteTextStyle): Boolean = when (style) {
        NoteTextStyle.BOLD -> bold
        NoteTextStyle.ITALIC -> italic
        NoteTextStyle.UNDERLINE -> underline
        NoteTextStyle.STRIKE -> strike
    }

    fun withStyle(style: NoteTextStyle, enabled: Boolean): NoteTextSpan = when (style) {
        NoteTextStyle.BOLD -> copy(bold = enabled)
        NoteTextStyle.ITALIC -> copy(italic = enabled)
        NoteTextStyle.UNDERLINE -> copy(underline = enabled)
        NoteTextStyle.STRIKE -> copy(strike = enabled)
    }

    fun isEmpty(): Boolean = !bold && !italic && !underline && !strike
}

object NoteDocumentCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(document: NoteDocument): String = json.encodeToString(document)

    fun decode(raw: String): NoteDocument {
        if (raw.isBlank()) return NoteDocument()
        return runCatching { json.decodeFromString<NoteDocument>(raw) }
            .getOrElse { NoteDocument.fromPlainText(raw) }
    }
}

private fun newParagraphId(): String = UUID.randomUUID().toString()
