package com.antgskds.calendarassistant.feature.home.ui.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.antgskds.calendarassistant.shared.ui.material.component.PredictiveFloatingActionCard

@Composable
fun HomeActionDialog(
    visible: Boolean,
    title: String,
    content: String,
    confirmText: String,
    dismissText: String,
    isDestructive: Boolean,
    dismissIsDestructive: Boolean = false,
    predictiveBackEnabled: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PredictiveFloatingActionCard(
        visible = visible,
        title = title,
        content = content,
        confirmText = confirmText,
        dismissText = dismissText,
        isDestructive = isDestructive,
        dismissIsDestructive = dismissIsDestructive,
        isLoading = false,
        predictiveBackEnabled = predictiveBackEnabled,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}
