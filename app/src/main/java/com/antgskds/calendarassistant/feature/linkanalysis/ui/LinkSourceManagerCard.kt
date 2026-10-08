package com.antgskds.calendarassistant.feature.linkanalysis.ui
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.linkanalysis.data.InstalledLinkSource
import com.antgskds.calendarassistant.shared.ui.material.component.*
import com.antgskds.calendarassistant.shared.ui.edition.EditionButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun LinkSourceManagerCard(
    sources: List<InstalledLinkSource>, enabled: Boolean, localAudio: Boolean, busy: Boolean,
    onEnable: (Boolean)->Unit, onAudioMode: (Boolean)->Unit, onImport: (Uri)->Unit,
    onSourceEnabled: (String,Boolean)->Unit, onDelete: (String)->Unit,
) {
    var managing by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(onImport) }
    AppCard(modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("链接解析（实验）",style=MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("自动生成摘要")
                    Text("使用导入的解析源处理新收藏",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked=enabled,onCheckedChange=onEnable,enabled=!busy)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("音频处理")
                    Text("音频在本地转写或直接发送给 AI",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if(localAudio) "本地转写" else "直接发送给 AI",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                }
                Switch(checked=localAudio,onCheckedChange=onAudioMode,enabled=!busy)
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                EditionButton(onClick={launcher.launch(arrayOf("application/zip","application/octet-stream"))},enabled=!busy) { Text(if(busy) "处理中…" else "导入源") }
                EditionButton(onClick={managing=true},enabled=!busy) { Text("管理源（"+sources.size+"）") }
            }
        }
    }
    if (managing) AppModalBottomSheet(title="解析源",onDismissRequest={managing=false}) {
        if (sources.isEmpty()) Text("尚未导入解析源",color=MaterialTheme.colorScheme.onSurfaceVariant)
        sources.forEach { source ->
            Row(Modifier.fillMaxWidth().padding(vertical=8.dp),horizontalArrangement=Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text(source.manifest.name)
                    Text(source.manifest.version,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked=source.enabled,onCheckedChange={onSourceEnabled(source.manifest.id,it)},enabled=!busy)
                TextButton(onClick={onDelete(source.manifest.id)},enabled=!busy) { Text("移除") }
            }
        }
    }
}
