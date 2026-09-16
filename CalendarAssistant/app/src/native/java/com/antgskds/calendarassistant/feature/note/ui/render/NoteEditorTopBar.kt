package com.antgskds.calendarassistant.feature.note.ui.render

import com.antgskds.calendarassistant.shared.ui.material.component.AppTopBar
import com.antgskds.calendarassistant.shared.ui.material.component.AppTopBarBackButton

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorTopBar(
    title: String,
    canManage: Boolean,
    pinned: Boolean,
    containerColor: Color,
    onBack: () -> Unit,
    onTogglePinned: () -> Unit,
    onDelete: () -> Unit,
) {
    val navigationContent: @Composable () -> Unit = {
        AppTopBarBackButton(onClick = onBack)
    }
    val actionsContent: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        if (canManage) {
            IconButton(onClick = onTogglePinned) {
                Icon(
                    Icons.Default.PushPin,
                    if (pinned) "取消置顶" else "置顶",
                    tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.DeleteOutline, "删除", Modifier.size(28.dp))
            }
        }
    }

    AppTopBar(
        title = title,
        containerColor = containerColor,
        navigationIcon = navigationContent,
        actions = actionsContent,
    )
}
