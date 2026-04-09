package com.antgskds.calendarassistant.core.note

import androidx.compose.ui.graphics.Color
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.EventType
import com.antgskds.calendarassistant.data.model.MyEvent
import java.time.LocalDate
import java.util.UUID

data class MarkdownTaskItem(
    val lineIndex: Int,
    val text: String,
    val isDone: Boolean
)

private val markdownTaskRegex = Regex("^(\\s*[-*+]\\s+\\[)( |x|X)(](?:\\s+|$))(.*)$")

fun extractMarkdownTasks(markdown: String): List<MarkdownTaskItem> {
    return markdown.lines().mapIndexedNotNull { index, line ->
        val match = markdownTaskRegex.matchEntire(line) ?: return@mapIndexedNotNull null
        MarkdownTaskItem(
            lineIndex = index,
            text = match.groupValues[4].trim(),
            isDone = match.groupValues[2].equals("x", ignoreCase = true)
        )
    }
}

fun toggleMarkdownTask(markdown: String, lineIndex: Int): String {
    val lines = markdown.lines().toMutableList()
    if (lineIndex !in lines.indices) return markdown
    val line = lines[lineIndex]
    val match = markdownTaskRegex.matchEntire(line) ?: return markdown
    val toggled = if (match.groupValues[2].equals("x", ignoreCase = true)) " " else "x"
    lines[lineIndex] = buildString {
        append(match.groupValues[1])
        append(toggled)
        append(match.groupValues[3])
        append(match.groupValues[4])
    }
    return lines.joinToString("\n")
}

fun markdownWithoutTasks(markdown: String): String {
    return markdown.lines()
        .filterNot { markdownTaskRegex.matches(it) }
        .joinToString("\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

fun MyEvent.noteMarkdown(): String {
    return description.trimEnd()
}

fun MyEvent.withNoteMarkdown(title: String = this.title, markdown: String): MyEvent {
    val normalizedMarkdown = markdown.trimEnd()
    val normalizedTitle = title.trim().take(28).ifBlank { "无标题" }
    val tasks = extractMarkdownTasks(normalizedMarkdown)
    val allDone = tasks.isNotEmpty() && tasks.all { it.isDone }

    return copy(
        title = normalizedTitle,
        description = normalizedMarkdown,
        tag = EventTags.NOTE,
        eventType = EventType.EVENT,
        skipCalendarSync = true,
        reminders = emptyList(),
        isCompleted = allDone,
        completedAt = if (allDone) System.currentTimeMillis() else null,
        lastModified = System.currentTimeMillis()
    )
}

fun MyEvent.notePlainText(): String {
    return noteMarkdown()
}

fun createNoteEvent(
    title: String,
    markdown: String,
    color: Color,
    existingId: String? = null
): MyEvent {
    val normalizedMarkdown = markdown.trimEnd()
    val normalizedTitle = title.trim().take(28).ifBlank { "无标题" }
    val tasks = extractMarkdownTasks(normalizedMarkdown)
    val allDone = tasks.isNotEmpty() && tasks.all { it.isDone }

    return MyEvent(
        id = existingId ?: UUID.randomUUID().toString(),
        title = normalizedTitle,
        startDate = LocalDate.now(),
        endDate = LocalDate.now(),
        startTime = "00:00",
        endTime = "23:59",
        location = "",
        description = normalizedMarkdown,
        color = color,
        reminders = emptyList(),
        eventType = EventType.EVENT,
        tag = EventTags.NOTE,
        isCompleted = allDone,
        completedAt = if (allDone) System.currentTimeMillis() else null,
        skipCalendarSync = true
    )
}
