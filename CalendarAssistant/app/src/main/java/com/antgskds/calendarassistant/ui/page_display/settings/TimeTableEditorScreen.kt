package com.antgskds.calendarassistant.ui.page_display.settings

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.data.model.TimeNode
import com.antgskds.calendarassistant.ui.components.WheelPicker
import com.antgskds.calendarassistant.ui.components.WheelTimePickerDialog
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedReader
import java.io.InputStreamReader
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeTableEditorScreen(
    viewModel: SettingsViewModel,
) {
    val settings by viewModel.settings.collectAsState()
    val initialJson = settings.timeTableJson
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val jsonParser = Json { ignoreUnknownKeys = true; prettyPrint = true }

    // --- 状态管理 ---
    var totalNodes by remember { mutableIntStateOf(12) }
    var courseDuration by remember { mutableIntStateOf(45) }
    var morningStart by remember { mutableStateOf(LocalTime.of(8, 0)) }
    var afternoonStart by remember { mutableStateOf(LocalTime.of(14, 0)) }
    var nightStart by remember { mutableStateOf(LocalTime.of(19, 0)) }
    val customBreaks = remember { mutableStateMapOf<Int, Int>() }

    var showTotalNodesPicker by remember { mutableStateOf(false) }
    var showBreakPickerForNode by remember { mutableStateOf<Int?>(null) }
    var showTimePickerForAnchor by remember { mutableStateOf<String?>(null) }

    // --- 初始化 ---
    LaunchedEffect(Unit) {
        if (initialJson.isNotBlank()) {
            try {
                val nodes = jsonParser.decodeFromString<List<TimeNode>>(initialJson)
                if (nodes.isNotEmpty()) {
                    totalNodes = nodes.size
                    morningStart = try { LocalTime.parse(nodes[0].startTime) } catch(e:Exception){ LocalTime.of(8,0) }
                    if(nodes.size >= 5) afternoonStart = try { LocalTime.parse(nodes[4].startTime) } catch(e:Exception){ LocalTime.of(14,0) }
                    if(nodes.size >= 9) nightStart = try { LocalTime.parse(nodes[8].startTime) } catch(e:Exception){ LocalTime.of(19,0) }

                    val firstStart = LocalTime.parse(nodes[0].startTime)
                    val firstEnd = LocalTime.parse(nodes[0].endTime)
                    courseDuration = Duration.between(firstStart, firstEnd).toMinutes().toInt()

                    for (i in 0 until nodes.size - 1) {
                        val currentEnd = LocalTime.parse(nodes[i].endTime)
                        val nextStart = LocalTime.parse(nodes[i + 1].startTime)
                        if (i + 1 == 4 || i + 1 == 8) continue
                        val diff = Duration.between(currentEnd, nextStart).toMinutes().toInt()
                        if (diff != 10) customBreaks[i + 1] = diff
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    // --- 核心计算引擎 ---
    val generatedNodes = remember(totalNodes, courseDuration, morningStart, afternoonStart, nightStart, customBreaks.toMap()) {
        val list = mutableListOf<TimeNode>()
        val fmt = DateTimeFormatter.ofPattern("HH:mm")
        for (i in 1..totalNodes) {
            val startTime: LocalTime = when (i) {
                1 -> morningStart
                5 -> afternoonStart
                9 -> nightStart
                else -> {
                    val prevNode = list.last()
                    val prevEnd = LocalTime.parse(prevNode.endTime)
                    val breakMinutes = customBreaks[i - 1] ?: 10
                    prevEnd.plusMinutes(breakMinutes.toLong())
                }
            }
            val endTime = startTime.plusMinutes(courseDuration.toLong())
            list.add(TimeNode(i, startTime.format(fmt), endTime.format(fmt)))
        }
        list
    }

    // --- 导入功能 ---
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(it)?.use { input ->
                        val json = BufferedReader(InputStreamReader(input)).readText()
                        withContext(Dispatchers.Main) {
                            try {
                                val nodes = jsonParser.decodeFromString<List<TimeNode>>(json)
                                if (nodes.isNotEmpty()) {
                                    totalNodes = nodes.size
                                    morningStart = LocalTime.parse(nodes[0].startTime)
                                    if(nodes.size >=5) afternoonStart = LocalTime.parse(nodes[4].startTime)
                                    if(nodes.size >=9) nightStart = LocalTime.parse(nodes[8].startTime)
                                    customBreaks.clear()
                                    // 导入即保存
                                    viewModel.updateTimeTable(json)
                                    Toast.makeText(context, "导入成功并保存", Toast.LENGTH_SHORT).show()
                                }
                            } catch(e:Exception) {
                                Toast.makeText(context, "文件格式错误", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { Toast.makeText(context, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show() }
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        // 工具栏
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showTotalNodesPicker = true }) { Text("总节数: $totalNodes") }
                // 暂时隐藏导入按钮（待实现导出功能后再显示）
                // OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                //     Icon(Icons.Default.UploadFile, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp))
                //     Text("导入")
                // }
            }
            Button(onClick = {
                val jsonStr = jsonParser.encodeToString(generatedNodes)
                viewModel.updateTimeTable(jsonStr)
                Toast.makeText(context, "作息时间已保存", Toast.LENGTH_SHORT).show()
            }) { Text("保存生效") }
        }

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(generatedNodes) { index, node ->
                val nodeIndex = node.index
                if (nodeIndex == 1) SectionHeader("上午课程", morningStart) { showTimePickerForAnchor = "morning" }
                else if (nodeIndex == 5) SectionHeader("下午课程", afternoonStart) { showTimePickerForAnchor = "afternoon" }
                else if (nodeIndex == 9) SectionHeader("晚上课程", nightStart) { showTimePickerForAnchor = "night" }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Badge(containerColor = MaterialTheme.colorScheme.primary) { Text(nodeIndex.toString(), modifier = Modifier.padding(4.dp)) }
                            Spacer(Modifier.width(16.dp))
                            Text("${node.startTime} - ${node.endTime}", style = MaterialTheme.typography.titleMedium)
                        }
                        Text("${courseDuration}分", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }

                // 课间休息 (Modified Section)
                if (nodeIndex < totalNodes) {
                    if (nodeIndex == 4 || nodeIndex == 8) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            HorizontalDivider(modifier = Modifier.alpha(0.3f))
                            Text(
                                if(nodeIndex==4)"午休" else "晚饭",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.background)
                                    .padding(horizontal = 8.dp)
                            )
                        }
                    } else {
                        val breakTime = customBreaks[nodeIndex] ?: 10
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(36.dp) // 增加高度方便点击
                                .clickable { showBreakPickerForNode = nodeIndex },
                            contentAlignment = Alignment.Center
                        ) {
                            // 背景横线
                            HorizontalDivider(
                                modifier = Modifier
                                    .fillMaxWidth(0.7f) // 控制横线宽度，0.7看起来比较平衡
                                    .alpha(0.2f),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            // 文字 (带背景遮挡)
                            Text(
                                text = "休息 ${breakTime} 分钟",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.background) // 使用背景色遮挡横线
                                    .padding(horizontal = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // 弹窗逻辑
    if (showTotalNodesPicker) {
        val options = (4..16).toList()
        AlertDialog(
            onDismissRequest = { showTotalNodesPicker = false },
            title = { Text("总节数") },
            text = { WheelPicker(items = options.map { "$it 节" }, initialIndex = options.indexOf(totalNodes).coerceAtLeast(0), onSelectionChanged = { totalNodes = options[it] }) },
            confirmButton = { TextButton(onClick = { showTotalNodesPicker = false }) { Text("确定") } }
        )
    }

    if (showBreakPickerForNode != null) {
        val nodeIdx = showBreakPickerForNode!!
        val options = listOf(5, 10, 15, 20, 25, 30, 40)
        AlertDialog(
            onDismissRequest = { showBreakPickerForNode = null },
            title = { Text("课间时长") },
            text = { WheelPicker(items = options.map { "$it 分钟" }, initialIndex = options.indexOf(customBreaks[nodeIdx]?:10).coerceAtLeast(0), onSelectionChanged = { customBreaks[nodeIdx] = options[it] }) },
            confirmButton = { TextButton(onClick = { showBreakPickerForNode = null }) { Text("确定") } }
        )
    }

    if (showTimePickerForAnchor != null) {
        val initial = when(showTimePickerForAnchor) { "morning" -> morningStart; "afternoon" -> afternoonStart; else -> nightStart }
        WheelTimePickerDialog(
            initialTime = initial.toString(),
            onDismiss = { showTimePickerForAnchor = null },
            onConfirm = {
                try {
                    val t = LocalTime.parse(it)
                    when(showTimePickerForAnchor) { "morning" -> morningStart=t; "afternoon" -> afternoonStart=t; "night" -> nightStart=t }
                } catch(_:Exception){}
                showTimePickerForAnchor = null
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String, time: LocalTime, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "开始: $time",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}