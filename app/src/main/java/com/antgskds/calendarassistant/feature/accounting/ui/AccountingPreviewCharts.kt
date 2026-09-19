package com.antgskds.calendarassistant.feature.accounting.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import kotlin.math.roundToInt

/** 小卡和分析面板使用相同折线规则；未来日期为 null，不补零或外推。 */
@Composable
internal fun PreviewLineChart(
    series: List<Pair<LocalDate, Long?>>,
    modifier: Modifier = Modifier,
    selectedDate: LocalDate? = null,
    onSelect: ((LocalDate) -> Unit)? = null,
    detailed: Boolean = false,
) {
    val color = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
    val max = (series.mapNotNull { it.second }.maxOrNull() ?: 0L).coerceAtLeast(100L).toFloat()
    if (series.none { (it.second ?: 0L) > 0L }) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text("暂无支出", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Canvas(modifier.semantics { contentDescription = "支出折线图" }.then(
        if (onSelect == null) Modifier else Modifier.pointerInput(series, onSelect) {
            detectTapGestures { position ->
                val inset = 8.dp.toPx()
                val fraction = ((position.x - inset) / (size.width - inset * 2).coerceAtLeast(1f)).coerceIn(0f, 1f)
                val index = (fraction * (series.size - 1)).roundToInt()
                if (series[index].second != null) onSelect(series[index].first)
            }
        }
    )) {
        val inset = 8.dp.toPx()
        val width = (size.width - inset * 2).coerceAtLeast(1f)
        val height = (size.height - inset * 2).coerceAtLeast(1f)
        fun point(index: Int, amount: Long) = Offset(
            inset + width * index / (series.size - 1).coerceAtLeast(1),
            inset + height * (1f - amount / max),
        )
        // 小卡用完整周期基线标出绘图区，未来日期仍留空，不拉伸已有数据。
        if (!detailed) {
            drawLine(grid, Offset(inset, inset + height), Offset(inset + width, inset + height), 1.dp.toPx())
        }
        if (detailed) repeat(4) { line ->
            val y = inset + height * line / 3f
            drawLine(grid, Offset(inset, y), Offset(inset + width, y), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())))
        }
        val points = series.mapIndexedNotNull { index, entry -> entry.second?.let { point(index, it) } }
        if (points.isNotEmpty()) {
            val path = Path().apply {
                moveTo(points.first().x, points.first().y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val fill = Path().apply {
                addPath(path)
                lineTo(points.last().x, inset + height)
                lineTo(points.first().x, inset + height)
                close()
            }
            drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.16f), color.copy(alpha = 0f))))
            drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            if (points.size == 1) drawCircle(color, 3.dp.toPx(), points.first())
            val selectedIndex = series.indexOfFirst { it.first == selectedDate }
            if (selectedIndex >= 0) series[selectedIndex].second?.let {
                val selected = point(selectedIndex, it)
                drawLine(grid, Offset(selected.x, inset), Offset(selected.x, inset + height), 1.dp.toPx())
                drawCircle(color, 4.dp.toPx(), selected)
            }
        }
    }
}

@Composable
internal fun PreviewStructureChart(bills: List<PreviewBill>, income: Boolean) {
    // 两侧始终参与测量，Box 取较高内容：切换期间和结束后高度一致。
    // 不写死图例高度，系统大字体、换行和空态同样参与测量。
    Box(Modifier.fillMaxWidth()) {
        listOf(false, true).forEach { side ->
            val opacity by animateFloatAsState(if (income == side) 1f else 0f,
                animationSpec = tween(), label = "structure_opacity")
            Column(Modifier.fillMaxWidth().graphicsLayer { alpha = opacity }
                .then(if (income == side) Modifier else Modifier.clearAndSetSemantics {})) {
                PreviewStructureContent(bills, side)
            }
        }
    }
}

@Composable
private fun PreviewStructureContent(bills: List<PreviewBill>, income: Boolean) {
    val categories = bills.filter { it.income == income && it.counted }.groupBy { it.category }
        .mapValues { (_, values) -> values.sumOf { it.cents } }.toList().sortedByDescending { it.second }
    val total = categories.sumOf { it.second }
    val colors = listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.outline)
    if (total == 0L) {
        Box(Modifier.fillMaxWidth().heightIn(min = 224.dp), contentAlignment = Alignment.Center) {
            Text("暂无${if (income) "入账" else "支出"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(184.dp).semantics { contentDescription = "${if (income) "入账" else "支出"}分类占比" }) {
            val stroke = 25.dp.toPx()
            var start = -90f
            categories.forEachIndexed { index, (_, value) ->
                val sweep = value.toFloat() / total * 360f
                drawArc(colors[index % colors.size], start, (sweep - 2f).coerceAtLeast(0.1f), false,
                    topLeft = Offset(stroke / 2, stroke / 2),
                    size = Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (income) "入账合计" else "支出合计", style = MaterialTheme.typography.labelMedium)
            Text(AccountingPreviewData.money(total), style = MaterialTheme.typography.titleMedium)
        }
    }
    categories.forEachIndexed { index, (name, value) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(8.dp)) { drawCircle(colors[index % colors.size]) }
            Spacer(Modifier.width(10.dp))
            Text(name, modifier = Modifier.weight(1f))
            Text("${(value * 100.0 / total).roundToInt()}%", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
            Text(AccountingPreviewData.money(value))
        }
    }
}
