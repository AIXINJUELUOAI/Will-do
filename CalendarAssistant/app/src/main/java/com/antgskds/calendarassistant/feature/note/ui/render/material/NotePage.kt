package com.antgskds.calendarassistant.feature.note.ui.render.material

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.note.ui.render.material.component.NoteCard
import com.antgskds.calendarassistant.feature.note.ui.contract.NoteListUiAction
import com.antgskds.calendarassistant.feature.note.ui.contract.NoteListUiState

@Composable
fun MaterialNoteListScreen(
    state: NoteListUiState,
    extraBottomPadding: Dp = 0.dp,
    onAction: (NoteListUiAction) -> Unit
) {
    val bottomSafePadding = 112.dp + extraBottomPadding

    if (state.items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = if (state.searchQuery.isBlank()) "还没有便签" else "未找到相关便签",
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = if (state.searchQuery.isBlank()) {
                        "试试用底部按钮记下一条想法、清单或临时备忘。"
                    } else {
                        "换个关键词，或者到编辑页里补充更明确的标题和正文。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f)
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
                        noteCount = state.items.size,
                        pendingTaskCount = state.pendingTaskCount,
                        searchQuery = state.searchQuery
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp, horizontal = 20.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            items(state.items, key = { it.key }) { note ->
                Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                    NoteCard(
                        note = note,
                        onClick = { onAction(NoteListUiAction.OpenNote(note.key)) },
                        onLongClick = { onAction(NoteListUiAction.RequestDelete(note.key)) },
                        onToggleTodo = { paragraphId ->
                            onAction(NoteListUiAction.ToggleTodo(note.key, paragraphId))
                        },
                        hapticEnabled = state.hapticEnabled
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
