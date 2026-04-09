package com.antgskds.calendarassistant.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.core.note.MarkdownTaskItem
import com.antgskds.calendarassistant.core.note.extractMarkdownTasks
import com.antgskds.calendarassistant.core.note.noteMarkdown
import com.antgskds.calendarassistant.data.model.MyEvent
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val noteTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val noteShortDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")
private val noteFullDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

private val markdownHeadingRegex = Regex("^#{1,6}\\s+")
private val markdownQuoteRegex = Regex("^>+\\s*")
private val markdownTaskRegex = Regex("^[-+*]\\s+\\[(?: |x|X)]\\s*")
private val markdownBulletRegex = Regex("^[-+*]\\s+")
private val markdownOrderedRegex = Regex("^\\d+\\.\\s+")
private val markdownDividerRegex = Regex("^\\s*([-*_]\\s*){3,}$")
private val markdownLinkRegex = Regex("\\[(.+?)]\\((.+?)\\)")
private val markdownInlineCodeRegex = Regex("`([^`]*)`")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: MyEvent,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val markdown = remember(note.description, note.lastModified) { note.noteMarkdown() }
    val tasks = remember(markdown) { extractMarkdownTasks(markdown) }
    val previewText = remember(markdown) { buildNotePreview(markdown) }
    val updatedLabel = remember(note.lastModified) { formatNoteUpdatedText(note.lastModified) }
    val secondaryText = remember(tasks, previewText) {
        taskSummaryText(tasks) ?: previewText ?: "空白便签"
    }
    val titleColor = if (note.isCompleted) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .let {
                if (onLongClick != null) {
                    it.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                } else {
                    it.clickable(onClick = onClick)
                }
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (note.isCompleted && tasks.isNotEmpty()) TextDecoration.LineThrough else null,
                    color = titleColor
                )
                Text(
                    text = "$secondaryText · $updatedLabel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        HorizontalDivider(
            thickness = 0.6.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
    }
}

private fun taskSummaryText(tasks: List<MarkdownTaskItem>): String? {
    if (tasks.isEmpty()) return null

    val doneCount = tasks.count { it.isDone }
    val pendingCount = tasks.size - doneCount
    return when {
        doneCount == 0 -> "${tasks.size} 项待办"
        pendingCount == 0 -> "${tasks.size} 项已完成"
        else -> "$pendingCount 项待办，$doneCount 项已完成"
    }
}

private fun buildNotePreview(markdown: String): String? {
    val summary = markdown.lineSequence()
        .map { it.trim() }
        .filterNot { it.isBlank() || markdownDividerRegex.matches(it) }
        .map { line ->
            line
                .replace(markdownHeadingRegex, "")
                .replace(markdownQuoteRegex, "")
                .replace(markdownTaskRegex, "")
                .replace(markdownBulletRegex, "")
                .replace(markdownOrderedRegex, "")
                .replace(markdownLinkRegex, "$1")
                .replace(markdownInlineCodeRegex, "$1")
                .replace("**", "")
                .replace("__", "")
                .replace("*", "")
                .replace("_", "")
                .replace("~~", "")
                .replace("|", " ")
                .trim()
        }
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .replace(Regex("\\s+"), " ")
        .trim()

    return summary.takeIf { it.isNotBlank() }
}

private fun formatNoteUpdatedText(lastModified: Long): String {
    val modifiedAt = Instant.ofEpochMilli(lastModified)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
    val now = LocalDateTime.now()
    val modifiedDate = modifiedAt.toLocalDate()
    val nowDate = now.toLocalDate()

    return when {
        modifiedAt.isAfter(now.minusMinutes(10)) -> "刚刚"
        modifiedDate == nowDate -> "今天 ${modifiedAt.format(noteTimeFormatter)}"
        modifiedAt.year == now.year -> modifiedAt.format(noteShortDateTimeFormatter)
        else -> modifiedAt.format(noteFullDateFormatter)
    }
}
