package com.antgskds.calendarassistant.shared.ui.material.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp

@Composable
fun settingsSliderLabelTextStyle(): TextStyle = MaterialTheme.typography.bodyMedium.copy(
    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    letterSpacing = 0.sp,
)
