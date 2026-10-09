package com.antgskds.calendarassistant.feature.quickmemo.ui.render.material

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Topic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.shared.ui.edition.EditionCheckbox
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.semantics.Role
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoFolderEntity
import com.antgskds.calendarassistant.shared.ui.material.component.AppDropdownMenu
import com.antgskds.calendarassistant.shared.ui.material.component.AppMenuItem
import com.antgskds.calendarassistant.shared.ui.material.component.AppModalBottomSheet
import com.antgskds.calendarassistant.shared.ui.material.component.AppSheetAction
import com.antgskds.calendarassistant.shared.ui.material.component.AppSheetActionRole

@Composable
fun QuickMemoSelectionToolbar(
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    enabled: Boolean,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    backgroundMode: Boolean,
    miuiBlurEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    QuickMemoToolbar(backgroundMode, miuiBlurEnabled, modifier, itemCount = 3) { itemWidth ->
        val contentColor = LocalContentColor.current
        QuickMemoActionButton(
            icon = ImageVector.vectorResource(R.drawable.quickmemo_select_all),
            contentDescription = if (allSelected) "取消全选" else "全选",
            isActive = false, width = itemWidth,
            indicatorColor = Color.Transparent, contentColor = contentColor,
            disabledColor = contentColor.copy(alpha = 0.38f), onClick = onSelectAll,
        )
        QuickMemoActionButton(
            icon = Icons.Rounded.DriveFileMove, contentDescription = "移动至",
            isActive = false, enabled = enabled, width = itemWidth,
            indicatorColor = Color.Transparent, contentColor = contentColor,
            disabledColor = contentColor.copy(alpha = 0.38f), onClick = onMove,
        )
        QuickMemoActionButton(
            icon = Icons.Rounded.Delete, contentDescription = "删除",
            isActive = false, enabled = enabled, width = itemWidth,
            indicatorColor = Color.Transparent, contentColor = contentColor,
            disabledColor = contentColor.copy(alpha = 0.38f), onClick = onDelete,
        )
    }
}

/** The caller composes this inside the top-bar button's Box to keep the popup anchored. */
@Composable
fun QuickMemoFolderMenu(
    expanded: Boolean,
    folders: List<QuickMemoFolderEntity>,
    selectedFolderId: String?,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
    onCreate: (String, (Result<Unit>) -> Unit) -> Unit,
    onDelete: (String, (Result<Unit>) -> Unit) -> Unit,
    onRename: (String, String, (Result<Unit>) -> Unit) -> Unit,
    onError: (String) -> Unit,
    containerColor: Color,
    selectionColor: Color,
    contentColor: Color,
    additionalItems: List<AppMenuItem> = emptyList(),
) {
    var creating by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var renamingFolder by remember { mutableStateOf<QuickMemoFolderEntity?>(null) }
    var deletingFolder by remember { mutableStateOf<QuickMemoFolderEntity?>(null) }
    var busy by remember { mutableStateOf(false) }
    val selectedFolder = folders.firstOrNull { it.id == selectedId }
    AppDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = containerColor, selectionColor = selectionColor, contentColor = contentColor,
        items = buildList {
            addAll(additionalItems)
            add(AppMenuItem("全部随口记", { onSelect(null) }, Icons.Rounded.Topic, selected = selectedFolderId == null))
            add(AppMenuItem("未分组", { onSelect("") }, Icons.Rounded.FolderOff, selected = selectedFolderId == ""))
            folders.forEach { folder ->
                add(AppMenuItem(folder.name, { onSelect(folder.id) }, Icons.Rounded.Folder, selected = selectedFolderId == folder.id))
            }
            add(AppMenuItem("新建文件夹", { creating = true }, Icons.Rounded.CreateNewFolder, dividerBefore = true))
            add(AppMenuItem("管理文件夹", {
                selectedId = selectedFolderId?.takeIf { id -> folders.any { it.id == id } }
                managing = true
            }, Icons.Rounded.Settings, enabled = folders.isNotEmpty()))
        },
    )
    QuickMemoFolderNameSheet(creating, { creating = false }, onCreate, onError)
    QuickMemoManageFoldersSheet(
        visible = managing && renamingFolder == null && deletingFolder == null,
        folders = folders,
        selectedId = selectedFolder?.id,
        onSelect = { selectedId = it },
        onDismiss = { managing = false },
        onRename = { renamingFolder = selectedFolder },
        onDelete = { deletingFolder = selectedFolder },
    )
    val renameTarget = renamingFolder
    QuickMemoFolderNameSheet(
        visible = renameTarget != null,
        onDismiss = { renamingFolder = null },
        onSave = { name, callback ->
            renameTarget?.let { folder -> onRename(folder.id, name, callback) }
        },
        onError = onError,
        title = "重命名文件夹",
        confirmText = "保存",
        initialName = renameTarget?.name.orEmpty(),
    )
    val deleteTarget = deletingFolder
    QuickMemoDeleteConfirmationSheet(
        visible = deleteTarget != null,
        title = "删除文件夹",
        message = "删除“${deleteTarget?.name.orEmpty()}”后，其中的随口记将移至未分组。",
        confirmText = "删除文件夹", isLoading = busy, confirmEnabled = deleteTarget != null,
        onConfirm = {
            if (!busy) deleteTarget?.let { folder ->
                busy = true
                onDelete(folder.id) { result ->
                    busy = false
                    result.onSuccess {
                        deletingFolder = null
                        if (selectedId == folder.id) selectedId = null
                    }.onFailure { onError(it.message ?: "删除失败") }
                }
            }
        },
        onDismiss = { deletingFolder = null },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickMemoManageFoldersSheet(
    visible: Boolean,
    folders: List<QuickMemoFolderEntity>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    val haptics = rememberAppHaptics()
    if (visible) AppModalBottomSheet(
        title = "管理文件夹",
        subtitle = "选择一个文件夹进行重命名或删除",
        onDismissRequest = onDismiss,
        actions = listOf(
            AppSheetAction("重命名", onRename, enabled = selectedId != null),
            AppSheetAction("删除", onDelete, enabled = selectedId != null),
        ),
    ) {
        folders.forEach { folder ->
            val checked = folder.id == selectedId
            ListItem(
                headlineContent = { Text(folder.name) },
                leadingContent = { Icon(Icons.Rounded.Folder, null) },
                trailingContent = {
                    EditionCheckbox(checked = checked, onCheckedChange = null, modifier = Modifier.size(48.dp))
                },
                modifier = Modifier.fillMaxWidth().toggleable(
                    value = checked, role = Role.Checkbox,
                    onValueChange = { haptics.selection(); onSelect(if (it) folder.id else null) },
                ),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMemoFolderPicker(
    visible: Boolean,
    folders: List<QuickMemoFolderEntity>,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit,
    onCreate: (String, (Result<Unit>) -> Unit) -> Unit,
    onError: (String) -> Unit,
) {
    var creating by remember(visible) { mutableStateOf(false) }
    val busy by rememberUpdatedState(isLoading)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !busy },
    )
    if (visible && !creating) AppModalBottomSheet(
        title = "移动至",
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        actions = listOf(
            AppSheetAction("取消", onDismiss, AppSheetActionRole.Secondary, enabled = !busy),
            AppSheetAction("新建文件夹", { creating = true }, enabled = !busy),
        ),
    ) {
        ListItem(
            headlineContent = { Text("未分组") },
            leadingContent = { Icon(Icons.Rounded.FolderOff, null) },
            modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { onSelect(null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        folders.forEach { folder ->
            ListItem(
                headlineContent = { Text(folder.name) },
                leadingContent = { Icon(Icons.Rounded.Folder, null) },
                modifier = Modifier.fillMaxWidth().clickable(enabled = !busy) { onSelect(folder.id) },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
    }
    QuickMemoFolderNameSheet(visible && creating, { creating = false }, onCreate, onError)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickMemoFolderNameSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, (Result<Unit>) -> Unit) -> Unit,
    onError: (String) -> Unit,
    title: String = "新建文件夹",
    confirmText: String = "创建",
    initialName: String = "",
) {
    var name by remember(visible, initialName) { mutableStateOf(initialName) }
    var busy by remember(visible) { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !busy },
    )
    val save = {
        if (!busy && name.isNotBlank()) {
            busy = true
            onSave(name) { result ->
                busy = false
                result.onSuccess { onDismiss() }.onFailure { onError(it.message ?: "保存失败") }
            }
        }
    }
    if (visible) AppModalBottomSheet(
        title = title,
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        actions = listOf(
            AppSheetAction("取消", onDismiss, AppSheetActionRole.Secondary, enabled = !busy),
            AppSheetAction(if (busy) "保存中…" else confirmText, save, enabled = name.isNotBlank() && !busy),
        ),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Rounded.Folder, null, modifier = Modifier.size(32.dp))
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true, enabled = !busy,
                label = { Text("文件夹名称") }, modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { save() }),
            )
        }
    }
}

/** Destructive actions keep a second confirmation, using the app's sheet actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickMemoDeleteConfirmationSheet(
    visible: Boolean,
    title: String,
    message: String,
    confirmText: String,
    isLoading: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    val busy by rememberUpdatedState(isLoading)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden || !busy },
    )
    if (visible) AppModalBottomSheet(
        title = title,
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        actions = listOf(
            AppSheetAction("取消", onDismiss, AppSheetActionRole.Secondary, enabled = !busy),
            AppSheetAction(if (busy) "删除中…" else confirmText, onConfirm, AppSheetActionRole.Destructive,
                enabled = confirmEnabled && !busy),
        ),
    ) {
        Text(message, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 12.dp))
        content()
    }
}
