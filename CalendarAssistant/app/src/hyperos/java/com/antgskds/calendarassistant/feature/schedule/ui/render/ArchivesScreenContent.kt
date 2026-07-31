package com.antgskds.calendarassistant.feature.schedule.ui.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.schedule.ui.contract.ArchivesUiAction
import com.antgskds.calendarassistant.feature.schedule.ui.contract.ArchivesUiState
import java.time.format.DateTimeFormatter
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ArchivesScreenContent(
    state: ArchivesUiState,
    onAction: (ArchivesUiAction) -> Unit,
) {
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    if (state.groups.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("暂无归档", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 16.dp + bottomInset),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        state.groups.forEach { group ->
            item(key = "header_${group.date}") {
                val pattern = if (group.date.year == state.currentYear) "M月d日" else "yyyy年M月d日"
                Text(
                    text = group.date.format(DateTimeFormatter.ofPattern(pattern)),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                    color = MiuixTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }
            items(group.items, key = { it.stableKey }) { item ->
                HyperEventItem(
                    item = item,
                    isRevealed = false,
                    timeRefreshToken = state.timeRefreshToken,
                    onExpand = {},
                    onCollapse = {},
                    onDelete = { item.eventId?.let { onAction(ArchivesUiAction.Delete(it)) } },
                    onEdit = {},
                    isArchivePage = true,
                    onRestore = { item.eventId?.let { onAction(ArchivesUiAction.Restore(it)) } },
                    hapticEnabled = state.hapticEnabled,
                )
            }
        }
    }
}
