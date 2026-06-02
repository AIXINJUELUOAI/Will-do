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
        return NoteTransferData(title = title, document = NoteDocument(paragraphs = paragraphs))
    }

    private fun encodeParagraph(paragraph: NoteParagraph, index: Int): String {
        val text = encodeInline(paragraph.text, paragraph.spans)
        return when (paragraph.type) {
            NoteParagraphType.TODO -> "- [${if (paragraph.checked) "x" else " "}] $text"
            NoteParagraphType.IMAGE -> "![${paragraph.attachmentName.ifBlank { "image" }}](attachments/${File(paragraph.attachmentPath).name})"
            NoteParagraphType.FILE -> "[${paragraph.attachmentName.ifBlank { "file" }}](attachments/${File(paragraph.attachmentPath).name})"
            NoteParagraphType.DIVIDER -> "---"
            NoteParagraphType.TEXT -> when (paragraph.style) {
                NoteParagraphStyle.H1 -> "# $text"
                NoteParagraphStyle.H2 -> "## $text"
                NoteParagraphStyle.H3, NoteParagraphStyle.HEADING -> "### $text"
                NoteParagraphStyle.H4 -> "#### $text"
                NoteParagraphStyle.H5 -> "##### $text"
                NoteParagraphStyle.QUOTE -> "> $text"
                NoteParagraphStyle.CODE -> "```\n${paragraph.text}\n```"
                NoteParagraphStyle.BULLET -> "- $text"
                NoteParagraphStyle.ORDERED -> "${index + 1}. $text"
                NoteParagraphStyle.BODY -> text
            }
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

        Regex("^(#{1,5})\\s+(.*)$").matchEntire(line)?.let { match ->
            val level = match.groupValues[1].length
            val (text, spans) = parseInline(match.groupValues[2])
            return NoteParagraph(text = text, spans = spans, style = headingStyle(level))
        }

        Regex("^>\\s?(.*)$").matchEntire(line)?.let { match ->
            val (text, spans) = parseInline(match.groupValues[1])
            return NoteParagraph(text = text, spans = spans, style = NoteParagraphStyle.QUOTE)
        }

        Regex("^- \\[([ xX])]\\s+(.*)$").matchEntire(line)?.let { match ->
            val (text, spans) = parseInline(match.groupValues[2])
            return NoteParagraph(
                text = text,
                spans = spans,
                type = NoteParagraphType.TODO,
                checked = match.groupValues[1].equals("x", ignoreCase = true)
            )
        }

        Regex("^-\\s+(.*)$").matchEntire(line)?.let { match ->
            val (text, spans) = parseInline(match.groupValues[1])
            return NoteParagraph(text = text, spans = spans, style = NoteParagraphStyle.BULLET)
        }

        Regex("^\\d+\\.\\s+(.*)$").matchEntire(line)?.let { match ->
            val (text, spans) = parseInline(match.groupValues[1])
            return NoteParagraph(text = text, spans = spans, style = NoteParagraphStyle.ORDERED)
        }
        return null
    }

    private fun headingStyle(level: Int): NoteParagraphStyle = when (level) {
        1 -> NoteParagraphStyle.H1
        2 -> NoteParagraphStyle.H2
        3 -> NoteParagraphStyle.H3
        4 -> NoteParagraphStyle.H4
        else -> NoteParagraphStyle.H5
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
