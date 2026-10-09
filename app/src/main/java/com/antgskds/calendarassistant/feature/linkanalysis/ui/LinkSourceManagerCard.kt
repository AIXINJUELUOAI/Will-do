package com.antgskds.calendarassistant.feature.linkanalysis.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.linkanalysis.data.InstalledLinkSource
import com.antgskds.calendarassistant.feature.linkanalysis.domain.LinkAnalysisPolicy
import com.antgskds.calendarassistant.feature.linkanalysis.domain.LinkSourceLoginEntry
import com.antgskds.calendarassistant.shared.ui.material.component.*
import com.antgskds.calendarassistant.shared.ui.material.settings.ActionSettingItem
import com.antgskds.calendarassistant.shared.ui.material.settings.SwitchSettingItem
import com.antgskds.calendarassistant.shared.ui.edition.EditionButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkSourceManagerCard(
    sources: List<InstalledLinkSource>, enabled: Boolean, localAudio: Boolean, busy: Boolean,
    onEnable: (Boolean) -> Unit, onAudioMode: (Boolean) -> Unit, onImport: (Uri) -> Unit,
    onDelete: (String) -> Unit, transcriptToBody: Boolean, onTranscriptToBody: (Boolean) -> Unit,
    predictiveBackEnabled: Boolean = true,
) {
    val source = sources.lastOrNull()
    val loginEntries = source?.manifest?.let(LinkAnalysisPolicy::loginEntries).orEmpty()
    var managing by remember { mutableStateOf(false) }
    var choosingLogin by remember(source?.digest) { mutableStateOf(false) }
    var loginTarget by remember(source?.digest) { mutableStateOf<LinkSourceLoginEntry?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(onImport) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val haptics = rememberAppHaptics()
    val titleStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
    val subtitleStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    fun importSource() = launcher.launch(arrayOf("application/zip", "application/octet-stream"))

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("链接解析", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary)
        AppCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(vertical = 8.dp)) {
                SwitchSettingItem(
                    title = "自动生成摘要", subtitle = "使用导入的解析源处理新收藏",
                    checked = enabled, onCheckedChange = onEnable, enabled = !busy,
                    cardTitleStyle = titleStyle, cardSubtitleStyle = subtitleStyle,
                )
            }
        }
        AppCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(vertical = 8.dp)) {
                SwitchSettingItem(
                    title = "音频处理",
                    subtitle = "音频在本地转写或直接发送给 AI\n当前：" + if (localAudio) "本地转写" else "直接发送给 AI",
                    checked = localAudio, onCheckedChange = onAudioMode, enabled = !busy,
                    cardTitleStyle = titleStyle, cardSubtitleStyle = subtitleStyle,
                )
            }
        }
        AppCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(vertical = 8.dp)) {
                SwitchSettingItem(
                    title = "转写文本写入正文", subtitle = "保留音频转写原文，并按句号分段追加到正文",
                    checked = transcriptToBody, onCheckedChange = onTranscriptToBody, enabled = !busy,
                    cardTitleStyle = titleStyle, cardSubtitleStyle = subtitleStyle,
                )
            }
        }
        AppCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !busy) {
                    haptics.click()
                    managing = true
                }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("导入源", style = titleStyle)
                    Text(
                        source?.let { it.manifest.name + " · " + it.manifest.version }
                            ?: "导入符合协议的 ZIP 解析源", style = subtitleStyle,
                    )
                }
                Box(
                    Modifier.clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = if (busy) 0.56f else 1f))
                        .defaultMinSize(minWidth = 64.dp, minHeight = 40.dp)
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (busy) "处理中" else "管理",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold)
                }
            }
        }
        AppCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(vertical = 8.dp)) {
                ActionSettingItem(
                    title = "平台登录", subtitle = when {
                        source == null -> "导入解析源后使用"
                        loginEntries.isEmpty() -> "当前源未提供登录入口"
                        else -> "登录状态仅保存在本机"
                    },
                    value = "", icon = Icons.Default.ChevronRight, enabled = !busy,
                    onClick = {
                        when {
                            source == null -> Toast.makeText(context, "请先导入解析源", Toast.LENGTH_SHORT).show()
                            loginEntries.isEmpty() -> Toast.makeText(context, "当前源未提供登录入口", Toast.LENGTH_SHORT).show()
                            else -> choosingLogin = true
                        }
                    }, cardTitleStyle = titleStyle,
                    cardSubtitleStyle = subtitleStyle, cardValueStyle = subtitleStyle,
                )
            }
        }
    }
    if (choosingLogin && loginEntries.isNotEmpty()) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val sheetScope = rememberCoroutineScope()
        var openingLogin by remember { mutableStateOf(false) }
        AppModalBottomSheet(
            title = "平台登录",
            subtitle = "选择需要登录的平台",
            sheetState = sheetState,
            onDismissRequest = { choosingLogin = false },
        ) {
            loginEntries.forEach { target ->
                ListItem(
                    headlineContent = { Text(target.name.trim()) },
                    trailingContent = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().clickable(enabled = !busy && !openingLogin) {
                        haptics.click()
                        openingLogin = true
                        sheetScope.launch {
                            try {
                                // 先收起 sheet，再打开登录页，避免两个窗口叠加。
                                sheetState.hide()
                                if (!sheetState.isVisible) {
                                    choosingLogin = false
                                    loginTarget = target
                                }
                            } finally {
                                openingLogin = false
                            }
                        }
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
    loginTarget?.takeIf { it in loginEntries }?.let { target ->
        source?.let { installed ->
            key(installed.digest, target.url) {
                LinkPlatformLoginDialog(manifest = installed.manifest, entry = target,
                    onDismiss = { loginTarget = null })
            }
        }
    }
    PredictiveFloatingActionCard(
        visible = managing, title = "解析源",
        content = source?.let { it.manifest.name + " · " + it.manifest.version } ?: "导入符合协议的 ZIP 解析源",
        confirmText = "", dismissText = "", isLoading = busy,
        predictiveBackEnabled = predictiveBackEnabled,
        onConfirm = {}, onDismiss = { if (!busy) managing = false },
        actionContent = {
            TextButton(enabled = source != null && !busy, onClick = {
                val id = source?.manifest?.id ?: return@TextButton
                managing = false
                scope.launch { delay(PredictiveFloatingActionCardExitMillis); onDelete(id) }
            }) { Text("移除", color = MaterialTheme.colorScheme.error) }
            EditionButton(enabled = !busy, onClick = {
                managing = false
                scope.launch { delay(PredictiveFloatingActionCardExitMillis); importSource() }
            }) { Text(if (source == null) "导入" else "更换") }
        },
    )
}
