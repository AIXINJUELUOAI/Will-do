package com.antgskds.calendarassistant.shared.ui.edition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SheetState
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.window.WindowBottomSheet
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun EditionCardSurface(
    modifier: Modifier,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    shadowElevation: Dp,
    materialElevation: CardElevation?,
    useEditionDefaultColors: Boolean,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        cornerRadius = 16.dp,
        insideMargin = androidx.compose.foundation.layout.PaddingValues(0.dp),
        colors = if (useEditionDefaultColors) {
            CardDefaults.defaultColors()
        } else {
            CardDefaults.defaultColors(
                color = containerColor,
                contentColor = contentColor,
            )
        },
        content = content,
    )
}

@Composable
fun EditionAlertDialogSurface(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier,
    dismissButton: (@Composable () -> Unit)?,
    icon: (@Composable () -> Unit)?,
    title: (@Composable () -> Unit)?,
    text: (@Composable () -> Unit)?,
    shape: Shape,
    containerColor: Color,
    tonalElevation: Dp,
    titleContentColor: Color,
    textContentColor: Color,
) {
    WindowDialog(
        show = true,
        modifier = modifier,
        onDismissRequest = onDismissRequest,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            icon?.let { content ->
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    content()
                }
            }
            title?.invoke()
            text?.invoke()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                dismissButton?.invoke()
                confirmButton()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditionModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    sheetState: SheetState?,
    content: @Composable ColumnScope.() -> Unit,
) {
    WindowBottomSheet(
        show = true,
        onDismissRequest = onDismissRequest,
        enableNestedScroll = true,
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            content = content,
        )
    }
}
