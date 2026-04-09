package com.antgskds.calendarassistant.ui.page_display

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.core.note.extractMarkdownTasks
import com.antgskds.calendarassistant.core.note.noteMarkdown
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.ui.components.NoteCard
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel

@Composable
fun NotePage(
    viewModel: MainViewModel,
    searchQuery: String = "",
    extraBottomPadding: Dp = 0.dp,
    onEditNote: (MyEvent) -> Unit = {},
    onPendingDeleteChange: (MyEvent?) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val bottomSafePadding = 112.dp + extraBottomPadding
    val notes = remember(uiState.noteEvents, searchQuery) {
        uiState.noteEvents.filter { note ->
            val markdown = note.noteMarkdown()
            val tasks = extractMarkdownTasks(markdown)
            if (searchQuery.isBlank()) {
                true
            } else {
                note.title.contains(searchQuery, ignoreCase = true) ||
                    markdown.contains(searchQuery, ignoreCase = true) ||
                    tasks.any { it.text.contains(searchQuery, ignoreCase = true) }
            }
        }.sortedWith(compareBy<MyEvent> { it.isCompleted }.thenByDescending { it.lastModified })
    }
    val completedNotes = remember(notes) { notes.count { it.isCompleted } }
    val pendingTaskCount = remember(notes) {
        notes.sumOf { note ->
            extractMarkdownTasks(note.noteMarkdown()).count { !it.isDone }
        }
    }

    if (notes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (searchQuery.isBlank()) "还没有便签" else "未找到相关便签",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (searchQuery.isBlank()) {
                        "试试用底部按钮记下一条想法、清单或临时备忘。"
                    } else {
                        "换个关键词，或者到编辑页里补充更明确的标题和正文。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 10.dp,
                bottom = bottomSafePadding + 24.dp
            )
        ) {
            item(key = "summary") {
                Text(
                    text = buildNoteSummaryText(
                        noteCount = notes.size,
                        completedCount = completedNotes,
                        pendingTaskCount = pendingTaskCount,
                        searchQuery = searchQuery
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 10.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(notes, key = { it.id }) { note ->
                NoteCard(
                    note = note,
                    onClick = { onEditNote(note) },
                    onLongClick = { onPendingDeleteChange(note) }
                )
            }
        }
    }
}

private fun buildNoteSummaryText(
    noteCount: Int,
    completedCount: Int,
    pendingTaskCount: Int,
    searchQuery: String
): String {
    if (searchQuery.isNotBlank()) {
        return "关键词“$searchQuery”匹配到 $noteCount 条便签"
    }

    return buildString {
        append("共 $noteCount 条便签")
        if (pendingTaskCount > 0) {
            append(" · $pendingTaskCount 项待办")
        }
        if (completedCount > 0) {
            append(" · $completedCount 条已完成")
        }
    }
}
