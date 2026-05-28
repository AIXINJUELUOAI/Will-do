package com.antgskds.calendarassistant.miui.floating

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.data.model.EventPatch
import com.antgskds.calendarassistant.data.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.data.model.WeatherData

@Composable
fun FloatingScheduleScreen(
    scheduleItems: List<ScheduleDisplayItem>,
    noteEvents: List<Event>,
    weatherData: WeatherData?,
    weatherForecastRange: Int = 0,
    noteEnabled: Boolean,
    expandSide: String,
    onClose: () -> Unit,
    onManualInput: (String, Boolean, () -> Unit) -> Unit,
    onPickImageRequest: (() -> Unit) -> Unit,
    onUpdateEvent: (Event, () -> Unit) -> Unit,
    onUpdateScheduleItem: (ScheduleDisplayItem, EventPatch, () -> Unit) -> Unit,
    onArchiveScheduleItem: (ScheduleDisplayItem) -> Unit,
    onStatusAction: (ScheduleDisplayItem) -> Unit,
    pendingStatusKeys: Set<String>,
    undoPendingLabel: String?,
    onUndoAction: () -> Unit,
    onDeleteNote: (Event, () -> Unit) -> Unit,
    onRestoreNote: (Event, () -> Unit) -> Unit,
    onLoadingChange: (Boolean) -> Unit,
    hapticEnabled: Boolean = true
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "MIUI 悬浮窗预留中",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "接口已保留，后续会独立实现。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Button(onClick = onClose) {
                Text("关闭")
            }
        }
    }
}
