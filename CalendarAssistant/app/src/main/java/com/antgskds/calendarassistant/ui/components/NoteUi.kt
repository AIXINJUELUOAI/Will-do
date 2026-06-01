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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.core.note.NoteEntity
import com.antgskds.calendarassistant.core.note.NoteParagraph
import com.antgskds.calendarassistant.core.note.NoteParagraphType
import com.antgskds.calendarassistant.ui.haptic.rememberAppHaptics
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val noteTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
private val noteShortDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日 HH:mm")
private val noteFullDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteEntity,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onToggleTodo: (String) -> Unit = {},
    hapticEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val haptics = rememberAppHaptics(hapticEnabled)
    val document = remember(note.documentJson, note.plainText) { note.document() }
    val tasks = remember(document) { document.paragraphs.filter { it.type == NoteParagraphType.TODO } }
    val previewTasks = remember(tasks) { tasks.take(3) }
    val remainingTaskCount = remember(tasks, previewTasks) { (tasks.size - previewTasks.size).coerceAtLeast(0) }
    val previewText = remember(document) { buildNotePreview(document.paragraphs) }
    val updatedLabel = remember(note.updatedAt) { formatNoteUpdatedText(note.updatedAt) }
    val isCompleted = document.allTodosCompleted()
    val titleColor = if (isCompleted) {
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
            text = note.displayTitle,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp, bottom = 10.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textDecoration = if (isCompleted && tasks.isNotEmpty()) TextDecoration.LineThrough else null,
            color = titleColor
        )

        if (previewTasks.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                previewTasks.forEach { task ->
                    NoteTaskPreviewRow(
                        task = task,
                        onToggle = {
                            haptics.click()
                            onToggleTodo(task.id)
                        }
                    )
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
private fun NoteTaskPreviewRow(task: NoteParagraph, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        NoteTaskMark(done = task.checked, onClick = onToggle)
        Text(
            text = styledPreviewText(task),
            style = MaterialTheme.typography.bodyMedium,
            color = if (task.checked) {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textDecoration = if (task.checked) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun NoteTaskMark(done: Boolean, onClick: () -> Unit) {
    val accent = if (done) Color.Gray else MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(4.dp)
    Box(
        modifier = Modifier
            .width(24.dp)
            .height(24.dp)
            .clickable { onClick() },
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
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
}

private fun styledPreviewText(paragraph: NoteParagraph) = buildAnnotatedString {
    val text = paragraph.text.ifBlank { "未命名待办" }
    append(text)
    paragraph.spans.forEach { span ->
        val start = span.start.coerceIn(0, text.length)
        val end = span.end.coerceIn(0, text.length)
        if (start < end) {
            addStyle(
                SpanStyle(
                    fontWeight = if (span.bold) FontWeight.Bold else null,
                    fontStyle = if (span.italic) FontStyle.Italic else null,
                    textDecoration = when {
                        span.underline && span.strike -> TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
                        span.underline -> TextDecoration.Underline
                        span.strike -> TextDecoration.LineThrough
                        else -> null
                    }
                ),
                start,
                end
            )
        }
    }
}

private fun buildNotePreview(paragraphs: List<NoteParagraph>): String? {
    val summary = paragraphs
        .asSequence()
        .filterNot { it.type == NoteParagraphType.TODO }
        .map { it.text.trim() }
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
