package com.antgskds.calendarassistant.shared.ui.material.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog

/** 由实际右侧宿主提供；手机及子选择器不自动切换成工作区。 */
val LocalDetailWorkspace = compositionLocalOf { false }
val LocalDetailReadOnly = compositionLocalOf { false }
val LocalDetailDirtyChanged = compositionLocalOf<(Boolean) -> Unit> { {} }
val LocalDetailWorkspaceContent = compositionLocalOf<(@Composable () -> Unit)?> { null }

@Composable
fun rememberWorkspaceCloseRequest(dirty: Boolean, onClose: () -> Unit): () -> Unit {
    val embedded = LocalDetailWorkspace.current
    val notifyDirty = LocalDetailDirtyChanged.current
    var confirm by remember { mutableStateOf(false) }
    SideEffect { if (embedded) notifyDirty(dirty) }
    DisposableEffect(embedded) { onDispose { if (embedded) notifyDirty(false) } }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false },
        title = { Text("放弃修改？") }, text = { Text("尚未保存的内容将被丢弃。") },
        confirmButton = { TextButton(onClick = { confirm = false; onClose() }) { Text("放弃") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("继续编辑") } })
    return { if (embedded && dirty) confirm = true else onClose() }
}

@Composable
fun DetailWorkspaceOverlay(content: @Composable () -> Unit) {
    val detail = LocalDetailWorkspaceContent.current
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (detail != null) Modifier.alpha(0f).clearAndSetSemantics {} else Modifier)) {
            content()
        }
        if (detail != null) CompositionLocalProvider(LocalDetailWorkspace provides true) { detail() }
    }
}

@Composable
fun AppDetailWorkspace(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    closeEnabled: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    attachment: (@Composable () -> Unit)? = null,
    scrollState: ScrollState? = rememberScrollState(),
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler { if (closeEnabled) onClose() }
    Surface(modifier.fillMaxSize().imePadding(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().padding(bottom = LocalAppPageBottomPadding.current)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, enabled = closeEnabled) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
                }
                Column(Modifier.weight(1f).padding(horizontal = 8.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                actions()
            }
            HorizontalDivider()
            DetailWorkspaceBody(attachment, scrollState, footer, Modifier.weight(1f), content)
        }
    }
}

@Composable
fun DetailWorkspaceBody(
    attachment: (@Composable () -> Unit)? = null,
    scrollState: ScrollState? = rememberScrollState(),
    footer: @Composable ColumnScope.() -> Unit = {},
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val split = attachment != null && maxWidth >= ConfigCatalog.DETAIL_WORKSPACE_SPLIT_MIN_WIDTH_DP.dp
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.Center) {
            Column(Modifier.then(if (split) Modifier.weight(1f) else Modifier.widthIn(max = ConfigCatalog.DETAIL_WORKSPACE_FORM_MAX_WIDTH_DP.dp))
                .fillMaxWidth().fillMaxHeight().testTag("detail_form")) {
                Column(Modifier.weight(1f).fillMaxWidth()
                    .then(if (scrollState != null) Modifier.verticalScroll(scrollState) else Modifier)
                    .padding(24.dp)) {
                    content()
                    if (!split && attachment != null) {
                        Spacer(Modifier.height(24.dp))
                        attachment()
                    }
                }
                footer()
            }
            if (split) {
                VerticalDivider()
                Column(Modifier.weight(1f).fillMaxHeight().testTag("detail_attachment")
                    .verticalScroll(rememberScrollState()).padding(24.dp)) { attachment?.invoke() }
            }
        }
    }
}

/** 仅详情/编辑入口使用，提醒、导入、日期等子弹层继续保留原交互。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppEditorSheet(
    title: String,
    onDismissRequest: () -> Unit,
    subtitle: String? = null,
    actions: List<AppSheetAction> = emptyList(),
    sheetState: SheetState? = null,
    contentBottomPadding: Dp = 16.dp,
    closeEnabled: Boolean = true,
    attachment: (@Composable () -> Unit)? = null,
    footer: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    if (LocalDetailWorkspace.current) {
        AppDetailWorkspace(title, onDismissRequest, subtitle = subtitle, closeEnabled = closeEnabled,
            actions = { actions.forEach { action ->
                if (action.role == AppSheetActionRole.Primary) Button(action.onClick, enabled = action.enabled) { Text(action.text) }
                else TextButton(action.onClick, enabled = action.enabled) { Text(action.text) }
            } }, attachment = attachment, footer = footer, content = content)
    } else {
        AppModalBottomSheet(title, onDismissRequest, subtitle = subtitle, actions = actions, sheetState = sheetState,
            contentBottomPadding = contentBottomPadding, footer = footer) {
            content()
            attachment?.invoke()
        }
    }
}
