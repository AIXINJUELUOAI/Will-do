package com.antgskds.calendarassistant.feature.note.ui.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.Unpin
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    SmallTopAppBar(
        title = title,
        color = MiuixTheme.colorScheme.surface,
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(MiuixIcons.Normal.Back, "返回")
            }
        },
        actions = {
            if (canManage) {
                IconButton(onClick = onTogglePinned) {
                    Icon(
                        if (pinned) MiuixIcons.Normal.Unpin else MiuixIcons.Normal.Pin,
                        if (pinned) "取消置顶" else "置顶",
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(MiuixIcons.Normal.Delete, "删除")
                }
            }
        },
    )
}
