package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.data.model.RemoteAppUpdateSection
import com.antgskds.calendarassistant.data.model.RemoteAppVersion
import com.antgskds.calendarassistant.ui.viewmodel.MainViewModel

@Composable
fun AppUpdatePage(
    mainViewModel: MainViewModel,
    uiSize: Int = 2
) {
    val updateState by mainViewModel.appUpdateUiState.collectAsState()
    val uriHandler = LocalUriHandler.current
    val versionTitleStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
    val bodyStyle = MaterialTheme.typography.bodyLarge

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        val versions = updateState.info?.versions.orEmpty()
        if (versions.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            ) {
                Text(
                    text = updateState.errorMessage ?: "暂无更新日志",
                    style = bodyStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(if (uiSize >= 3) 20.dp else 16.dp)
                )
            }
        } else {
            versions.forEachIndexed { index, version ->
                AppVersionCard(
                    version = version,
                    isLatest = index == 0,
                    defaultExpanded = index == 0,
                    titleStyle = versionTitleStyle,
                    sectionTitleStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    bodyStyle = bodyStyle,
                    onDownload = { url -> uriHandler.openUri(url) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun AppVersionCard(
    version: RemoteAppVersion,
    isLatest: Boolean,
    defaultExpanded: Boolean,
    titleStyle: androidx.compose.ui.text.TextStyle,
    sectionTitleStyle: androidx.compose.ui.text.TextStyle,
    bodyStyle: androidx.compose.ui.text.TextStyle,
    onDownload: (String) -> Unit
) {
    var expanded by rememberSaveable(version.versionname) { mutableStateOf(defaultExpanded) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier
                .clickable { expanded = !expanded }
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(version.versionname.ifBlank { "未知版本" }, style = titleStyle)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLatest) {
                        Text(
                            text = "最新",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "收起" else "展开",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (version.downloadPassword.isNotBlank() || version.downloadUrl.isNotBlank()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (version.downloadPassword.isNotBlank()) {
                        Text(
                            text = "提取码：${version.downloadPassword}",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (version.downloadUrl.isNotBlank()) {
                        Button(
                            onClick = { onDownload(version.downloadUrl) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("前往下载")
                        }
                    }
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    version.sections.forEach { section ->
                        AppUpdateSection(section, sectionTitleStyle, bodyStyle)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppUpdateSection(
    section: RemoteAppUpdateSection,
    sectionTitleStyle: androidx.compose.ui.text.TextStyle,
    bodyStyle: androidx.compose.ui.text.TextStyle
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(section.title.ifBlank { "更新内容" }, style = sectionTitleStyle)
        section.items.forEach { item ->
            Text(
                text = "- $item",
                style = bodyStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
