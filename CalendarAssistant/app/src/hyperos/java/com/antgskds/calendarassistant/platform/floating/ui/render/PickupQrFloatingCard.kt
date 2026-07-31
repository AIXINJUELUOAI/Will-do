package com.antgskds.calendarassistant.platform.floating.ui.render

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.recognition.ingest.instantcode.InstantCodeQrSupport
import com.antgskds.calendarassistant.platform.floating.ui.contract.PickupQrFloatingCardUiAction
import com.antgskds.calendarassistant.platform.floating.ui.contract.PickupQrFloatingCardUiState
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PickupQrFloatingCardContent(
    state: PickupQrFloatingCardUiState,
    onAction: (PickupQrFloatingCardUiAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val qrImage = remember(state.qrPayload) {
        InstantCodeQrSupport.createQrBitmap(state.qrPayload)?.asImageBitmap()
    }
    val details = listOf(state.code, state.location).filter(String::isNotBlank).joinToString(" · ")
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.18f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
            ) { onAction(PickupQrFloatingCardUiAction.Dismiss) }
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth(),
            insideMargin = PaddingValues(18.dp),
            cornerRadius = 18.dp,
            onClick = {},
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            state.typeLabel,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            fontSize = MiuixTheme.textStyles.footnote1.fontSize,
                        )
                        Text(
                            state.title,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onAction(PickupQrFloatingCardUiAction.Dismiss) }) {
                        Icon(MiuixIcons.Normal.Close, "关闭")
                    }
                }
                if (details.isNotBlank()) {
                    Text(
                        details,
                        modifier = Modifier.fillMaxWidth(),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        textAlign = TextAlign.Center,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(236.dp)
                        .background(Color.White)
                        .padding(10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qrImage != null) {
                        Image(qrImage, "取件二维码", Modifier.fillMaxSize())
                    } else {
                        Text("二维码不可用", color = Color.Gray)
                    }
                }
                Button(
                    onClick = { onAction(PickupQrFloatingCardUiAction.Complete) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) {
                    Text(state.completeLabel)
                }
            }
        }
    }
}
