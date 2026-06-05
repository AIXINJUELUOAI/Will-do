package com.antgskds.calendarassistant.core.note

import java.io.File

object NoteMarkdownCodec {
    fun encode(title: String, document: NoteDocument): String {
        return buildString {
            if (title.isNotBlank()) {
                append("# ").appendLine(title.trim())
                appendLine()
            }
            document.paragraphs.forEachIndexed { index, paragraph ->
                appendLine(encodeParagraph(paragraph, index))
            }
        }.trimEnd().plus("\n")
    }

    fun decode(
        markdown: String,
        attachmentResolver: (String) -> StoredNoteAttachment? = { null }
    ): NoteTransferData {
        val paragraphs = parseParagraphs(markdown, attachmentResolver).toMutableList()
        val first = paragraphs.firstOrNull()
        val title = if (first?.type == NoteParagraphType.TEXT && first.style == NoteParagraphStyle.H1) {
            paragraphs.removeAt(0).text
        } else {
            ""
        }
        while (paragraphs.firstOrNull()?.text?.isBlank() == true && paragraphs.first().type == NoteParagraphType.TEXT) {
            paragraphs.removeAt(0)
        }
        if (paragraphs.lastOrNull()?.style == NoteParagraphStyle.CODE) {
            paragraphs += NoteParagraph()
        }
        return NoteTransferData(title = title, document = NoteDocument(paragraphs = paragraphs))
    }

    private fun encodeParagraph(paragraph: NoteParagraph, index: Int): String {
        val normalized = paragraph.withMigratedParagraphStyle()
        val text = encodeStyledText(normalized)
        val body = when (normalized.type) {
            NoteParagraphType.TODO -> "[${if (normalized.checked) "x" else " "}] $text"
            NoteParagraphType.IMAGE -> "![${normalized.attachmentName.ifBlank { "image" }}](attachments/${File(normalized.attachmentPath).name})"
            NoteParagraphType.FILE -> "[${normalized.attachmentName.ifBlank { "file" }}](attachments/${File(normalized.attachmentPath).name})"
            NoteParagraphType.DIVIDER -> "---"
            NoteParagraphType.TABLE -> encodeTable(normalized.table)
            NoteParagraphType.TEXT -> text
        }
        if (normalized.type == NoteParagraphType.TODO && normalized.effectiveListStyle() == NoteListStyle.NONE) return "- $body"
        return when (normalized.effectiveListStyle()) {
            NoteListStyle.BULLET -> "- $body"
            NoteListStyle.ORDERED -> "${index + 1}. $body"
            NoteListStyle.NONE -> body
        }
    }

    private fun encodeStyledText(paragraph: NoteParagraph): String {
        val text = encodeInline(paragraph.text, paragraph.spans)
        return when (paragraph.effectiveParagraphStyle()) {
            NoteParagraphStyle.H1 -> "# $text"
            NoteParagraphStyle.H2 -> "## $text"
            NoteParagraphStyle.H3, NoteParagraphStyle.HEADING -> "### $text"
            NoteParagraphStyle.H4 -> "#### $text"
            NoteParagraphStyle.H5 -> "##### $text"
            NoteParagraphStyle.QUOTE -> "> $text"
            NoteParagraphStyle.CODE -> "```\n${paragraph.text}\n```"
            NoteParagraphStyle.BULLET,
            NoteParagraphStyle.ORDERED,
            NoteParagraphStyle.BODY -> text
        }
    }

    private fun parseParagraphs(markdown: String, attachmentResolver: (String) -> StoredNoteAttachment?): List<NoteParagraph> {
        val result = mutableListOf<NoteParagraph>()
        val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            if (line.trimStart().startsWith("```")) {
                val code = mutableListOf<String>()
                index++
                while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                    code += lines[index]
                    index++
                }
                if (index < lines.size) index++
                result += NoteParagraph(text = code.joinToString("\n"), style = NoteParagraphStyle.CODE)
                continue
            }

            parseTable(lines, index)?.let { (paragraph, nextIndex) ->
                result += paragraph
                index = nextIndex
                continue
            }

            parseSpecialLine(line, attachmentResolver)?.let { paragraph ->
                result += paragraph
                index++
                continue
            }

            val (text, spans) = parseInline(line)
            result += NoteParagraph(text = text, spans = spans)
            index++
        }
        return result.ifEmpty { listOf(NoteParagraph()) }
    }

    private fun parseSpecialLine(line: String, attachmentResolver: (String) -> StoredNoteAttachment?): NoteParagraph? {
        val trimmed = line.trim()
        if (trimmed == "---" || trimmed == "***") return NoteParagraph(type = NoteParagraphType.DIVIDER)

        Regex("^!\\[([^]]*)]\\(([^)]+)\\)\\s*$").matchEntire(trimmed)?.let { match ->
            val label = match.groupValues[1]
            val target = match.groupValues[2]
            val stored = attachmentResolver(target) ?: return null
            return NoteParagraph(
                type = NoteParagraphType.IMAGE,
                attachmentPath = stored.relativePath,
                attachmentName = label.ifBlank { stored.displayName },
                attachmentMime = stored.mimeType
            )
        }

        Regex("^\\[([^]]+)]\\(([^)]+)\\)\\s*$").matchEntire(trimmed)?.let { match ->
            val label = match.groupValues[1]
            val target = match.groupValues[2]
            val stored = attachmentResolver(target) ?: return null
            return NoteParagraph(
                type = NoteParagraphType.FILE,
                attachmentPath = stored.relativePath,
                attachmentName = label.ifBlank { stored.displayName },
                attachmentMime = stored.mimeType
            )
        }

        parseDecoratedTextLine(line)?.let { return it }
        return null
    }

    private fun parseDecoratedTextLine(line: String): NoteParagraph? {
        Regex("^- \\[([ xX])]\\s+(.*)$").matchEntire(line)?.let { match ->
            val styled = parseStyledText(match.groupValues[2])
            return styled.copy(
                type = NoteParagraphType.TODO,
                checked = match.groupValues[1].equals("x", ignoreCase = true)
            )
        }
        Regex("^\\d+\\.\\s+\\[([ xX])]\\s+(.*)$").matchEntire(line)?.let { match ->
            val styled = parseStyledText(match.groupValues[2])
            return styled.copy(
                type = NoteParagraphType.TODO,
                checked = match.groupValues[1].equals("x", ignoreCase = true),
                listStyle = NoteListStyle.ORDERED
            )
        }

        var working = line
        var listStyle = NoteListStyle.NONE
        Regex("^-\\s+(.*)$").matchEntire(working)?.let { match ->
            listStyle = NoteListStyle.BULLET
            working = match.groupValues[1]
        } ?: Regex("^\\d+\\.\\s+(.*)$").matchEntire(working)?.let { match ->
            listStyle = NoteListStyle.ORDERED
            working = match.groupValues[1]
        }

        Regex("^\\[([ xX])]\\s+(.*)$").matchEntire(working)?.let { match ->
            val styled = parseStyledText(match.groupValues[2])
            return styled.copy(
                type = NoteParagraphType.TODO,
                checked = match.groupValues[1].equals("x", ignoreCase = true),
                listStyle = listStyle
            )
        }

        Regex("^(#{1,5})\\s+(.*)$").matchEntire(working)?.let { match ->
            val level = match.groupValues[1].length
            val (text, spans) = parseInline(match.groupValues[2])
            return NoteParagraph(text = text, spans = spans, style = headingStyle(level), listStyle = listStyle)
        }

        Regex("^>\\s?(.*)$").matchEntire(working)?.let { match ->
            val (text, spans) = parseInline(match.groupValues[1])
            return NoteParagraph(text = text, spans = spans, style = NoteParagraphStyle.QUOTE)
        }

        if (listStyle != NoteListStyle.NONE) {
            val (text, spans) = parseInline(working)
            return NoteParagraph(text = text, spans = spans, listStyle = listStyle)
        }
        return null
    }

    private fun parseStyledText(raw: String): NoteParagraph {
        Regex("^(#{1,5})\\s+(.*)$").matchEntire(raw)?.let { match ->
            val level = match.groupValues[1].length
            val (text, spans) = parseInline(match.groupValues[2])
            return NoteParagraph(text = text, spans = spans, style = headingStyle(level))
        }

        Regex("^>\\s?(.*)$").matchEntire(raw)?.let { match ->
            val (text, spans) = parseInline(match.groupValues[1])
            return NoteParagraph(text = text, spans = spans, style = NoteParagraphStyle.QUOTE)
        }

        val (text, spans) = parseInline(raw)
        return NoteParagraph(text = text, spans = spans)
    }

    private fun headingStyle(level: Int): NoteParagraphStyle = when (level) {
        1 -> NoteParagraphStyle.H1
        2 -> NoteParagraphStyle.H2
        3 -> NoteParagraphStyle.H3
        4 -> NoteParagraphStyle.H4
        else -> NoteParagraphStyle.H5
    }

    private fun encodeTable(table: NoteTableData?): String {
        val normalized = table?.normalized() ?: NoteTableData().normalized()
        val rows = normalized.rowCount.coerceAtLeast(1)
        val headerRow = 0
        val header = buildMarkdownTableRow(List(normalized.columnCount) { column -> normalized.cell(headerRow, column) })
        val separator = buildMarkdownTableRow(List(normalized.columnCount) { "---" })
        val body = buildString {
            for (row in 1 until rows) {
                appendLine(buildMarkdownTableRow(List(normalized.columnCount) { column -> normalized.cell(row, column) }))
            }
        }.trimEnd()
        return buildString {
            appendLine(header)
            appendLine(separator)
            if (body.isNotBlank()) append(body)
        }.trimEnd()
    }

    private fun buildMarkdownTableRow(cells: List<String>): String {
        return cells.joinToString(prefix = "| ", postfix = " |", separator = " | ") { encodeTableCell(it) }
    }

    private fun encodeTableCell(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("|", "\\|")
            .replace("\n", "<br>")
    }

    private fun parseTable(lines: List<String>, startIndex: Int): Pair<NoteParagraph, Int>? {
        if (startIndex + 1 >= lines.size) return null
        val headerLine = lines[startIndex]
        val separatorLine = lines[startIndex + 1]
        if (!headerLine.contains('|') || !isMarkdownTableSeparator(separatorLine)) return null
        val headerCells = splitMarkdownTableRow(headerLine)
        val separatorCells = splitMarkdownTableRow(separatorLine)
        if (headerCells.isEmpty() || headerCells.size != separatorCells.size) return null
        val rows = mutableListOf(headerCells.map(::decodeTableCell))
        var index = startIndex + 2
        while (index < lines.size) {
            val line = lines[index]
            if (!line.contains('|')) break
            val cells = splitMarkdownTableRow(line)
            if (cells.isEmpty()) break
            rows += alignRowCells(cells.map(::decodeTableCell), headerCells.size)
            index++
        }
        val normalizedRows = rows.map { alignRowCells(it, headerCells.size) }
        val table = NoteTableData(
            columnCount = headerCells.size,
            headerRowCount = 1,
            cells = normalizedRows.flatten()
        ).normalized()
        return NoteParagraph(type = NoteParagraphType.TABLE, table = table) to index
    }

    private fun isMarkdownTableSeparator(line: String): Boolean {
        val cells = splitMarkdownTableRow(line)
        if (cells.isEmpty()) return false
        return cells.all { cell ->
            val trimmed = cell.trim()
            trimmed.isNotEmpty() && trimmed.all { it == '-' || it == ':' }
        }
    }

    private fun splitMarkdownTableRow(line: String): List<String> {
        val working = line.trim().removePrefix("|").removeSuffix("|")
        if (working.isBlank()) return emptyList()
        val result = mutableListOf<String>()
        val cell = StringBuilder()
        var escaping = false
        working.forEach { char ->
            when {
                escaping -> {
                    cell.append(char)
                    escaping = false
                }
                char == '\\' -> escaping = true
                char == '|' -> {
                    result += cell.toString().trim()
                    cell.clear()
                }
                else -> cell.append(char)
            }
        }
        if (escaping) cell.append('\\')
        result += cell.toString().trim()
        return result
    }

    private fun alignRowCells(cells: List<String>, columnCount: Int): List<String> {
        return when {
            cells.size == columnCount -> cells
            cells.size > columnCount -> cells.take(columnCount)
            else -> cells + List(columnCount - cells.size) { "" }
        }
    }

    private fun decodeTableCell(value: String): String {
        return value
            .replace("<br>", "\n")
            .replace("\\|", "|")
            .replace("\\\\", "\\")
    }

    private fun encodeInline(raw: String, spans: List<NoteTextSpan>): String {
        val builder = StringBuilder(raw)
        spans.sortedWith(compareByDescending<NoteTextSpan> { it.start }.thenByDescending { it.end }).forEach { span ->
            val start = span.start.coerceIn(0, builder.length)
            val end = span.end.coerceIn(0, builder.length)
            if (start >= end) return@forEach
            val open = buildString {
                if (span.strike) append("~~")
                if (span.underline) append("<u>")
                if (span.bold) append("**")
                if (span.italic) append("*")
            }
            val close = buildString {
                if (span.italic) append("*")
                if (span.bold) append("**")
                if (span.underline) append("</u>")
                if (span.strike) append("~~")
            }
            builder.insert(end, close)
            builder.insert(start, open)
        }
        return builder.toString()
    }

    private fun parseInline(raw: String): Pair<String, List<NoteTextSpan>> {
        var text = raw
        val spans = mutableListOf<NoteTextSpan>()
        fun consume(open: String, close: String, style: NoteTextStyle) {
            while (true) {
                val start = text.indexOf(open)
                if (start < 0) return
                val contentStart = start + open.length
                val end = text.indexOf(close, contentStart)
                if (end < 0) return
                val length = end - contentStart
                text = text.removeRange(end, end + close.length).removeRange(start, contentStart)
                if (length > 0) spans += NoteTextSpan(start = start, end = start + length).withStyle(style, true)
            }
        }
        consume("**", "**", NoteTextStyle.BOLD)
        consume("~~", "~~", NoteTextStyle.STRIKE)
        consume("<u>", "</u>", NoteTextStyle.UNDERLINE)
        consume("*", "*", NoteTextStyle.ITALIC)
        return text to spans
    }
}
