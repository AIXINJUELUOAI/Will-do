package com.antgskds.calendarassistant.feature.home.ui.render.material

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlin.math.roundToInt
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.accounting.ui.AccountingPreviewData
import com.antgskds.calendarassistant.feature.accounting.ui.LocalAccountingEntries
import com.antgskds.calendarassistant.feature.home.domain.HomeAgendaMapper
import com.antgskds.calendarassistant.feature.home.domain.HomeAgendaRow
import com.antgskds.calendarassistant.feature.home.ui.contract.HomePageUiAction
import com.antgskds.calendarassistant.feature.home.ui.contract.HomePageUiState
import com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.util.LunarCalendarUtils
import com.antgskds.calendarassistant.app.ui.theme.material.background.appBackgroundSurfaceAlpha
import androidx.compose.ui.graphics.luminance
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private enum class HomeTodayScene { CALENDAR, AGENDA, SEARCH }

/** 三份滚动状态跨动画保留；目标列表在进入动画开始时就定位，避免显示后再跳。 */
@Composable
internal fun HomeTodayContent(
    state: HomePageUiState,
    todayEvents: List<ScheduleDisplayItem>,
    tomorrowEvents: List<ScheduleDisplayItem>,
    searchQuery: String,
    calendarViewMode: HomeCalendarViewMode,
    listState: LazyListState,
    contentBottomPadding: Dp,
    uiSize: Int,
    isTwoPane: Boolean,
    serviceEnabled: Boolean,
    notificationEnabled: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    onOpenWeatherDetail: () -> Unit,
    onOpenAccounting: (LocalDate, Boolean) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onAction: (HomePageUiAction) -> Unit,
    onEditItem: (ScheduleDisplayItem) -> Unit,
    onRequestDeleteItem: (ScheduleDisplayItem) -> Unit,
    scheduleContent: @Composable () -> Unit,
    onCalendarViewModeChange: (HomeCalendarViewMode) -> Unit,
) {
    val entries = LocalAccountingEntries.current
    val bills = remember(entries) { AccountingPreviewData.bills(entries) }
    val billDates = remember(bills) { bills.map { it.date }.toSet() }
    val expenses = remember(bills) {
        bills.filter { it.counted && it.direction == "EXPENSE" }.groupBy { it.date }
            .mapValues { (_, items) -> items.sumOf { it.cents } }
    }
    val reverse = state.settings.homeAgendaReverseOrder
    val onlyScheduled = state.settings.homeAgendaOnlyScheduled
    val rangeEnd = state.today.plusDays(state.agendaFutureDays.toLong())
    val rows = remember(state.agendaItems, billDates, reverse, onlyScheduled, state.today, rangeEnd) {
        HomeAgendaMapper.rows(state.agendaItems, billDates, "", reverse, onlyScheduled, state.today..rangeEnd)
    }
    // Keep outgoing search content unchanged while clearing the field animates back to the previous scene.
    var lastSearchQuery by rememberSaveable { mutableStateOf("") }
    val displayedSearchQuery = searchQuery.ifBlank { lastSearchQuery }
    SideEffect { if (searchQuery.isNotBlank()) lastSearchQuery = searchQuery }
    val searchRows = remember(state.agendaItems, displayedSearchQuery, reverse) {
        HomeAgendaMapper.rows(state.agendaItems, emptySet(), displayedSearchQuery, reverse)
    }
    val agendaState = rememberLazyListState(initialFirstVisibleItemIndex = HomeAgendaMapper.anchorIndex(rows, state.selectedDate))
    val searchState = rememberLazyListState()
    var previousCalendarMode by rememberSaveable { mutableStateOf(HomeCalendarViewMode.TODAY.name) }
    if (calendarViewMode != HomeCalendarViewMode.AGENDA) {
        SideEffect { previousCalendarMode = calendarViewMode.name }
    }
    val scene = when {
        searchQuery.isNotBlank() -> HomeTodayScene.SEARCH
        calendarViewMode == HomeCalendarViewMode.AGENDA -> HomeTodayScene.AGENDA
        else -> HomeTodayScene.CALENDAR
    }
    var autoSelectedDate by remember { mutableStateOf<LocalDate?>(null) }
    // VM 的日期快照可能晚于快速滚动一帧；中间回执也不能触发反向定位。
    val pendingScrollDates = remember { mutableSetOf<LocalDate>() }
    val selectManually: (LocalDate) -> Unit = {
        autoSelectedDate = null
        pendingScrollDates.clear()
        onSelectDate(it)
    }
    // Search does not change these keys, so clearing it restores the original agenda position.
    LaunchedEffect(calendarViewMode, state.selectedDate, reverse, state.agendaReady, onlyScheduled) {
        if (calendarViewMode == HomeCalendarViewMode.AGENDA && state.agendaReady && rows.isNotEmpty()) {
            val scrollAcknowledged = pendingScrollDates.remove(state.selectedDate)
            if ((scrollAcknowledged || autoSelectedDate == state.selectedDate) && agendaState.layoutInfo.visibleItemsInfo.isNotEmpty()) {
                return@LaunchedEffect
            }
            val loadRowOffset = if (reverse) 1 else 0
            agendaState.requestScrollToItem(HomeAgendaMapper.anchorIndex(rows, state.selectedDate) + loadRowOffset)
        }
    }
    LaunchedEffect(searchQuery) {
        if (searchQuery.isNotBlank()) searchState.requestScrollToItem(0)
    }
    val duration = ConfigCatalog.HOME_AGENDA_MOTION_MS
    val isDayHeader = scene == HomeTodayScene.CALENDAR && calendarViewMode == HomeCalendarViewMode.TODAY
    // Overlay 没有外层日期卡片的裁剪，必须直接画出顶栏最终可见的上圆角、下直角。
    val selectionTopRadius by animateDpAsState(
        if (isDayHeader) 16.dp else 12.dp,
        tween(duration), label = "home_shared_date_top_radius",
    )
    val selectionBottomRadius by animateDpAsState(
        if (isDayHeader) 0.dp else 12.dp,
        tween(duration), label = "home_shared_date_bottom_radius",
    )
    val selectionShape = RoundedCornerShape(
        topStart = selectionTopRadius, topEnd = selectionTopRadius,
        bottomStart = selectionBottomRadius, bottomEnd = selectionBottomRadius,
    )
    val baseSelectionColor = if (state.selectedDate != state.today) {
        MaterialTheme.colorScheme.surfaceVariant
    } else MaterialTheme.colorScheme.primary
    val selectionColor by animateColorAsState(
        if (state.settings.appBackgroundImagePath.isNotBlank()) baseSelectionColor.copy(
            alpha = appBackgroundSurfaceAlpha(
                cardAlphaPercent = state.settings.appBackgroundCardAlphaPercent,
                dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
                miuiBlurEnabled = state.settings.appBackgroundMiuiBlurTestEnabled,
            )
        ) else baseSelectionColor,
        tween(duration), label = "home_shared_date_color",
    )
    SharedTransitionLayout(Modifier.fillMaxSize()) {
        val sharedScope = this
        AnimatedContent(
            targetState = scene,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = {
                fadeIn(tween(duration)).togetherWith(fadeOut(tween(duration))).using(SizeTransform(clip = false))
            },
            label = "home_agenda_transition",
        ) { target ->
            CompositionLocalProvider(
                LocalHomeDateSelectionTransition provides if (target == HomeTodayScene.SEARCH) null
                    else HomeDateSelectionTransition(sharedScope, this, selectionShape, selectionColor),
            ) {
                if (target == HomeTodayScene.CALENDAR) {
                    HomeCalendarContent(
                        state = state,
                        todayEvents = todayEvents,
                        tomorrowEvents = tomorrowEvents,
                        searchQuery = "",
                        calendarViewMode = if (calendarViewMode == HomeCalendarViewMode.AGENDA) HomeCalendarViewMode.valueOf(previousCalendarMode) else calendarViewMode,
                        listState = listState,
                        contentBottomPadding = contentBottomPadding,
                        uiSize = uiSize,
                        isTwoPane = isTwoPane,
                        serviceEnabled = serviceEnabled,
                        notificationEnabled = notificationEnabled,
                        onSelectDate = selectManually,
                        onOpenWeatherDetail = onOpenWeatherDetail,
                        onOpenAccounting = onOpenAccounting,
                        onOpenAccessibilitySettings = onOpenAccessibilitySettings,
                        onOpenNotificationSettings = onOpenNotificationSettings,
                        onAction = onAction,
                        onEditItem = onEditItem,
                        onRequestDeleteItem = onRequestDeleteItem,
                        scheduleContent = scheduleContent,
                        onCalendarViewModeChange = onCalendarViewModeChange,
                    )
                } else {
                    val query = if (target == HomeTodayScene.SEARCH) displayedSearchQuery else ""
                    HomeAgendaList(
                        rows = if (target == HomeTodayScene.SEARCH) searchRows else rows,
                        expenses = expenses,
                        listState = if (target == HomeTodayScene.SEARCH) searchState else agendaState,
                        state = state,
                        searching = target == HomeTodayScene.SEARCH,
                        canLoadMore = state.agendaFutureItems.any { HomeAgendaMapper.matches(it, query) },
                        contentBottomPadding = contentBottomPadding,
                        uiSize = uiSize,
                        onSelectDate = selectManually,
                        keepDateAnchor = !isTwoPane && target == HomeTodayScene.AGENDA,
                        followScrollSelection = !isTwoPane && target == HomeTodayScene.AGENDA && scene == HomeTodayScene.AGENDA,
                        onScrollSelectDate = { date ->
                            autoSelectedDate = date
                            pendingScrollDates.add(date)
                            onSelectDate(date)
                        },
                        onOpenAccounting = onOpenAccounting,
                        onAction = onAction,
                        onEditItem = onEditItem,
                        onRequestDeleteItem = onRequestDeleteItem,
                    )
                }
            }
        }
    }
}

/** 独立键的扁平 LazyColumn；追加/前插七天时由 Compose 维持首个可见项及像素偏移。 */
@Composable
private fun HomeAgendaList(
    rows: List<HomeAgendaRow>,
    expenses: Map<LocalDate, Long>,
    listState: LazyListState,
    state: HomePageUiState,
    searching: Boolean,
    followScrollSelection: Boolean = false,
    keepDateAnchor: Boolean = false,
    onScrollSelectDate: (LocalDate) -> Unit = {},
    canLoadMore: Boolean,
    contentBottomPadding: Dp,
    uiSize: Int,
    onSelectDate: (LocalDate) -> Unit,
    onOpenAccounting: (LocalDate, Boolean) -> Unit,
    onAction: (HomePageUiAction) -> Unit,
    onEditItem: (ScheduleDisplayItem) -> Unit,
    onRequestDeleteItem: (ScheduleDisplayItem) -> Unit,
) {
    val reverse = state.settings.homeAgendaReverseOrder
    var pullDistance by remember { mutableFloatStateOf(0f) }
    var loading by remember { mutableStateOf(false) }
    var pendingAnchorKey by remember { mutableStateOf<String?>(null) }
    var pendingAnchorOffset by remember { mutableIntStateOf(0) }
    var previousKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var emptyWindow by remember { mutableStateOf(false) }
    val selectedDate = rows.getOrNull(HomeAgendaMapper.anchorIndex(rows, state.selectedDate))?.date ?: state.selectedDate
    val latestSelectedDate by rememberUpdatedState(selectedDate)
    val latestScrollSelect by rememberUpdatedState(onScrollSelectDate)
    var badgeHeight by remember { mutableIntStateOf(0) }
    var badgeTop by remember { mutableIntStateOf(0) }
    var towardTop by remember { mutableStateOf(true) }
    val dateTopPadding = with(LocalDensity.current) { 24.dp.roundToPx() }
    val bottomOcclusion = with(LocalDensity.current) { contentBottomPadding.roundToPx() }
    fun rowAt(index: Int) = rows.getOrNull(index - if (reverse && (rows.isNotEmpty() || canLoadMore)) 1 else 0)
    fun visibleBadgeDates(): List<LocalDate> {
        val layout = listState.layoutInfo
        val bottom = layout.viewportEndOffset - bottomOcclusion
        return layout.visibleItemsInfo.mapNotNull { info ->
            val row = rowAt(info.index) ?: return@mapNotNull null
            val top = info.offset + dateTopPadding + badgeTop
            row.date.takeIf { row.startsDay && badgeHeight > 0 && top < bottom && top + badgeHeight > layout.viewportStartOffset }
        }
    }
    LaunchedEffect(listState, rows, followScrollSelection, bottomOcclusion, reverse) {
        if (!followScrollSelection) return@LaunchedEffect
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { Triple(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, badgeHeight) }
            .collect { (index, offset, _) ->
                val moved = index != previousIndex || offset != previousOffset
                if (moved && listState.isScrollInProgress) {
                    towardTop = index > previousIndex || (index == previousIndex && offset > previousOffset)
                    val dates = visibleBadgeDates()
                    val next = if (dates.isNotEmpty()) {
                        HomeAgendaMapper.followSelection(latestSelectedDate, dates, towardTop)
                    } else {
                        // 一天占满整屏时，在日期栏边缘保留该日期作为共享动画起点。
                        val visibleRows = listState.layoutInfo.visibleItemsInfo.mapNotNull { rowAt(it.index) }
                        (if (towardTop) visibleRows.firstOrNull() else visibleRows.lastOrNull())?.date ?: latestSelectedDate
                    }
                    if (next != latestSelectedDate) latestScrollSelect(next)
                }
                previousIndex = index
                previousOffset = offset
            }
    }
    val haptics = rememberAppHaptics(state.settings.hapticFeedbackEnabled)
    val threshold = with(LocalDensity.current) { ConfigCatalog.HOME_AGENDA_PULL_DP.dp.toPx() }
    val load by rememberUpdatedState {
        if (canLoadMore && !loading) {
            // Never anchor to the loader: moving it past newly inserted dates would skip the new page.
            val keys = rows.map { it.key }.toHashSet()
            val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key in keys }
            pendingAnchorKey = visible?.key as? String
                ?: (if (reverse) rows.firstOrNull()?.key else rows.lastOrNull()?.key)
            pendingAnchorOffset = visible?.offset?.let { -it } ?: 0
            previousKeys = rows.map { it.key }.toSet()
            emptyWindow = false
            loading = true
            haptics.click()
            onAction(HomePageUiAction.LoadMoreAgenda)
        }
    }
    LaunchedEffect(state.agendaFutureDays) {
        if (loading) {
            val index = rows.indexOfFirst { it.key == pendingAnchorKey }
            if (index >= 0) {
                listState.requestScrollToItem(index + if (reverse) 1 else 0, pendingAnchorOffset)
            }
            emptyWindow = rows.none { it.key !in previousKeys }
            // Local expansion may finish within a frame; let the progress feedback complete its entrance.
            delay(ConfigCatalog.HOME_AGENDA_MOTION_MS.toLong())
        }
        loading = false
        pendingAnchorKey = null
        pullDistance = 0f
    }
    val connection = remember(reverse, canLoadMore, loading, threshold, listState) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Reversing the gesture retracts the indicator before scrolling the list.
                val delta = if (reverse) available.y else -available.y
                if (source == NestedScrollSource.UserInput && pullDistance > 0 && delta < 0) {
                    val consumed = maxOf(delta, -pullDistance)
                    pullDistance += consumed
                    return Offset(0f, if (reverse) consumed else -consumed)
                }
                return Offset.Zero
            }
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!canLoadMore || loading || source != NestedScrollSource.UserInput) return Offset.Zero
                val atEnd = if (reverse) !listState.canScrollBackward else !listState.canScrollForward
                val distance = if (reverse) available.y else -available.y
                if (atEnd && distance > 0) {
                    pullDistance += distance
                    return available
                }
                return Offset.Zero
            }
            override suspend fun onPreFling(available: Velocity): Velocity {
                val consumed = pullDistance > 0
                if (pullDistance >= threshold) load()
                pullDistance = 0f
                return if (consumed) available else Velocity.Zero
            }
        }
    }
    val pinnedDateVisible by remember(rows, selectedDate, keepDateAnchor, badgeHeight, badgeTop, bottomOcclusion, reverse) {
        derivedStateOf {
            keepDateAnchor && badgeHeight > 0 && selectedDate !in visibleBadgeDates() &&
                listState.layoutInfo.visibleItemsInfo.any { rowAt(it.index)?.date == selectedDate }
        }
    }

    val indicatorProgress by animateFloatAsState(
        if (loading) 1f else (pullDistance / threshold).coerceIn(0f, 1f),
        tween(ConfigCatalog.HOME_AGENDA_MOTION_MS), label = "agenda_pull_indicator",
    )
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().testTag("home_agenda_list").nestedScroll(connection),
            contentPadding = PaddingValues(top = 8.dp, bottom = contentBottomPadding),
        ) {
            fun loadItem() {
                item(key = "agenda-load-future", contentType = "load") {
                    Box(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (canLoadMore) {
                            TextButton(onClick = { load() }, enabled = !loading) {
                                Text(
                                    HomeAgendaMapper.loadHint(
                                        canLoadMore, loading, emptyWindow, reverse,
                                        pullDistance >= threshold,
                                    ),
                                )
                            }
                        } else {
                            Text(
                                HomeAgendaMapper.loadHint(false, false, false, reverse, false),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
            if (reverse && (rows.isNotEmpty() || canLoadMore)) loadItem()
            if (rows.isEmpty()) {
                item(key = "agenda-empty") {
                    Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                        Text(
                            if (!state.agendaReady) "正在加载" else if (searching) "未找到相关日程" else "暂无日程",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(rows, key = { it.key }, contentType = { "agenda-row" }) { row ->
                Row(
                    Modifier.fillMaxWidth().padding(top = if (row.startsDay) 24.dp else 0.dp, bottom = 8.dp, start = 12.dp, end = 16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    if (row.startsDay) {
                        AgendaDateBadge(
                            row.date, state.today, if (pinnedDateVisible) null else selectedDate, onClick = { onSelectDate(row.date) },
                            onSelectionBounds = { top, height -> badgeTop = top; badgeHeight = height },
                        )
                    } else {
                        Spacer(Modifier.width(60.dp))
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        if (row.startsDay) {
                            val expense = expenses[row.date] ?: 0L
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                                    haptics.click()
                                    onOpenAccounting(row.date, false)
                                }.padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Outlined.AccountBalanceWallet, contentDescription = null, modifier = Modifier.size(20.dp))
                                Text(
                                    if (expense > 0) "支出 " + AccountingPreviewData.money(expense) else "暂无消费记录",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                            }
                        }
                        row.item?.let { item ->
                            HomeAgendaEventItem(item, state, uiSize, onAction, onEditItem, onRequestDeleteItem)
                        } ?: Text("暂无日程", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }
            }
            if (!reverse && (rows.isNotEmpty() || canLoadMore)) loadItem()
        }
        if (pinnedDateVisible) {
            AgendaDateBadge(
                selectedDate, state.today, selectedDate,
                onClick = { onSelectDate(selectedDate) },
                modifier = Modifier.align(if (towardTop) Alignment.TopStart else Alignment.BottomStart)
                    .padding(start = 12.dp, top = 8.dp, bottom = contentBottomPadding),
            )
        }
        if (indicatorProgress > 0f) {
            Surface(
                modifier = Modifier
                    .align(if (reverse) Alignment.TopCenter else Alignment.BottomCenter)
                    .padding(top = 8.dp, bottom = if (reverse) 0.dp else contentBottomPadding + 8.dp)
                    .graphicsLayer {
                        alpha = indicatorProgress
                        translationY = (if (reverse) -1f else 1f) * (1f - indicatorProgress) * size.height
                    },
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 3.dp,
                shadowElevation = 2.dp,
            ) {
                Row(
                    Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        CircularProgressIndicator(
                            progress = { indicatorProgress }, modifier = Modifier.size(18.dp), strokeWidth = 2.dp,
                            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        )
                    }
                    Text(
                        HomeAgendaMapper.loadHint(canLoadMore, loading, emptyWindow, reverse, pullDistance >= threshold),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AgendaDateBadge(
    date: LocalDate,
    today: LocalDate,
    selectedDate: LocalDate?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectionBounds: (Int, Int) -> Unit = { _, _ -> },
) {
    val isToday = date == today
    val isSelected = date == selectedDate
    val color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier.width(60.dp).testTag(if (isSelected) "agenda_selected_date" else "agenda_date_$date").clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA), color = color, fontWeight = FontWeight.Bold)
        Box(
            Modifier.onGloballyPositioned { onSelectionBounds(it.positionInParent().y.roundToInt(), it.size.height) },
            contentAlignment = Alignment.Center,
        ) {
            if (isSelected) {
                Box(Modifier.matchParentSize().homeDateSelectionBackground(
                    12.dp,
                    if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                ))
            }
            Column(
                Modifier.widthIn(min = 44.dp)
                    .then(if (isSelected) Modifier.homeDateSelectionForeground() else Modifier)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected && isToday) MaterialTheme.colorScheme.onPrimary else color,
                )
                Text(
                    LunarCalendarUtils.getLunarDate(date).takeLast(2),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected && isToday) MaterialTheme.colorScheme.onPrimary else color,
                )
            }
        }
        // Cross-month/year lists must remain unambiguous even when dates have gaps.
        Text(
            if (date.year == today.year) date.monthValue.toString() + "月" else date.year.toString() + "/" + date.monthValue,
            style = MaterialTheme.typography.labelSmall, color = color,
        )
    }
}
