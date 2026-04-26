package com.antgskds.calendarassistant.core.note

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.antgskds.calendarassistant.calendar.helpers.STATE_COMPLETED
import com.antgskds.calendarassistant.calendar.helpers.STATE_PENDING
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import java.time.LocalDate
import java.time.ZoneId

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

fun Event.noteMarkdown(): String {
    return description.trimEnd()
}

fun Event.withNoteMarkdown(title: String = this.title, markdown: String): Event {
    val normalizedMarkdown = markdown.trimEnd()
    val normalizedTitle = title.trim().take(28).ifBlank { "无标题" }
    val tasks = extractMarkdownTasks(normalizedMarkdown)
    val allDone = tasks.isNotEmpty() && tasks.all { it.isDone }
    val targetState = if (allDone) STATE_COMPLETED else STATE_PENDING

    if (
        this.title == normalizedTitle &&
        this.description == normalizedMarkdown &&
        this.tag == EventTags.NOTE &&
        this.reminderMinutes.isEmpty() &&
        this.state == targetState
    ) {
        return this
    }

    return copy(
        title = normalizedTitle,
        description = normalizedMarkdown,
        tag = EventTags.NOTE,
        reminder1Minutes = -1,
        reminder2Minutes = -1,
        reminder3Minutes = -1,
        state = targetState,
        lastUpdated = System.currentTimeMillis() / 1000L
    )
}

fun Event.notePlainText(): String {
    return noteMarkdown()
}

fun createNoteEvent(
    title: String,
    markdown: String,
    color: Color,
    existingId: Long? = null
): Event {
    val normalizedMarkdown = markdown.trimEnd()
    val normalizedTitle = title.trim().take(28).ifBlank { "无标题" }
    val tasks = extractMarkdownTasks(normalizedMarkdown)
    val allDone = tasks.isNotEmpty() && tasks.all { it.isDone }
    val nowSec = System.currentTimeMillis() / 1000L
    val todayStart = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toEpochSecond()
    val todayEnd = todayStart + 86399L

    return Event(
        id = existingId,
        title = normalizedTitle,
        startTS = todayStart,
        endTS = todayEnd,
        location = "",
        description = normalizedMarkdown,
        color = color.toArgb(),
        reminder1Minutes = -1,
        reminder2Minutes = -1,
        reminder3Minutes = -1,
        tag = EventTags.NOTE,
        state = if (allDone) STATE_COMPLETED else STATE_PENDING,
        lastUpdated = nowSec
    )
}
