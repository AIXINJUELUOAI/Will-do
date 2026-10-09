package com.antgskds.calendarassistant.feature.linkanalysis.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.linkanalysis.data.*
import com.antgskds.calendarassistant.feature.linkanalysis.domain.LinkSummaryPresentationPolicy

@Composable
fun LinkSummarySection(record: LinkAnalysisEntity?, textColor: Color = MaterialTheme.colorScheme.onSurface) {
    val summary = LinkAnalysisRepository.summary(record)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("摘要", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        val stateText = when (record?.state) {
            "QUEUED" -> "等待处理…"
            "EXTRACTING" -> "正在提取内容…"
            "TRANSCRIBING" -> "正在处理素材并本地转写…"
            "PREPARING" -> "正在处理素材…"
            "SUMMARIZING" -> "正在生成摘要…"
            "FAILED" -> record.error.ifBlank { "处理失败，可在更多菜单中重试" }
            "CANCELLED" -> "已停止"
            else -> ""
        }
        if (stateText.isNotBlank()) Text(stateText, style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.65f))
        if (summary != null) {
            val content = LinkSummaryPresentationPolicy.present(summary)
            if (content.markdown.isNotBlank()) com.antgskds.calendarassistant.feature.note.ui.render.material.component.MarkdownText(
                markdown = content.markdown, textColor = textColor, textSizeSp = 16f,
            )
            if (content.warnings.isNotEmpty()) Text(content.warnings.joinToString("\n"), style = MaterialTheme.typography.bodySmall, color = textColor.copy(alpha = 0.65f))
        }
    }
}
