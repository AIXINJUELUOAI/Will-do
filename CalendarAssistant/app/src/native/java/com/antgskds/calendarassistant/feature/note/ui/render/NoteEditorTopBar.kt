package com.antgskds.calendarassistant.feature.note.ui.render

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.shared.ui.adaptive.LocalAdaptiveLayoutInfo

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
    val useWideHeader = LocalAdaptiveLayoutInfo.current.useNavigationRail
    val titleContent: @Composable () -> Unit = { Text(title) }
    val navigationContent: @Composable () -> Unit = {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", Modifier.size(28.dp))
        }
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
        } else {
            Box(Modifier.width(48.dp))
        }
    }

    if (useWideHeader) TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = containerColor),
        title = titleContent,
        navigationIcon = navigationContent,
        actions = actionsContent,
    ) else CenterAlignedTopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = containerColor),
        title = titleContent,
        navigationIcon = navigationContent,
        actions = actionsContent,
    )
}
