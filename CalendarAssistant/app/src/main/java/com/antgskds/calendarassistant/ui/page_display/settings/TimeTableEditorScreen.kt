package com.antgskds.calendarassistant.ui.page_display.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.data.model.TimeNode
import com.antgskds.calendarassistant.ui.components.ToastType
import com.antgskds.calendarassistant.ui.components.UniversalToast
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
import androidx.compose.material.icons.filled.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeTableEditorScreen(
    viewModel: SettingsViewModel,
    uiSize: Int = 2
) {
    val settings by viewModel.settings.collectAsState()
    val initialJson = settings.timeTableJson
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val jsonParser = Json { ignoreUnknownKeys = true; prettyPrint = true }
    val snackbarHostState = remember { SnackbarHostState() }
    var currentToastType by remember { mutableStateOf(ToastType.SUCCESS) }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    fun showToast(message: String, type: ToastType = ToastType.SUCCESS) {
        currentToastType = type
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    val fabSize = when (uiSize) { 1 -> 56.dp; 2 -> 64.dp; else -> 72.dp }
    val fabIconSize = when (uiSize) { 1 -> 24.dp; 2 -> 28.dp; else -> 32.dp }

    // --- 字体样式优化 ---
    // 板块标题：Primary + Bold
    val sectionHeaderStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    // 课间休息等辅助信息：可点击元素，颜色加深
    val contentBodyStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurface
    )
    // 卡片内的时间段：OnSurface + Medium (稍微强调一点)
    val cardTimeStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
    )
    // 卡片内的时长：Grey + Normal (不抢眼)
    val cardDurationStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    var totalNodes by remember { mutableIntStateOf(12) }
    var courseDuration by remember { mutableIntStateOf(45) }
    var morningStart by remember { mutableStateOf(LocalTime.of(8, 0)) }
    var afternoonStart by remember { mutableStateOf(LocalTime.of(14, 0)) }
    var nightStart by remember { mutableStateOf(LocalTime.of(19, 0)) }
    val customBreaks = remember { mutableStateMapOf<Int, Int>() }
    var showTotalNodesPicker by remember { mutableStateOf(false) }
    var showBreakPickerForNode by remember { mutableStateOf<Int?>(null) }
    var showTimePickerForAnchor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (initialJson.isNotBlank()) {
            try {
                val nodes = jsonParser.decodeFromString<List<TimeNode>>(initialJson)
                if (nodes.isNotEmpty()) {
                    totalNodes = nodes.size
                    morningStart = try { LocalTime.parse(nodes[0].startTime) } catch(e:Exception){ LocalTime.of(8,0) }
                    if(nodes.size >= 5) afternoonStart = try { LocalTime.parse(nodes[4].startTime) } catch(e:Exception){ LocalTime.of(14,0) }
                    if(nodes.size >= 9) nightStart = try { LocalTime.parse(nodes[8].startTime) } catch(e:Exception){ LocalTime.of(19,0) }

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

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = 16.dp,
                end = 16.dp,
                bottom = 160.dp + bottomInset
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            itemsIndexed(generatedNodes) { index, node ->
                val nodeIndex = node.index
                if (nodeIndex == 1) SectionHeader("上午课程", morningStart, sectionHeaderStyle, contentBodyStyle) { showTimePickerForAnchor = "morning" }
                else if (nodeIndex == 5) SectionHeader("下午课程", afternoonStart, sectionHeaderStyle, contentBodyStyle) { showTimePickerForAnchor = "afternoon" }
                else if (nodeIndex == 9) SectionHeader("晚上课程", nightStart, sectionHeaderStyle, contentBodyStyle) { showTimePickerForAnchor = "night" }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Badge(containerColor = MaterialTheme.colorScheme.primary) { Text(nodeIndex.toString(), modifier = Modifier.padding(4.dp)) }
                            Spacer(Modifier.width(16.dp))
                            // 左侧时间段：使用稍微强调的黑色
                            Text("${node.startTime} - ${node.endTime}", style = cardTimeStyle)
                        }
                        // 右侧时长：使用灰色
                        Text("${courseDuration}分", style = cardDurationStyle)
                    }
                }

                if (nodeIndex < totalNodes) {
                    if (nodeIndex == 4 || nodeIndex == 8) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                            HorizontalDivider(modifier = Modifier.alpha(0.3f))
                            Text(if(nodeIndex==4)"午休" else "晚饭", style = contentBodyStyle, modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(horizontal = 8.dp))
                        }
                    } else {
                        val breakTime = customBreaks[nodeIndex] ?: 10
                        Box(Modifier.fillMaxWidth().height(36.dp).clickable { showBreakPickerForNode = nodeIndex }, contentAlignment = Alignment.Center) {
                            HorizontalDivider(modifier = Modifier.fillMaxWidth(0.7f).alpha(0.2f), color = MaterialTheme.colorScheme.onSurface)
                            Text(text = "休息 ${breakTime} 分钟", style = contentBodyStyle, color = MaterialTheme.colorScheme.secondary, modifier = Modifier.background(MaterialTheme.colorScheme.background).padding(horizontal = 8.dp))
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 32.dp)
                .navigationBarsPadding(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingActionButton(
                onClick = { showTotalNodesPicker = true },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(fabSize)
            ) {
                Text(
                    text = "$totalNodes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            FloatingActionButton(
                onClick = {
                    val jsonStr = jsonParser.encodeToString(generatedNodes)
                    viewModel.updateTimeTable(jsonStr)
                    showToast("作息时间已保存", ToastType.SUCCESS)
                },
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(fabSize)
            ) {
                Icon(Icons.Default.Check, contentDescription = "保存", modifier = Modifier.size(fabIconSize))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 32.dp),
            snackbar = { data -> UniversalToast(message = data.visuals.message, type = currentToastType) }
        )
    }

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
private fun SectionHeader(title: String, time: LocalTime, sectionHeaderStyle: TextStyle, contentBodyStyle: TextStyle, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = sectionHeaderStyle
        )
        Text(
            text = "开始: $time",
            style = contentBodyStyle
        )
    }
}