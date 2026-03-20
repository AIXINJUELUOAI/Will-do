package com.antgskds.calendarassistant.ui.page_display.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.core.course.TimeTableLayoutConfig
import com.antgskds.calendarassistant.core.course.TimeTableLayoutUtils
import com.antgskds.calendarassistant.data.model.TimeNode
import com.antgskds.calendarassistant.ui.components.ToastType
import com.antgskds.calendarassistant.ui.components.UniversalToast
import com.antgskds.calendarassistant.ui.components.WheelPicker
import com.antgskds.calendarassistant.ui.components.WheelTimePickerDialog
import com.antgskds.calendarassistant.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeTableEditorScreen(
    viewModel: SettingsViewModel,
    uiSize: Int = 2
) {
    val settings by viewModel.settings.collectAsState()
    val initialJson = settings.timeTableJson
    val scope = rememberCoroutineScope()
    val jsonParser = remember { Json { ignoreUnknownKeys = true; prettyPrint = true } }
    val snackbarHostState = remember { SnackbarHostState() }
    var currentToastType by remember { mutableStateOf(ToastType.SUCCESS) }

    fun showToast(message: String, type: ToastType = ToastType.SUCCESS) {
        currentToastType = type
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message)
        }
    }

    val resolvedConfig = remember(settings.timeTableConfigJson, settings.timeTableJson) {
        TimeTableLayoutUtils.resolveLayoutConfig(settings.timeTableConfigJson, settings.timeTableJson)
    }

    // 统一 FAB 尺寸为 72.dp，图标 34.dp
    val fabSize = 72.dp
    val fabIconSize = 34.dp

    val sectionHeaderStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary
    )
    val contentBodyStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurface
    )
    val cardTimeStyle = MaterialTheme.typography.bodyLarge.copy(
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurface
    )
    val cardDurationStyle = MaterialTheme.typography.bodyMedium.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    var morningCount by remember(resolvedConfig) { mutableIntStateOf(resolvedConfig.morningCount) }
    var afternoonCount by remember(resolvedConfig) { mutableIntStateOf(resolvedConfig.afternoonCount) }
    var nightCount by remember(resolvedConfig) { mutableIntStateOf(resolvedConfig.nightCount) }
    var morningStart by remember(resolvedConfig) { mutableStateOf(resolvedConfig.morningStart) }
    var afternoonStart by remember(resolvedConfig) { mutableStateOf(resolvedConfig.afternoonStart) }
    var nightStart by remember(resolvedConfig) { mutableStateOf(resolvedConfig.nightStart) }
    val customBreaks = remember(resolvedConfig) { mutableStateMapOf<Int, Int>().apply { putAll(resolvedConfig.customBreaks) } }
    val customDurations = remember(resolvedConfig) { mutableStateMapOf<Int, Int>().apply { putAll(resolvedConfig.customDurations) } }

    var showLayoutConfigDialog by remember { mutableStateOf(false) }
    var showBreakPickerForNode by remember { mutableStateOf<Int?>(null) }
    var showDurationPickerForNode by remember { mutableStateOf<Int?>(null) }
    var showTimePickerForAnchor by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(settings.timeTableConfigJson, initialJson) {
        if (settings.timeTableConfigJson.isBlank() && initialJson.isNotBlank()) {
            viewModel.updateTimeTableConfig(TimeTableLayoutUtils.encodeLayoutConfig(resolvedConfig))
        }
    }

    val layoutConfig = remember(
        morningCount,
        afternoonCount,
        nightCount,
        morningStart,
        afternoonStart,
        nightStart,
        customBreaks.toMap(),
        customDurations.toMap()
    ) {
        val draftConfig = TimeTableLayoutConfig(
            morningCount = morningCount,
            afternoonCount = afternoonCount,
            nightCount = nightCount,
            morningStart = morningStart,
            afternoonStart = afternoonStart,
            nightStart = nightStart,
            customBreaks = customBreaks.toMap(),
            customDurations = customDurations.toMap()
        )
        draftConfig.copy(
            customBreaks = TimeTableLayoutUtils.sanitizeCustomBreaks(customBreaks.toMap(), draftConfig),
            customDurations = TimeTableLayoutUtils.sanitizeCustomDurations(customDurations.toMap(), draftConfig)
        )
    }

    val generatedNodes = remember(layoutConfig) {
        TimeTableLayoutUtils.generateNodes(layoutConfig)
    }
    val totalNodes = layoutConfig.totalNodes
    val lunchBoundaryNode = layoutConfig.lunchBoundaryNode
    val dinnerBoundaryNode = layoutConfig.dinnerBoundaryNode
    val afternoonStartNode = layoutConfig.afternoonStartNode
    val nightStartNode = layoutConfig.nightStartNode

    LaunchedEffect(generatedNodes, afternoonCount, nightCount, settings.timeTableConfigJson) {
        if (settings.timeTableConfigJson.isNotBlank()) return@LaunchedEffect

        if (afternoonCount > 0 && afternoonStartNode in 1..generatedNodes.size) {
            val actualAfternoonStart = parseTimeOrFallback(
                generatedNodes[afternoonStartNode - 1].startTime,
                afternoonStart
            )
            if (actualAfternoonStart.isAfter(afternoonStart)) {
                afternoonStart = actualAfternoonStart
            }
        }

        if (nightCount > 0 && nightStartNode in 1..generatedNodes.size) {
            val actualNightStart = parseTimeOrFallback(
                generatedNodes[nightStartNode - 1].startTime,
                nightStart
            )
            if (actualNightStart.isAfter(nightStart)) {
                nightStart = actualNightStart
            }
        }
    }

    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

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
            itemsIndexed(generatedNodes) { _, node ->
                val nodeIndex = node.index
                val nodeDuration = TimeTableLayoutUtils.durationForNode(nodeIndex, layoutConfig)
                when {
                    nodeIndex == 1 && morningCount > 0 -> SectionHeader(
                        "上午课程",
                        morningStart,
                        sectionHeaderStyle,
                        contentBodyStyle
                    ) {
                        showTimePickerForAnchor = "morning"
                    }

                    nodeIndex == 1 && morningCount == 0 && afternoonCount > 0 -> SectionHeader(
                        "下午课程",
                        afternoonStart,
                        sectionHeaderStyle,
                        contentBodyStyle
                    ) {
                        showTimePickerForAnchor = "afternoon"
                    }

                    nodeIndex == 1 && morningCount == 0 && afternoonCount == 0 && nightCount > 0 -> SectionHeader(
                        "晚上课程",
                        nightStart,
                        sectionHeaderStyle,
                        contentBodyStyle
                    ) {
                        showTimePickerForAnchor = "night"
                    }

                    afternoonCount > 0 && nodeIndex == afternoonStartNode -> SectionHeader(
                        "下午课程",
                        afternoonStart,
                        sectionHeaderStyle,
                        contentBodyStyle
                    ) {
                        showTimePickerForAnchor = "afternoon"
                    }

                    nightCount > 0 && nodeIndex == nightStartNode -> SectionHeader(
                        "晚上课程",
                        nightStart,
                        sectionHeaderStyle,
                        contentBodyStyle
                    ) {
                        showTimePickerForAnchor = "night"
                    }
                }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDurationPickerForNode = nodeIndex },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                Text(nodeIndex.toString(), modifier = Modifier.padding(4.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Text("${node.startTime} - ${node.endTime}", style = cardTimeStyle)
                        }
                        Text("${nodeDuration}分", style = cardDurationStyle)
                    }
                }

                if (nodeIndex < totalNodes) {
                    when (nodeIndex) {
                        lunchBoundaryNode,
                        dinnerBoundaryNode -> {
                            val label = if (nodeIndex == lunchBoundaryNode) "午休" else "晚饭"
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                HorizontalDivider(modifier = Modifier.alpha(0.3f))
                                Text(
                                    text = label,
                                    style = contentBodyStyle,
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.background)
                                        .padding(horizontal = 8.dp)
                                )
                            }
                        }

                        else -> {
                            val breakTime = layoutConfig.customBreaks[nodeIndex]
                                ?: TimeTableLayoutUtils.DEFAULT_BREAK_MINUTES
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(36.dp)
                                    .clickable { showBreakPickerForNode = nodeIndex },
                                contentAlignment = Alignment.Center
                            ) {
                                HorizontalDivider(
                                    modifier = Modifier
                                        .fillMaxWidth(0.7f)
                                        .alpha(0.2f),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "休息 ${breakTime} 分钟",
                                    style = contentBodyStyle,
                                    color = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier
                                        .background(MaterialTheme.colorScheme.background)
                                        .padding(horizontal = 7.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 32.dp + bottomInset),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FloatingActionButton(
                onClick = { showLayoutConfigDialog = true },
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
                    if (!isChronologicalTimeTable(generatedNodes)) {
                        showToast("作息时间有重叠，请调整各时段开始时间", ToastType.ERROR)
                    } else {
                        val jsonStr = jsonParser.encodeToString(generatedNodes)
                        val configJson = TimeTableLayoutUtils.encodeLayoutConfig(layoutConfig)
                        viewModel.updateTimeTable(jsonStr, configJson)
                        showToast("作息时间已保存", ToastType.SUCCESS)
                    }
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
                .padding(bottom = 32.dp + bottomInset),
            snackbar = { data -> UniversalToast(message = data.visuals.message, type = currentToastType) }
        )
    }

    if (showLayoutConfigDialog) {
        TimeTableStructureDialog(
            initialMorningCount = morningCount,
            initialAfternoonCount = afternoonCount,
            initialNightCount = nightCount,
            onDismiss = { showLayoutConfigDialog = false },
            onConfirm = { newMorningCount, newAfternoonCount, newNightCount ->
                val updatedConfig = TimeTableLayoutConfig(
                    morningCount = newMorningCount,
                    afternoonCount = newAfternoonCount,
                    nightCount = newNightCount,
                    morningStart = morningStart,
                    afternoonStart = afternoonStart,
                    nightStart = nightStart,
                    customBreaks = customBreaks.toMap(),
                    customDurations = customDurations.toMap()
                )
                val sanitizedBreaks = TimeTableLayoutUtils.sanitizeCustomBreaks(
                    customBreaks.toMap(),
                    updatedConfig
                )
                val sanitizedDurations = TimeTableLayoutUtils.sanitizeCustomDurations(
                    customDurations.toMap(),
                    updatedConfig
                )

                morningCount = newMorningCount
                afternoonCount = newAfternoonCount
                nightCount = newNightCount
                customBreaks.clear()
                customBreaks.putAll(sanitizedBreaks)
                customDurations.clear()
                customDurations.putAll(sanitizedDurations)
                showLayoutConfigDialog = false
            }
        )
    }

    if (showBreakPickerForNode != null) {
        val nodeIndex = showBreakPickerForNode!!
        val options = listOf(5, 10, 15, 20, 25, 30, 40)
        val initialBreak = remember(nodeIndex, layoutConfig.customBreaks[nodeIndex]) {
            nearestOption(
                options,
                layoutConfig.customBreaks[nodeIndex] ?: TimeTableLayoutUtils.DEFAULT_BREAK_MINUTES
            )
        }
        var selectedBreak by remember(nodeIndex, initialBreak) { mutableIntStateOf(initialBreak) }
        AlertDialog(
            onDismissRequest = { showBreakPickerForNode = null },
            title = { Text("课间时长") },
            text = {
                WheelPicker(
                    items = options.map { "$it 分钟" },
                    initialIndex = options.indexOf(initialBreak).coerceAtLeast(0),
                    onSelectionChanged = { selectedBreak = options[it] }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedBreak == TimeTableLayoutUtils.DEFAULT_BREAK_MINUTES) {
                        customBreaks.remove(nodeIndex)
                    } else {
                        customBreaks[nodeIndex] = selectedBreak
                    }
                    showBreakPickerForNode = null
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBreakPickerForNode = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showDurationPickerForNode != null) {
        val nodeIndex = showDurationPickerForNode!!
        val currentDuration = TimeTableLayoutUtils.durationForNode(nodeIndex, layoutConfig)
        val options = remember(currentDuration) {
            val baseOptions = (TimeTableLayoutUtils.MIN_DURATION_MINUTES..
                TimeTableLayoutUtils.MAX_DURATION_MINUTES step 5).toMutableList()
            if (currentDuration !in baseOptions) {
                baseOptions += currentDuration.coerceIn(
                    TimeTableLayoutUtils.MIN_DURATION_MINUTES,
                    TimeTableLayoutUtils.MAX_DURATION_MINUTES
                )
                baseOptions.sort()
            }
            baseOptions.toList()
        }
        var selectedDuration by remember(nodeIndex, currentDuration) { mutableIntStateOf(currentDuration) }

        AlertDialog(
            onDismissRequest = { showDurationPickerForNode = null },
            title = { Text("第 $nodeIndex 节时长") },
            text = {
                WheelPicker(
                    items = options.map { "$it 分钟" },
                    initialIndex = options.indexOf(currentDuration).coerceAtLeast(0),
                    onSelectionChanged = { selectedDuration = options[it] }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (selectedDuration == TimeTableLayoutUtils.DEFAULT_COURSE_DURATION_MINUTES) {
                        customDurations.remove(nodeIndex)
                    } else {
                        customDurations[nodeIndex] = selectedDuration
                    }
                    showDurationPickerForNode = null
                }) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDurationPickerForNode = null }) {
                    Text("取消")
                }
            }
        )
    }

    if (showTimePickerForAnchor != null) {
        val initialTime = when (showTimePickerForAnchor) {
            "morning" -> morningStart
            "afternoon" -> afternoonStart
            else -> nightStart
        }
        WheelTimePickerDialog(
            initialTime = initialTime.toString(),
            onDismiss = { showTimePickerForAnchor = null },
            onConfirm = {
                try {
                    val selectedTime = LocalTime.parse(it)
                    when (showTimePickerForAnchor) {
                        "morning" -> morningStart = selectedTime
                        "afternoon" -> afternoonStart = selectedTime
                        "night" -> nightStart = selectedTime
                    }
                } catch (_: Exception) {
                }
                showTimePickerForAnchor = null
            }
        )
    }
}

@Composable
private fun TimeTableStructureDialog(
    initialMorningCount: Int,
    initialAfternoonCount: Int,
    initialNightCount: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int) -> Unit
) {
    val daySectionOptions = remember {
        (TimeTableLayoutUtils.MIN_SECTION_COUNT..TimeTableLayoutUtils.MAX_SECTION_COUNT).toList()
    }
    val nightSectionOptions = remember {
        (TimeTableLayoutUtils.MIN_NIGHT_SECTION_COUNT..TimeTableLayoutUtils.MAX_NIGHT_SECTION_COUNT).toList()
    }
    val requiresLegacyConversion =
        initialMorningCount !in TimeTableLayoutUtils.MIN_SECTION_COUNT..TimeTableLayoutUtils.MAX_SECTION_COUNT ||
            initialAfternoonCount !in TimeTableLayoutUtils.MIN_SECTION_COUNT..TimeTableLayoutUtils.MAX_SECTION_COUNT ||
            initialNightCount !in TimeTableLayoutUtils.MIN_NIGHT_SECTION_COUNT..TimeTableLayoutUtils.MAX_NIGHT_SECTION_COUNT

    var morningCount by remember {
        mutableIntStateOf(
            initialMorningCount.coerceIn(
                TimeTableLayoutUtils.MIN_SECTION_COUNT,
                TimeTableLayoutUtils.MAX_SECTION_COUNT
            )
        )
    }
    var afternoonCount by remember {
        mutableIntStateOf(
            initialAfternoonCount.coerceIn(
                TimeTableLayoutUtils.MIN_SECTION_COUNT,
                TimeTableLayoutUtils.MAX_SECTION_COUNT
            )
        )
    }
    var nightCount by remember {
        mutableIntStateOf(
            initialNightCount.coerceIn(
                TimeTableLayoutUtils.MIN_NIGHT_SECTION_COUNT,
                TimeTableLayoutUtils.MAX_NIGHT_SECTION_COUNT
            )
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("课程结构", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "总节数 ${morningCount + afternoonCount + nightCount} 节",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (requiresLegacyConversion) {
                    Text(
                        text = "当前作息表属于旧版结构，确认后会转换为新的早/中/晚三段节数。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimeTableCountWheel(
                        title = "上午",
                        items = daySectionOptions,
                        selectedValue = morningCount,
                        modifier = Modifier.weight(1f),
                        onValueChanged = { morningCount = it }
                    )
                    TimeTableCountWheel(
                        title = "中午",
                        items = daySectionOptions,
                        selectedValue = afternoonCount,
                        modifier = Modifier.weight(1f),
                        onValueChanged = { afternoonCount = it }
                    )
                    TimeTableCountWheel(
                        title = "晚上",
                        items = nightSectionOptions,
                        selectedValue = nightCount,
                        modifier = Modifier.weight(1f),
                        onValueChanged = { nightCount = it }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(morningCount, afternoonCount, nightCount) }
            ) {
                Text("确定")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp
    )
}

@Composable
private fun TimeTableCountWheel(
    title: String,
    items: List<Int>,
    selectedValue: Int,
    modifier: Modifier = Modifier,
    onValueChanged: (Int) -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        WheelPicker(
            items = items.map { "$it 节" },
            initialIndex = items.indexOf(selectedValue).coerceAtLeast(0),
            onSelectionChanged = { onValueChanged(items[it]) }
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    time: LocalTime,
    sectionHeaderStyle: TextStyle,
    contentBodyStyle: TextStyle,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = title, style = sectionHeaderStyle)
        Text(text = "开始: $time", style = contentBodyStyle)
    }
}

private fun isChronologicalTimeTable(nodes: List<TimeNode>): Boolean {
    if (nodes.any { node ->
            try {
                val start = LocalTime.parse(node.startTime)
                val end = LocalTime.parse(node.endTime)
                !end.isAfter(start)
            } catch (_: Exception) {
                true
            }
        }
    ) {
        return false
    }

    return nodes.zipWithNext().all { (current, next) ->
        try {
            val currentEnd = LocalTime.parse(current.endTime)
            val nextStart = LocalTime.parse(next.startTime)
            !currentEnd.isAfter(nextStart)
        } catch (_: Exception) {
            false
        }
    }
}

private fun nearestOption(options: List<Int>, target: Int): Int {
    if (options.isEmpty()) return target

    var nearest = options.first()
    var bestDistance = kotlin.math.abs(nearest - target)

    for (option in options.drop(1)) {
        val distance = kotlin.math.abs(option - target)
        if (distance < bestDistance) {
            nearest = option
            bestDistance = distance
        }
    }

    return nearest
}

private fun parseTimeOrFallback(value: String, fallback: LocalTime): LocalTime {
    return try {
        LocalTime.parse(normalizeTimeText(value))
    } catch (_: Exception) {
        fallback
    }
}

private fun normalizeTimeText(value: String): String {
    return value.trim()
        .replace('\uFF1A', ':')
        .replace('.', ':')
}
