package com.antgskds.calendarassistant.feature.accounting.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.outlined.CalendarViewDay
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.shared.ui.interaction.LocalAppHapticsEnabled
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.material.component.*
import com.antgskds.calendarassistant.app.ui.theme.material.SectionTitleTextStyle
import com.antgskds.calendarassistant.app.ui.theme.material.background.AppBackgroundStyleTheme
import com.antgskds.calendarassistant.app.ui.theme.material.background.rememberAppBackgroundStylePalette
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import java.time.LocalDate

/** 真实账单展示与文件导入入口，沿用已确认的页面布局。 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AccountingPreviewScreen(
    viewModel: AccountingViewModel,
    initialDate: LocalDate,
    initialMonthly: Boolean,
    onBack: () -> Unit,
    hapticEnabled: Boolean = true,
    uiSize: Int = 2,
    predictiveBackEnabled: Boolean = true,
    backgroundMode: Boolean = false,
    miuiBlurEnabled: Boolean = false,
    cardAlphaPercent: Int = MySettings.APP_BACKGROUND_CARD_ALPHA_DEFAULT_PERCENT,
) {
    AppBackgroundStyleTheme(backgroundMode, miuiBlurEnabled, cardAlphaPercent) {
        CompositionLocalProvider(LocalAppHapticsEnabled provides hapticEnabled) {
            val today = remember { LocalDate.now() }
            val dataState by viewModel.entries.collectAsState()
            val importState by viewModel.importState.collectAsState()
            val editorState by viewModel.editorState.collectAsState()
            val recognitionState by viewModel.recognitionState.collectAsState()
            val deleteState by viewModel.deleteState.collectAsState()
            val savedDate by viewModel.savedDate.collectAsState()
            val exportBills = rememberAccountingExportAction(viewModel)
            val bills = remember(dataState.entries) { AccountingPreviewData.bills(dataState.entries) }
            var anchorText by rememberSaveable { mutableStateOf(initialDate.toString()) }
            var periodName by rememberSaveable { mutableStateOf(if (initialMonthly) "MONTH" else "DAY") }
            val anchor = LocalDate.parse(anchorText)
            val period = PreviewPeriod.valueOf(periodName)
            val range = AccountingPreviewData.range(anchor, period)
            val suggestions = remember(bills, anchor, period, today) {
                AccountingSuggestionMapper.suggestions(bills, anchor, period, today)
            }
            var suggestionId by rememberSaveable(anchorText, periodName) { mutableStateOf<String?>(null) }
            val suggestionIndex = suggestions.indexOfFirst { it.id == suggestionId }.coerceAtLeast(0)
            val canCycleSuggestions = !dataState.loading && dataState.error == null && suggestions.size > 1
            val trendPeriod = if (period == PreviewPeriod.MONTH) PreviewPeriod.MONTH else PreviewPeriod.WEEK
            val trendRange = AccountingPreviewData.range(anchor, trendPeriod)
            val visible = remember(bills, range) { bills.filter { it.date in range }
                .sortedWith(compareByDescending<PreviewBill> { it.date }.thenByDescending { it.time }) }
            val expense = AccountingPreviewData.sum(bills, range)
            val income = AccountingPreviewData.sum(bills, range, income = true)
            val average = AccountingPreviewData.dailyAverage(bills, trendRange, today)
            val series = AccountingPreviewData.series(bills, trendRange, today)
            val haptics = rememberAppHaptics()
            val menuPalette = rememberAppBackgroundStylePalette(backgroundMode, miuiBlurEnabled, cardAlphaPercent)
            val menuContainer = if (backgroundMode) {
                menuPalette.surface.copy(alpha = MySettings.normalizeAppBackgroundCardAlphaPercent(cardAlphaPercent) / 100f)
            } else if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else MaterialTheme.colorScheme.surface
            val pageSecondary = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            var modeMenu by remember { mutableStateOf(false) }
            var analysis by rememberSaveable { mutableStateOf(false) }
            var transferMenu by remember { mutableStateOf(false) }
            var revealedId by remember { mutableStateOf<String?>(null) }
            val actionButtonSize = when (uiSize) { 1 -> 48.dp; 2 -> 52.dp; else -> 56.dp }
            val actionMenuWidth = when (uiSize) { 1 -> 130.dp; 2 -> 140.dp; else -> 150.dp }
            LaunchedEffect(savedDate) {
                savedDate?.let { anchorText = it.toString(); viewModel.consumeSavedDate() }
            }
            LaunchedEffect(anchorText, periodName) { revealedId = null }
            var detailId by rememberSaveable { mutableStateOf<String?>(null) }
            LaunchedEffect(importState.result) {
                if (importState.result != null) {
                    importState.preview?.entries?.maxByOrNull { it.occurredAt }?.let { latest ->
                        anchorText = java.time.Instant.ofEpochMilli(latest.occurredAt)
                            .atZone(java.time.ZoneId.of(latest.zoneId)).toLocalDate().toString()
                    }
                }
            }

            fun shift(step: Long) {
                haptics.selection()
                anchorText = AccountingPreviewData.shift(anchor, period, step).toString()
            }
            fun changePeriod(value: PreviewPeriod) {
                haptics.selection()
                periodName = value.name
            }
            val cycleTitle = AccountingPreviewData.billTitle(anchor, period, today)
            AppPageScaffold(
                edgeToEdgeContent = true,
                contentMaxWidth = 720.dp,
                topBar = {
                    AppTopBar("记账", navigationIcon = { AppTopBarBackButton(onBack) }, actions = {
                        Box {
                            Box(
                                Modifier.size(48.dp).combinedClickable(
                                    onClick = { changePeriod(PreviewPeriod.entries[(period.ordinal + 1) % 3]) },
                                    onLongClick = { haptics.longPress(); modeMenu = true },
                                    onClickLabel = "切换日周月视图", onLongClickLabel = "选择账单视图",
                                    hapticFeedbackEnabled = false,
                                ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.SwapHoriz,
                                    "当前${period.label}视图，切换到${PreviewPeriod.entries[(period.ordinal + 1) % 3].label}视图",
                                    Modifier.size(28.dp))
                            }
                            AppDropdownMenu(
                                expanded = modeMenu,
                                onDismissRequest = { modeMenu = false },
                                containerColor = menuContainer,
                                selectionColor = if (backgroundMode) menuPalette.accent else MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = if (backgroundMode) menuPalette.content else MaterialTheme.colorScheme.onSurfaceVariant,
                                items = PreviewPeriod.entries.map { mode ->
                                    AppMenuItem("${mode.label}视图", { changePeriod(mode) }, selected = mode == period,
                                        icon = when (mode) {
                                            PreviewPeriod.DAY -> Icons.Outlined.CalendarViewDay
                                            PreviewPeriod.WEEK -> Icons.Outlined.CalendarViewWeek
                                            PreviewPeriod.MONTH -> Icons.Outlined.CalendarViewMonth
                                        })
                                },
                            )
                        }
                    })
                },
                floatingActionButton = {
                    AppFloatingActionButton(
                        onClick = { haptics.click(); revealedId = null; viewModel.openEditor(anchor) },
                        onLongClick = { haptics.longPress(); transferMenu = true },
                        onLongClickLabel = "账单管理",
                    ) { Icon(Icons.Default.Add, "新建账单", Modifier.size(AppFloatingActionButtonDefaults.IconSize)) }
                },
            ) {
                val bottomPadding = LocalAppPageBottomPadding.current
                val listState = rememberLazyListState()
                LaunchedEffect(anchorText, periodName) {
                    if (listState.firstVisibleItemIndex > 0) listState.animateScrollToItem(0)
                }
                LazyColumn(
                    Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = bottomPadding + 112.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item(key = "suggestion") {
                        AppCard(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
                            onClick = if (canCycleSuggestions) ({
                                haptics.selection()
                                suggestionId = suggestions[(suggestionIndex + 1) % suggestions.size].id
                            }) else null,
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 24.dp)) {
                            val summaryTitle = when {
                                dataState.loading -> "正在读取账单"
                                dataState.error != null -> "账单读取失败"
                                else -> "收支建议"
                            }
                            val summaryDetail = if (dataState.error != null) "请重试读取，当前统计可能尚未更新。"
                                else if (dataState.loading) "收支统计即将显示。"
                                else suggestions[suggestionIndex].text
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Insights, null, tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(10.dp))
                                Text(summaryTitle, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineMedium.copy(
                                    fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold))
                                if (canCycleSuggestions) Text("${suggestionIndex + 1}/${suggestions.size} · 点击切换",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(summaryDetail, minLines = 3, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (recognitionState.drafts.isNotEmpty()) item(key = "recognition_drafts") {
                        OutlinedButton(onClick = viewModel::openRecognition, modifier = Modifier.fillMaxWidth()) {
                            Text("${recognitionState.drafts.size} 条识别账单待确认")
                        }
                    }
                    if (dataState.error != null && visible.isNotEmpty()) item(key = "load_error") {
                        TextButton(onClick = viewModel::reload) { Text("重试读取账单") }
                    }
                    item(key = "summary") {
                        PreviewSummaryCards(expense, income, average, trendPeriod, series) { haptics.click(); analysis = true }
                    }
                    item(key = "bill_header") {
                        // 仅在标题区域切换周期，账单点击用于查看详情。
                        val threshold = with(LocalDensity.current) { 48.dp.toPx() }
                        Column(Modifier.fillMaxWidth().semantics {
                            customActions = listOf(
                                CustomAccessibilityAction("上一${period.label}") { shift(-1); true },
                                CustomAccessibilityAction("下一${period.label}") { shift(1); true },
                            )
                        }.pointerInput(anchorText, periodName) {
                            var drag = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { drag = 0f },
                                onHorizontalDrag = { change, amount -> change.consume(); drag += amount },
                                onDragEnd = { if (drag < -threshold) shift(1) else if (drag > threshold) shift(-1) },
                                onDragCancel = { drag = 0f },
                            )
                        }) {
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                Spacer(Modifier.width(10.dp))
                                Text(cycleTitle, modifier = Modifier.weight(1f), style = SectionTitleTextStyle,
                                    color = MaterialTheme.colorScheme.onBackground)
                                if (today !in range) {
                                    TextButton(modifier = Modifier.padding(start = 8.dp), onClick = { haptics.click(); anchorText = today.toString() }) {
                                        Text(when (period) {
                                            PreviewPeriod.DAY -> "回到今天"
                                            PreviewPeriod.WEEK -> "回到本周"
                                            PreviewPeriod.MONTH -> "回到本月"
                                        })
                                    }
                                }
                            }
                        }
                    }
                    if (visible.isEmpty()) item(key = "empty") {
                        Box(Modifier.animateItem().fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(when {
                                    dataState.loading -> "正在读取账单…"
                                    dataState.error != null -> dataState.error.orEmpty()
                                    else -> "这个周期暂无账单"
                                }, color = pageSecondary)
                                TextButton(onClick = { if (dataState.error != null) viewModel.reload() else viewModel.openImport() }) {
                                    Text(if (dataState.error != null) "重试" else "导入账单")
                                }
                            }
                        }
                    }
                    visible.groupBy { it.date }.forEach { (date, rows) ->
                        if (period != PreviewPeriod.DAY) item(key = "date_$date") {
                            Text("${date.monthValue}月${date.dayOfMonth}日", modifier = Modifier.animateItem(),
                                style = MaterialTheme.typography.labelLarge, color = pageSecondary)
                        }
                        items(rows, key = { it.id }) { bill ->
                            AppSwipeReveal(
                                modifier = Modifier.animateItem(), identity = bill.id,
                                isRevealed = revealedId == bill.id, actionWidth = actionMenuWidth,
                                hapticEnabled = hapticEnabled,
                                onRevealedChange = { revealedId = if (it) bill.id else null },
                                actions = { close ->
                                    Row(Modifier.width(actionMenuWidth).padding(horizontal = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically) {
                                        SwipeActionIcon(Icons.Outlined.Edit, Color(0xFF4CAF50), actionButtonSize,
                                            hapticEnabled, contentDescription = "编辑账单") {
                                            close(); viewModel.openEditor(bill.date, bill.id)
                                        }
                                        SwipeActionIcon(Icons.Outlined.Delete, Color(0xFFF44336), actionButtonSize,
                                            hapticEnabled, contentDescription = "删除账单") {
                                            close(); viewModel.requestDelete(bill.id)
                                        }
                                    }
                                },
                            ) { swipeModifier, progress, close ->
                                Row(swipeModifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).semantics {
                                    customActions = listOf(
                                        CustomAccessibilityAction("编辑账单") { close(); viewModel.openEditor(bill.date, bill.id); true },
                                        CustomAccessibilityAction("删除账单") { close(); viewModel.requestDelete(bill.id); true },
                                    )
                                }.combinedClickable(
                                    onClick = { haptics.click(); if (progress > 0f) close() else detailId = bill.id },
                                    onLongClick = { haptics.longPress(); close(); detailId = bill.id },
                                    hapticFeedbackEnabled = false,
                                ).padding(top = 12.dp, bottom = 12.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    val accent = if (!bill.counted) MaterialTheme.colorScheme.onSurfaceVariant
                                        else if (bill.income) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                                    Box(Modifier.width(4.dp).height(40.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(bill.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                                        Text("${bill.time} · ${bill.category}" + when {
                                            bill.status != "CONFIRMED" -> " · 待核对"
                                            bill.direction == "TRANSFER" -> " · 不计收支"
                                            else -> ""
                                        }, style = MaterialTheme.typography.bodySmall, color = pageSecondary)
                                    }
                                    Spacer(Modifier.width(10.dp))
                                    Text(billAmount(bill), style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = "tnum"),
                                        color = if (backgroundMode) MaterialTheme.colorScheme.onBackground else accent)
                                }
                            }
                        }
                    }
                }
            }
            if (analysis) PreviewAnalysisSheet(visible, series, anchor, trendRange, range, period, average) { analysis = false }
            bills.firstOrNull { it.id == detailId }?.let { bill ->
                AccountingEntrySheet(bill, onEdit = {
                    detailId = null; viewModel.openEditor(bill.date, bill.id)
                }, onDismiss = { detailId = null })
            }
            PredictiveFloatingActionCard(
                visible = transferMenu, title = "账单管理", content = "导出本机账单，或导入账单备份、微信和支付宝账单。",
                confirmText = "导入", dismissText = "导出", predictiveBackEnabled = predictiveBackEnabled,
                onConfirm = { transferMenu = false; viewModel.openImport() },
                onDismiss = { transferMenu = false; exportBills() }, onDismissRequest = { transferMenu = false },
                modifier = Modifier.padding(bottom = 24.dp),
            )
            PredictiveFloatingActionCard(
                visible = deleteState.entry != null,
                title = "删除账单",
                content = deleteState.error ?: "确定删除「${deleteState.entry?.merchant.orEmpty()}」？删除后将从列表和统计中移除。",
                confirmText = "删除", dismissText = "取消", isDestructive = true, isLoading = deleteState.saving,
                predictiveBackEnabled = predictiveBackEnabled,
                onConfirm = viewModel::confirmDelete, onDismiss = viewModel::closeDelete,
                onDismissRequest = viewModel::closeDelete, modifier = Modifier.padding(bottom = 24.dp),
            )
            if (editorState.open && editorState.draft == null) AccountingEditorSheet(editorState, viewModel::saveEntry, viewModel::closeEditor)
            if (importState.open) AccountingImportSheet(importState, viewModel::selectSource,
                viewModel::readFile, viewModel::confirmImport, viewModel::closeImport)
        }
    }
}

@Composable
private fun PreviewSummaryCards(
    expense: Long, income: Long, average: Long, trendPeriod: PreviewPeriod,
    series: List<Pair<LocalDate, Long?>>, onAnalysis: () -> Unit,
) {
    val totalCard: @Composable (Modifier) -> Unit = { modifier ->
        AppCard(modifier, shape = RoundedCornerShape(24.dp), contentPadding = PaddingValues(16.dp)) {
            Column(Modifier.fillMaxSize()) {
                PreviewSummaryHeading(expense - income, "净支出")
                Box(Modifier.fillMaxWidth().weight(1f).padding(top = 14.dp), contentAlignment = Alignment.BottomStart) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("支出", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            AnimatedContent(expense, label = "expense") { value ->
                                Text(AccountingPreviewData.money(value), style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("入账", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            AnimatedContent(income, label = "income") { value ->
                                Text(AccountingPreviewData.money(value), style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            }
        }
    }
    val averageCard: @Composable (Modifier) -> Unit = { modifier ->
        AppCard(modifier.semantics { contentDescription = "日均支出，点击查看账单分析" },
            shape = RoundedCornerShape(24.dp), contentPadding = PaddingValues(16.dp), onClick = onAnalysis) {
            Column(Modifier.fillMaxSize()) {
                PreviewSummaryHeading(average, "${if (trendPeriod == PreviewPeriod.MONTH) "所在月" else "所在周"}日均支出")
                Box(Modifier.fillMaxWidth().weight(1f).padding(top = 14.dp), contentAlignment = Alignment.BottomStart) {
                    Crossfade(series, modifier = Modifier.fillMaxSize(), label = "summary_trend") { points ->
                        PreviewLineChart(points, Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
    BoxWithConstraints {
        if (maxWidth < 340.dp || LocalDensity.current.fontScale > 1.25f) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                totalCard(Modifier.fillMaxWidth().aspectRatio(1f))
                averageCard(Modifier.fillMaxWidth().aspectRatio(1f))
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            totalCard(Modifier.weight(1f).aspectRatio(1f))
            averageCard(Modifier.weight(1f).aspectRatio(1f))
        }
    }
}

/** 两张统计卡共用金额与标签的排版，确保切换周期后仍保持顶部对齐。 */
@Composable
private fun PreviewSummaryHeading(amount: Long, label: String) {
    val amountStyle = MaterialTheme.typography.headlineMedium.copy(
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
        color = MaterialTheme.colorScheme.onSurface,
    )
    AnimatedContent(amount, label = "summary_amount") { value ->
        BasicText(AccountingPreviewData.money(value), modifier = Modifier.fillMaxWidth(),
            style = amountStyle, maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = 28.sp))
    }
    Spacer(Modifier.height(4.dp))
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewAnalysisSheet(
    visible: List<PreviewBill>, series: List<Pair<LocalDate, Long?>>,
    anchor: LocalDate, trendRange: PreviewRange, range: PreviewRange, period: PreviewPeriod,
    average: Long, onDismiss: () -> Unit,
) {
    var selectedDate by remember { mutableStateOf(anchor) }
    var income by remember { mutableStateOf(false) }
    AppModalBottomSheet(title = "${period.label}账单分析", subtitle = "${AccountingPreviewData.dateLabel(range, period)}",
        onDismissRequest = onDismiss) {
        Text(if (period == PreviewPeriod.MONTH) "每日支出趋势 · 所在月" else "每日支出趋势 · 所在周",
            style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text(AccountingPreviewData.money(average), style = MaterialTheme.typography.titleLarge); Text("日均支出", style = MaterialTheme.typography.labelMedium) }
            Column { Text(AccountingPreviewData.money(series.mapNotNull { it.second }.maxOrNull() ?: 0L), style = MaterialTheme.typography.titleLarge); Text("单日最高", style = MaterialTheme.typography.labelMedium) }
        }
        Spacer(Modifier.height(12.dp))
        val selected = series.firstOrNull { it.first == selectedDate }
        Crossfade(selectedDate to selected?.second, label = "selected_bill_day") { (date, amount) ->
            Text("${date.monthValue}月${date.dayOfMonth}日 · ${amount?.let(AccountingPreviewData::money) ?: "暂无数据"}",
                color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
        }
        PreviewLineChart(series, Modifier.fillMaxWidth().height(190.dp), selectedDate, { selectedDate = it }, detailed = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${trendRange.start.monthValue}/${trendRange.start.dayOfMonth}", style = MaterialTheme.typography.labelSmall)
            Text("点击折线查看日期金额", style = MaterialTheme.typography.labelSmall)
            Text("${trendRange.end.monthValue}/${trendRange.end.dayOfMonth}", style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(24.dp))
        Text("${period.label}收支结构", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        AppSegmentedControl(listOf(false, true), income, { income = it }, { if (it) "入账" else "支出" })
        PreviewStructureChart(visible, income)
    }
}

private fun billAmount(bill: PreviewBill): String {
    val prefix = when (bill.direction) { "INCOME" -> "+"; "EXPENSE" -> "−"; else -> "" }
    val amount = if (bill.currency == "CNY") AccountingPreviewData.money(bill.cents)
        else "${bill.currency} ${java.math.BigDecimal.valueOf(bill.cents, 2).toPlainString()}"
    return prefix + amount
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountingEntrySheet(bill: PreviewBill, onEdit: () -> Unit, onDismiss: () -> Unit) {
    AppModalBottomSheet(title = "账单详情", subtitle = "${bill.date} ${bill.time}",
        onDismissRequest = onDismiss, actions = listOf(
            AppSheetAction("关闭", onDismiss, AppSheetActionRole.Secondary), AppSheetAction("编辑", onEdit))) {
        Text(billAmount(bill), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(bill.title, style = MaterialTheme.typography.titleMedium)
        Text("分类：${bill.category}", Modifier.padding(top = 8.dp))
        Text("来源：${bill.channel.ifBlank { "其他" }}", Modifier.padding(top = 8.dp))
        if (bill.transactionId.isNotBlank()) Text("交易号：${bill.transactionId}", Modifier.padding(top = 8.dp))
        if (bill.note.isNotBlank()) Text(bill.note, Modifier.padding(top = 8.dp))
        AccountingSourceImage(bill.sourceImagePath)
        if (!bill.counted) Text(if (bill.status != "CONFIRMED")
            "此记录待核对，暂不计入统计。本版暂不支持手动核对；退款未自动抵扣，外币未折算人民币。"
            else "此记录不计入支出或入账统计。", Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
