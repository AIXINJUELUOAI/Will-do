package com.antgskds.calendarassistant.shared.ui.edition

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

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
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        elevation = materialElevation ?: CardDefaults.cardElevation(defaultElevation = shadowElevation),
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
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        modifier = modifier,
        dismissButton = dismissButton,
        icon = icon,
        title = title,
        text = text,
        shape = shape,
        containerColor = containerColor,
        tonalElevation = tonalElevation,
        titleContentColor = titleContentColor,
        textContentColor = textContentColor,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditionModalBottomSheet(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier,
    sheetState: SheetState?,
    subtitle: String? = null,
    actions: List<com.antgskds.calendarassistant.shared.ui.material.component.AppSheetAction> = emptyList(),
    content: @Composable ColumnScope.() -> Unit,
) {
    com.antgskds.calendarassistant.shared.ui.material.component.AppModalBottomSheet(
        title = title,
        subtitle = subtitle,
        actions = actions,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        content = content,
    )
}
