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
    fun plainText(): String = paragraphs.joinToString("\n") { it.plainTextContent() }

    fun isFloatingPlainTextLine(paragraph: NoteParagraph): Boolean {
        return paragraph.type == NoteParagraphType.TEXT &&
            paragraph.effectiveParagraphStyle() == NoteParagraphStyle.BODY &&
            paragraph.effectiveListStyle() == NoteListStyle.NONE &&
            paragraph.spans.isEmpty()
    }

    fun floatingText(): String = paragraphs
        .filter { isFloatingPlainTextLine(it) }
        .joinToString("\n") { it.text }

    fun searchableText(title: String = ""): String = buildString {
        appendLine(title)
        paragraphs.forEach { paragraph ->
            appendLine(paragraph.plainTextContent())
            appendLine(paragraph.attachmentName)
            appendLine(paragraph.attachmentMime)
            appendLine(paragraph.attachmentPath.substringAfterLast('/'))
        }
    }

    fun copyablePlainText(title: String = ""): String = buildString {
        if (title.isNotBlank()) appendLine(title.trim())
        paragraphs.forEach { paragraph ->
            when (paragraph.type) {
                NoteParagraphType.TODO -> appendLine("${if (paragraph.checked) "[x]" else "[ ]"} ${paragraph.text}")
                NoteParagraphType.TEXT -> appendLine(paragraph.text)
                NoteParagraphType.DIVIDER -> appendLine("---")
                NoteParagraphType.TABLE -> appendLine(paragraph.table?.copyableText().orEmpty())
                NoteParagraphType.IMAGE,
                NoteParagraphType.FILE -> Unit
            }
        }
    }.trimEnd()

    fun withFloatingText(text: String): NoteDocument {
        val newTextLines = text.replace("\r\n", "\n").replace('\r', '\n')
            .split('\n')
            .map { NoteParagraph(text = it) }
            .ifEmpty { listOf(NoteParagraph()) }
        val firstTextIndex = paragraphs.indexOfFirst { isFloatingPlainTextLine(it) }
        val preserved = paragraphs.filter { !isFloatingPlainTextLine(it) }.toMutableList()
        val insertAt = if (firstTextIndex >= 0) {
            paragraphs.take(firstTextIndex).count { !isFloatingPlainTextLine(it) }
        } else {
            0
        }
        preserved.addAll(insertAt.coerceIn(0, preserved.size), newTextLines)
        return copy(paragraphs = preserved)
    }

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
                paragraphs = text.replace("\r\n", "\n").replace('\r', '\n').split('\n').map { line ->
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
    val listStyle: NoteListStyle = NoteListStyle.NONE,
    val spans: List<NoteTextSpan> = emptyList(),
    val attachmentPath: String = "",
    val attachmentName: String = "",
    val attachmentMime: String = "",
    val table: NoteTableData? = null
)

@Serializable
enum class NoteParagraphType {
    TEXT,
    TODO,
    IMAGE,
    FILE,
    DIVIDER,
    TABLE
}

@Serializable
data class NoteTableData(
    val columnCount: Int = 2,
    val headerRowCount: Int = 1,
    val cells: List<String> = List(4) { "" }
) {
    val rowCount: Int
        get() = if (columnCount <= 0) 0 else cells.size / columnCount

    fun normalized(): NoteTableData {
        val normalizedColumns = columnCount.coerceAtLeast(1)
        val normalizedCells = if (cells.isEmpty()) {
            List(normalizedColumns * 2) { "" }
        } else {
            val sanitized = cells.map { it.replace("\r\n", "\n").replace('\r', '\n') }
            val remainder = sanitized.size % normalizedColumns
            if (remainder == 0) sanitized else sanitized + List(normalizedColumns - remainder) { "" }
        }
        val normalizedRows = (normalizedCells.size / normalizedColumns).coerceAtLeast(1)
        return copy(
            columnCount = normalizedColumns,
            headerRowCount = headerRowCount.coerceIn(0, normalizedRows),
            cells = normalizedCells
        )
    }

    fun cell(row: Int, column: Int): String {
        val index = row * columnCount + column
        return cells.getOrElse(index) { "" }
    }

    fun withCell(row: Int, column: Int, value: String): NoteTableData {
        val normalized = normalized()
        val index = row * normalized.columnCount + column
        if (index !in normalized.cells.indices) return normalized
        val updated = normalized.cells.toMutableList()
        updated[index] = value.replace("\r\n", "\n").replace('\r', '\n')
        return normalized.copy(cells = updated)
    }

    fun appendRow(): NoteTableData {
        val normalized = normalized()
        return normalized.copy(cells = normalized.cells + List(normalized.columnCount) { "" })
    }

    fun insertRow(row: Int): NoteTableData {
        val normalized = normalized()
        val insertIndex = row.coerceIn(0, normalized.rowCount) * normalized.columnCount
        val updated = normalized.cells.toMutableList().apply {
            addAll(insertIndex, List(normalized.columnCount) { "" })
        }
        val newRowCount = (updated.size / normalized.columnCount).coerceAtLeast(1)
        return normalized.copy(
            headerRowCount = normalized.headerRowCount.coerceIn(0, newRowCount),
            cells = updated
        )
    }

    fun removeRow(row: Int): NoteTableData {
        val normalized = normalized()
        if (normalized.rowCount <= 1 || row !in 0 until normalized.rowCount) return normalized
        val start = row * normalized.columnCount
        val end = start + normalized.columnCount
        val updated = normalized.cells.toMutableList().apply { subList(start, end).clear() }
        val newRowCount = (updated.size / normalized.columnCount).coerceAtLeast(1)
        return normalized.copy(
            headerRowCount = normalized.headerRowCount.coerceIn(0, newRowCount),
            cells = updated
        )
    }

    fun appendColumn(): NoteTableData {
        val normalized = normalized()
        val updated = buildList {
            repeat(normalized.rowCount) { row ->
                repeat(normalized.columnCount) { column -> add(normalized.cell(row, column)) }
                add("")
            }
        }
        return normalized.copy(columnCount = normalized.columnCount + 1, cells = updated)
    }

    fun removeColumn(column: Int): NoteTableData {
        val normalized = normalized()
        if (normalized.columnCount <= 1 || column !in 0 until normalized.columnCount) return normalized
        val updated = buildList {
            repeat(normalized.rowCount) { row ->
                repeat(normalized.columnCount) { currentColumn ->
                    if (currentColumn != column) add(normalized.cell(row, currentColumn))
                }
            }
        }
        return normalized.copy(columnCount = normalized.columnCount - 1, cells = updated)
    }

    fun insertColumn(column: Int): NoteTableData {
        val normalized = normalized()
        val insertColumn = column.coerceIn(0, normalized.columnCount)
        val updated = buildList {
            repeat(normalized.rowCount) { row ->
                repeat(normalized.columnCount + 1) { currentColumn ->
                    when {
                        currentColumn < insertColumn -> add(normalized.cell(row, currentColumn))
                        currentColumn == insertColumn -> add("")
                        else -> add(normalized.cell(row, currentColumn - 1))
                    }
                }
            }
        }
        return normalized.copy(columnCount = normalized.columnCount + 1, cells = updated)
    }

    fun moveRow(from: Int, to: Int): NoteTableData {
        val normalized = normalized()
        if (from !in 0 until normalized.rowCount || to !in 0 until normalized.rowCount || from == to) return normalized
        val rows = List(normalized.rowCount) { row ->
            MutableList(normalized.columnCount) { column -> normalized.cell(row, column) }
        }.toMutableList()
        val moved = rows.removeAt(from)
        rows.add(to, moved)
        return normalized.copy(cells = rows.flatten())
    }

    fun plainTextRows(): List<String> {
        val normalized = normalized()
        return List(normalized.rowCount) { row ->
            List(normalized.columnCount) { column -> normalized.cell(row, column).trim() }
                .joinToString(" | ")
                .trim()
        }.filter { it.isNotBlank() }
    }

    fun copyableText(): String = plainTextRows().joinToString("\n")
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
enum class NoteListStyle {
    NONE,
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
            .map { it.withMigratedParagraphStyles() }
            .getOrElse { NoteDocument.fromPlainText(raw) }
    }
}

fun NoteDocument.withMigratedParagraphStyles(): NoteDocument {
    return copy(paragraphs = paragraphs.map { it.withMigratedParagraphStyle() })
}

fun NoteParagraph.withMigratedParagraphStyle(): NoteParagraph {
    val migratedListStyle = effectiveListStyle()
    val migratedStyle = effectiveParagraphStyle()
    val compatibleListStyle = if (migratedStyle == NoteParagraphStyle.CODE || migratedStyle == NoteParagraphStyle.QUOTE) {
        NoteListStyle.NONE
    } else {
        migratedListStyle
    }
    return copy(style = migratedStyle, listStyle = compatibleListStyle)
}

fun NoteParagraph.effectiveListStyle(): NoteListStyle {
    if (listStyle != NoteListStyle.NONE) return listStyle
    return when (style) {
        NoteParagraphStyle.BULLET -> NoteListStyle.BULLET
        NoteParagraphStyle.ORDERED -> NoteListStyle.ORDERED
        else -> NoteListStyle.NONE
    }
}

fun NoteParagraph.effectiveParagraphStyle(): NoteParagraphStyle {
    return when (style) {
        NoteParagraphStyle.BULLET,
        NoteParagraphStyle.ORDERED -> NoteParagraphStyle.BODY
        else -> style
    }
}

private fun newParagraphId(): String = UUID.randomUUID().toString()

fun NoteParagraph.plainTextContent(): String = when (type) {
    NoteParagraphType.TABLE -> table?.copyableText().orEmpty()
    else -> text
}
