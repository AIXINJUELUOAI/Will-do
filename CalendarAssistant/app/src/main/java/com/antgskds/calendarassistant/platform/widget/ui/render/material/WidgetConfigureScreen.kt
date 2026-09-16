package com.antgskds.calendarassistant.platform.widget.ui.render.material

import com.antgskds.calendarassistant.shared.ui.material.component.AppPageScaffold
import com.antgskds.calendarassistant.shared.ui.material.component.AppTopBar
import com.antgskds.calendarassistant.shared.ui.material.component.AppTopBarBackButton

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.platform.widget.ui.contract.WidgetConfigureThemeOption
import com.antgskds.calendarassistant.platform.widget.ui.contract.WidgetConfigureUiAction
import com.antgskds.calendarassistant.platform.widget.ui.contract.WidgetConfigureUiState
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaterialWidgetConfigureScreen(
    state: WidgetConfigureUiState,
    onAction: (WidgetConfigureUiAction) -> Unit
) {
    AppPageScaffold(
        scrollState = rememberScrollState(),
        contentMaxWidth = 720.dp,
        modifier = Modifier.fillMaxSize(),
        topBar = {
            AppTopBar(
                title = state.widgetName,
                navigationIcon = {
                    AppTopBarBackButton(onClick = { onAction(WidgetConfigureUiAction.Exit) })
                }
            )
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 720.dp)
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "这些设置只作用于当前桌面小组件。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("主题", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                WidgetConfigureThemeOption.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onAction(WidgetConfigureUiAction.SelectTheme(option)) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = state.selectedTheme == option,
                            onClick = { onAction(WidgetConfigureUiAction.SelectTheme(option)) }
                        )
                        Text(
                            text = when (option) {
                                WidgetConfigureThemeOption.FOLLOW_APP -> "跟随软件"
                                WidgetConfigureThemeOption.LIGHT -> "浅色"
                                WidgetConfigureThemeOption.DARK -> "深色"
                            },
                            modifier = Modifier.padding(start = 8.dp),
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }

                HorizontalDivider()
                Text(
                    text = "背景不透明度 ${state.backgroundAlphaPercent}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Slider(
                    value = state.backgroundAlphaPercent.toFloat(),
                    onValueChange = { value ->
                        onAction(WidgetConfigureUiAction.ChangeBackgroundAlpha(value.roundToInt().coerceIn(60, 100)))
                    },
                    valueRange = 60f..100f,
                    steps = 39,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.padding(top = 4.dp))
                Button(
                    onClick = { onAction(WidgetConfigureUiAction.Save) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存")
                }
            }
        }
    }
}
