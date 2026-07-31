package com.antgskds.calendarassistant.feature.note.ui.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.note.ui.contract.NoteListItemUiModel
import com.antgskds.calendarassistant.feature.note.ui.contract.NoteListUiAction
import com.antgskds.calendarassistant.feature.note.ui.contract.NoteListUiState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Checkbox
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun NoteListScreen(
    state: NoteListUiState,
    extraBottomPadding: Dp,
    onAction: (NoteListUiAction) -> Unit,
) {
    if (state.items.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(if (state.searchQuery.isBlank()) "还没有便签" else "未找到相关便签")
                Text(
                    text = if (state.searchQuery.isBlank()) "记录一条想法、清单或临时备忘" else "请尝试其他关键词",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            top = 14.dp,
            end = 16.dp,
            bottom = 136.dp + extraBottomPadding,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "summary") {
            Text(
                text = buildNoteSummaryText(state),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold,
                fontSize = MiuixTheme.textStyles.title3.fontSize,
            )
        }
        items(state.items, key = { it.key }) { note ->
            HyperNoteCard(note = note, onAction = onAction)
        }
    }
}

@Composable
private fun HyperNoteCard(
    note: NoteListItemUiModel,
    onAction: (NoteListUiAction) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(16.dp),
        onClick = { onAction(NoteListUiAction.OpenNote(note.key)) },
        onLongPress = { onAction(NoteListUiAction.RequestDelete(note.key)) },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = note.title.ifBlank { "无标题便签" },
                    fontWeight = FontWeight.Bold,
                    fontSize = MiuixTheme.textStyles.headline1.fontSize,
                )
                if (note.pinned) {
                    Text(
                        text = "置顶",
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                    )
                }
            }
            note.previewText?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    maxLines = 3,
                )
            }
            note.previewTasks.take(3).forEach { task ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        state = ToggleableState(task.checked),
                        onClick = { onAction(NoteListUiAction.ToggleTodo(note.key, task.paragraphId)) },
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = task.text,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        textDecoration = if (task.checked) TextDecoration.LineThrough else null,
                        maxLines = 2,
                    )
                }
            }
            if (note.remainingTaskCount > 0) {
                Text(
                    text = "还有 ${note.remainingTaskCount} 项",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                )
            }
        }
    }
}

private fun buildNoteSummaryText(state: NoteListUiState): String {
    if (state.searchQuery.isNotBlank()) return "“${state.searchQuery}”匹配到 ${state.items.size} 条便签"
    return buildString {
        append("共 ${state.items.size} 条便签")
        if (state.pendingTaskCount > 0) append(" · ${state.pendingTaskCount} 项待办")
    }
}
