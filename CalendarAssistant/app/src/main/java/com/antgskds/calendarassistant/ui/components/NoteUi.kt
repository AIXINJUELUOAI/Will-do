package com.antgskds.calendarassistant.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.core.note.MarkdownTaskItem
import com.antgskds.calendarassistant.core.note.extractMarkdownTasks
import com.antgskds.calendarassistant.core.note.markdownWithoutTasks
import com.antgskds.calendarassistant.core.note.noteMarkdown
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.*
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
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
    note: Event,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    hapticEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAppHaptics(hapticEnabled)
    val markdown = remember(note.description, note.lastModifiedMillis) { note.noteMarkdown() }
    val tasks = remember(markdown) { extractMarkdownTasks(markdown) }
    val previewTasks = remember(tasks) { tasks.take(3) }
    val remainingTaskCount = remember(tasks, previewTasks) { (tasks.size - previewTasks.size).coerceAtLeast(0) }
    val previewText = remember(markdown) { buildNotePreview(markdownWithoutTasks(markdown)) ?: buildNotePreview(markdown) }
    val updatedLabel = remember(note.lastModifiedMillis) { formatNoteUpdatedText(note.lastModifiedMillis) }
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
                    it.combinedClickable(
                        onClick = {
                            haptics.click()
                            onClick()
                        },
                        onLongClick = {
                            haptics.longPress()
                            onLongClick()
                        }
                    )
                } else {
                    it.clickable {
                        haptics.click()
                        onClick()
                    }
                }
            }
    ) {
        Text(
            text = note.title,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (note.isCompleted && tasks.isNotEmpty()) TextDecoration.LineThrough else null,
            color = titleColor
        )

        if (previewTasks.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                previewTasks.forEach { task ->
                    NoteTaskPreviewRow(task = task)
                }
                if (remainingTaskCount > 0) {
                    Text(
                        text = "+$remainingTaskCount 项",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        } else {
            Text(
                text = previewText ?: "空白便签",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Text(
            text = updatedLabel,
            modifier = Modifier.padding(top = 10.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.58f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        HorizontalDivider(
            modifier = Modifier.padding(top = 18.dp),
            thickness = 0.6.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun NoteTaskPreviewRow(task: MarkdownTaskItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        NoteTaskMark(done = task.isDone)
        Text(
            text = task.text.ifBlank { "未命名待办" },
            style = MaterialTheme.typography.bodyMedium,
            color = if (task.isDone) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (task.isDone) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NoteTaskMark(done: Boolean) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(5.dp)
    Box(
        modifier = Modifier
            .padding(top = 2.dp)
            .size(18.dp)
            .background(if (done) accent.copy(alpha = 0.14f) else Color.Transparent, shape)
            .border(1.dp, accent.copy(alpha = if (done) 0.52f else 0.36f), shape),
        contentAlignment = Alignment.Center
    ) {
        if (done) {
            Text(
                text = "✓",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accent
            )
        }
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
