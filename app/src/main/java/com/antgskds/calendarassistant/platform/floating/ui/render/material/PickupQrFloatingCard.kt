package com.antgskds.calendarassistant.platform.floating.ui.render.material

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics

@Composable
fun MaterialPickupQrFloatingCard(
    state: PickupQrFloatingCardUiState,
    onAction: (PickupQrFloatingCardUiAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val payload = state.qrPayload
    val qrImage = remember(payload) {
        InstantCodeQrSupport.createQrBitmap(payload)?.asImageBitmap()
    }
    val detailText = listOf(state.code, state.location)
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    val haptics = rememberAppHaptics()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.22f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { haptics.click(); onAction(PickupQrFloatingCardUiAction.Dismiss) }
            )
            .padding(horizontal = 24.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {}
                ),
            shape = RoundedCornerShape(8.dp),
            color = Color.White,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.typeLabel,
                            style = MaterialTheme.typography.labelLarge,
                            color = Color(0xFF607083),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF111827),
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = {
                            haptics.click()
                            onAction(PickupQrFloatingCardUiAction.Dismiss)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "关闭",
                            tint = Color(0xFF607083)
                        )
                    }
                }

                if (detailText.isNotBlank()) {
                    Text(
                        text = detailText,
                        modifier = Modifier.fillMaxWidth(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF374151),
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .size(236.dp)
                        .background(Color.White, RoundedCornerShape(6.dp))
                        .border(1.dp, Color(0xFFE5E7EB), RoundedCornerShape(6.dp))
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrImage != null) {
                        Image(
                            bitmap = qrImage,
                            contentDescription = "取件二维码",
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(
                            text = "二维码不可用",
                            color = Color(0xFF6B7280),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Button(
                    onClick = {
                        haptics.confirm()
                        onAction(PickupQrFloatingCardUiAction.Complete)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF111827),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(state.completeLabel)
                }
            }
        }
    }
}
