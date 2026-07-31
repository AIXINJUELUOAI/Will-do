package com.antgskds.calendarassistant.feature.quickmemo.ui.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoSuggestionStatus
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.QuickMemoListUiState
import com.antgskds.calendarassistant.feature.quickmemo.ui.contract.QuickMemoUiAction
import com.antgskds.calendarassistant.feature.home.ui.render.LocalHyperHomeContentInsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun QuickMemoScreen(
    state: QuickMemoListUiState,
    searchQuery: String,
    uiSize: Int,
    extraBottomPadding: Dp,
    hapticEnabled: Boolean,
    onAction: (QuickMemoUiAction) -> Unit,
) {
    val contentInsets = LocalHyperHomeContentInsets.current
    val listState = rememberLazyListState()
    val filteredMemos = remember(state.memos, searchQuery) {
        state.memos
            .filter { searchQuery.isBlank() || it.bodyText.contains(searchQuery, ignoreCase = true) }
            .sortedWith(
                compareBy<QuickMemoEntity> { it.sortRank }
                    .thenByDescending { it.updatedAt }
                    .thenByDescending { it.createdAt },
            )
    }
    val groupedMemos = remember(filteredMemos) {
        filteredMemos
            .groupBy {
                LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(it.createdAt),
                    ZoneId.systemDefault(),
                ).toLocalDate()
            }
            .toSortedMap(compareByDescending { it })
    }
    val suggestionsByMemo = remember(state.suggestions) {
        state.suggestions
            .filter {
                it.status == QuickMemoSuggestionStatus.PENDING ||
                    it.status == QuickMemoSuggestionStatus.CREATED
            }
            .groupBy { it.quickMemoId }
    }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINESE) }
    val firstKey = filteredMemos.firstOrNull()?.id

    LaunchedEffect(firstKey, searchQuery) {
        if (firstKey != null && searchQuery.isBlank()) listState.animateScrollToItem(0)
    }

    if (filteredMemos.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (searchQuery.isBlank()) "还没有随口记" else "未找到相关随口记",
                    color = MiuixTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = if (searchQuery.isBlank()) "点击右上角新建一条随口记" else "换个关键词试试",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp,
                )
            }
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = contentInsets.top + 6.dp,
            bottom = contentInsets.bottom + 24.dp + extraBottomPadding,
        ),
    ) {
        groupedMemos.forEach { (date, memos) ->
            item(key = "memo_header_$date") {
                Text(
                    text = date.format(dateFormatter),
                    modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 10.dp),
                    color = MiuixTheme.colorScheme.primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(memos, key = { it.id ?: it.hashCode().toLong() }) { memo ->
                HyperQuickMemoItem(
                    memo = memo,
                    hasSuggestions = memo.id?.let { suggestionsByMemo[it] }.orEmpty().isNotEmpty(),
                    playbackState = state.playbackState,
                    isPinned = memo.id == state.pinnedMemoId,
                    onToggleTodo = {
                        memo.id?.let { onAction(QuickMemoUiAction.ToggleTodoCompletion(it)) }
                    },
                    onToggleTodoMode = {
                        memo.id?.let { id ->
                            onAction(
                                if (memo.isTodo) QuickMemoUiAction.RemoveTodo(id)
                                else QuickMemoUiAction.MarkTodo(id),
                            )
                        }
                    },
                    onTogglePinned = {
                        memo.id?.let { onAction(QuickMemoUiAction.TogglePinned(it, memo.id == state.pinnedMemoId)) }
                    },
                    onDelete = { memo.id?.let { onAction(QuickMemoUiAction.Delete(it)) } },
                    onToggleAudio = { onAction(QuickMemoUiAction.ToggleAudio(it)) },
                    onOpenDetail = { memo.id?.let { onAction(QuickMemoUiAction.OpenDetail(it)) } },
                    onLongPress = { onAction(QuickMemoUiAction.RequestDelete(memo)) },
                    hapticEnabled = hapticEnabled,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                )
            }
        }
    }
}
