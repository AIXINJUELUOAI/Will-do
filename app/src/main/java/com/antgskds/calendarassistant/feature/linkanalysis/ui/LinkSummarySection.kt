package com.antgskds.calendarassistant.feature.linkanalysis.ui
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.linkanalysis.data.*

@Composable fun LinkSummarySection(record: LinkAnalysisEntity?, onAnalyze: ()->Unit, onCancel: ()->Unit) {
    val summary = LinkAnalysisRepository.summary(record)
    val active = record?.state in setOf("QUEUED","EXTRACTING","TRANSCRIBING","PREPARING","SUMMARIZING")
    Column(Modifier.fillMaxWidth().padding(top=20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
            Text("摘要",style=MaterialTheme.typography.titleMedium)
            if(active) TextButton(onClick=onCancel) { Text("停止") }
            else TextButton(onClick=onAnalyze) { Text(if(summary==null) "生成摘要" else "重新生成") }
        }
        val stateText = when(record?.state) {
            "QUEUED" -> "等待处理…"
            "EXTRACTING" -> "正在提取内容…"
            "TRANSCRIBING" -> "正在处理素材并本地转写…"
            "PREPARING" -> "正在处理素材…"
            "SUMMARIZING" -> "正在生成摘要…"
            "FAILED" -> record.error.ifBlank { "处理失败，可重试" }
            "CANCELLED" -> "已停止"
            else -> ""
        }
        if(stateText.isNotBlank()) Text(stateText,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(summary!=null) {
            com.antgskds.calendarassistant.feature.note.ui.render.material.component.MarkdownText(
                markdown=summary.summary,textColor=MaterialTheme.colorScheme.onSurface,textSizeSp=16f,
            )
            if(summary.warnings.isNotEmpty()) Text(summary.warnings.joinToString("\n"),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
