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
import com.antgskds.calendarassistant.core.note.NoteEntity
import com.antgskds.calendarassistant.ui.components.NoteCard
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel

@Composable
fun NotePage(
    viewModel: MainViewModel,
    searchQuery: String = "",
    extraBottomPadding: Dp = 0.dp,
    onEditNote: (NoteEntity) -> Unit = {},
    onPendingDeleteChange: (NoteEntity?) -> Unit = {},
    hapticEnabled: Boolean = true
) {
    val notes by viewModel.notes.collectAsState()
    val bottomSafePadding = 112.dp + extraBottomPadding
    val filteredNotes = remember(notes, searchQuery) {
        notes.filter { note ->
            if (searchQuery.isBlank()) {
                true
            } else {
                note.title.contains(searchQuery, ignoreCase = true) ||
                    note.plainText.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    val pendingTaskCount = remember(filteredNotes) {
        filteredNotes.sumOf { it.document().pendingTodoCount() }
    }

    if (filteredNotes.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (searchQuery.isBlank()) "还没有便签" else "未找到相关便签",
                    color = MaterialTheme.colorScheme.secondary
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
                top = 10.dp,
                bottom = bottomSafePadding + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item(key = "summary") {
                Text(
                    text = buildNoteSummaryText(
                        noteCount = filteredNotes.size,
                        pendingTaskCount = pendingTaskCount,
                        searchQuery = searchQuery
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 20.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            items(filteredNotes, key = { it.id ?: 0L }) { note ->
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    NoteCard(
                        note = note,
                        onClick = { onEditNote(note) },
                        onLongClick = { onPendingDeleteChange(note) },
                        onToggleTodo = { paragraphId ->
                            note.id?.let { viewModel.toggleNoteTodo(it, paragraphId) }
                        },
                        hapticEnabled = hapticEnabled
                    )
                }
            }
        }
    }
}

private fun buildNoteSummaryText(
    noteCount: Int,
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
    }
}
