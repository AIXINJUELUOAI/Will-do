package com.antgskds.calendarassistant.app.ui.prompt.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.app.ui.prompt.contract.GlobalPromptUiAction
import com.antgskds.calendarassistant.app.ui.prompt.contract.GlobalPromptUiState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
fun GlobalPromptHost(
    state: GlobalPromptUiState,
    floatingBottomPadding: Dp,
    onAction: (GlobalPromptUiAction) -> Unit,
) {
    val prompt = state.prompt ?: return
    WindowDialog(
        show = true,
        modifier = if (prompt.useFloatingBottomPadding) {
            Modifier.padding(bottom = floatingBottomPadding)
        } else {
            Modifier
        },
        title = prompt.title,
        summary = prompt.content,
        onDismissRequest = { onAction(GlobalPromptUiAction.Dismiss(prompt.kind)) },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = { onAction(GlobalPromptUiAction.Dismiss(prompt.kind)) },
                modifier = Modifier.weight(1f),
            ) {
                Text(prompt.dismissText)
            }
            Button(
                onClick = { onAction(GlobalPromptUiAction.Confirm(prompt.kind)) },
                modifier = Modifier.weight(1f),
                colors = if (prompt.isDestructive) {
                    ButtonDefaults.buttonColors(
                        color = MiuixTheme.colorScheme.error,
                        contentColor = MiuixTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColorsPrimary()
                },
            ) {
                Text(prompt.confirmText)
            }
        }
    }
}
