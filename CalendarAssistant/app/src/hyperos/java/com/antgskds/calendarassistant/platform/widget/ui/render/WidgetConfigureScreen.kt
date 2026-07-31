package com.antgskds.calendarassistant.platform.widget.ui.render

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.platform.widget.ui.contract.WidgetConfigureThemeOption
import com.antgskds.calendarassistant.platform.widget.ui.contract.WidgetConfigureUiAction
import com.antgskds.calendarassistant.platform.widget.ui.contract.WidgetConfigureUiState
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.preference.SliderPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun WidgetConfigureScreen(
    state: WidgetConfigureUiState,
    onAction: (WidgetConfigureUiAction) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        topBar = {
            SmallTopAppBar(
                title = state.widgetName,
                navigationIcon = {
                    IconButton(onClick = { onAction(WidgetConfigureUiAction.Exit) }) {
                        Icon(MiuixIcons.Normal.Back, "退出")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "这些设置只作用于当前桌面小组件",
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = MiuixTheme.textStyles.body2.fontSize,
            )
            Text("主题", color = MiuixTheme.colorScheme.primary)
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(vertical = 6.dp),
            ) {
                WidgetConfigureThemeOption.entries.forEach { option ->
                    RadioButtonPreference(
                        title = option.label,
                        selected = state.selectedTheme == option,
                        onClick = { onAction(WidgetConfigureUiAction.SelectTheme(option)) },
                    )
                }
            }
            Text("外观", color = MiuixTheme.colorScheme.primary)
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(vertical = 6.dp),
            ) {
                SliderPreference(
                    value = state.backgroundAlphaPercent.toFloat(),
                    onValueChange = {
                        onAction(WidgetConfigureUiAction.ChangeBackgroundAlpha(it.roundToInt().coerceIn(60, 100)))
                    },
                    title = "背景不透明度",
                    valueText = "${state.backgroundAlphaPercent}%",
                    valueRange = 60f..100f,
                    steps = 39,
                    showKeyPoints = false,
                )
            }
            Button(
                onClick = { onAction(WidgetConfigureUiAction.Save) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text("保存")
            }
        }
    }
}

private val WidgetConfigureThemeOption.label: String
    get() = when (this) {
        WidgetConfigureThemeOption.FOLLOW_APP -> "跟随软件"
        WidgetConfigureThemeOption.LIGHT -> "浅色"
        WidgetConfigureThemeOption.DARK -> "深色"
    }
