package com.antgskds.calendarassistant.feature.home.ui.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

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
    WindowDialog(
        show = visible,
        modifier = modifier,
        title = title,
        summary = content,
        onDismissRequest = onDismiss,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                colors = if (dismissIsDestructive) {
                    ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.error,
                        contentColor = MiuixTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(dismissText)
            }
            Button(
                onClick = onConfirm,
                modifier = Modifier.weight(1f),
                colors = if (isDestructive) {
                    ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.error,
                        contentColor = MiuixTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColorsPrimary()
                },
            ) {
                Text(confirmText)
            }
        }
    }
}
