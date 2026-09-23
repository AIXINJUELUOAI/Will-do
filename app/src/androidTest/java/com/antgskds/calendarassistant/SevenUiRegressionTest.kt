package com.antgskds.calendarassistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.app.ui.theme.material.background.LocalAppBackgroundStyleEnabled
import com.antgskds.calendarassistant.feature.home.ui.contract.HomePageUiState
import com.antgskds.calendarassistant.feature.home.ui.render.material.HomeCalendarViewMode
import com.antgskds.calendarassistant.feature.home.ui.render.material.HomeTodayContent
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoReminderEntity
import com.antgskds.calendarassistant.feature.quickmemo.ui.render.material.QuickMemoReminderSection
import com.antgskds.calendarassistant.feature.schedule.domain.ScheduleDisplayHelper
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 需要设备执行；主机仅编译，不把编译通过当作视觉验收。 */
class SevenUiRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun remindersReplaceTheirRowsAndKeepOneAddRowAtBottom() {
        val now = System.currentTimeMillis()
        var reminders by mutableStateOf(listOf(
            QuickMemoReminderEntity(id = 1, quickMemoId = 1, triggerAt = now),
            QuickMemoReminderEntity(id = 2, quickMemoId = 1, triggerAt = now + 86_400_000),
        ))
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalAppBackgroundStyleEnabled provides true) {
                    QuickMemoReminderSection(reminders, { _, _, _ -> }, {}, 2, false)
                }
            }
        }
        compose.onAllNodesWithText("修改").assertCountEquals(2)
        compose.onAllNodesWithText("添加").assertCountEquals(1)
        compose.onNodeWithText("未设置提醒").assertDoesNotExist()
        compose.onNodeWithText("不重复", useUnmergedTree = true).assertDoesNotExist()
        val add = compose.onNodeWithText("添加").fetchSemanticsNode().boundsInRoot
        compose.onAllNodesWithText("修改").fetchSemanticsNodes().forEach {
            assertTrue(add.top >= it.boundsInRoot.bottom)
        }
        compose.runOnIdle { reminders = emptyList() }
        compose.onAllNodesWithText("修改").assertCountEquals(0)
        compose.onNodeWithText("提醒").assertIsDisplayed()
        compose.onNodeWithText("添加").assertIsDisplayed().performClick()
        compose.onNodeWithText("添加提醒").assertIsDisplayed()
    }

    @Test fun reminderHasNoWallpaperPanelAndBothTextsShareBaseline() {
        val time = LocalDate.now().atTime(11, 51).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalAppBackgroundStyleEnabled provides true, LocalDensity provides Density(1f)) {
                    Box(Modifier.requiredSize(360.dp, 180.dp).background(Color.Blue)
                        .testTag("reminder_panel").padding(16.dp)) {
                        QuickMemoReminderSection(
                            listOf(QuickMemoReminderEntity(id = 1, quickMemoId = 1, triggerAt = time, rrule = "FREQ=DAILY")),
                            { _, _, _ -> }, {}, 2, false,
                        )
                    }
                }
            }
        }
        fun baseline(text: String): Float {
            val node = compose.onNodeWithText(text, useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            return node.fetchSemanticsNode().boundsInRoot.top + layouts.single().firstBaseline
        }
        assertEquals(baseline("今天 11:51"), baseline("· 每天"), 1f)
        // 行顶内边距必须透出底图，不能重新叠加磨砂卡片。
        val pixels = compose.onNodeWithTag("reminder_panel").captureToImage().toPixelMap()
        assertEquals(Color.Blue, pixels[pixels.width / 2, 18])
        val title = compose.onNodeWithText("今天 11:51", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val repeat = compose.onNodeWithText("· 每天", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(repeat.left > title.right)
    }

    @Test fun forwardAgendaHandsSelectionToVisibleDate() = verifyAgenda(reverse = false, sameDay = false)
    @Test fun reverseAgendaHandsSelectionToVisibleDate() = verifyAgenda(reverse = true, sameDay = false)
    @Test fun longDayKeepsVisibleSharedDateAnchor() = verifyAgenda(reverse = false, sameDay = true)

    private fun verifyAgenda(reverse: Boolean, sameDay: Boolean) {
        val today = LocalDate.now()
        val items = (0..30).map { index ->
            val date = if (sameDay) today else today.plusDays(index.toLong())
            val start = date.atTime(9, 0).atZone(ZoneId.systemDefault()).toEpochSecond()
            ScheduleDisplayHelper.eventToSingleItem(Event(
                id = index + 1L, startTS = start, endTS = start + 3600, title = "测试日程 $index",
            ))
        }
        val initial = if (reverse) today.plusDays(30) else today
        var selected by mutableStateOf(initial)
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalDensity provides Density(1f)) {
                    Box(Modifier.requiredSize(360.dp, 520.dp)) {
                        HomeTodayContent(
                            state = HomePageUiState(selectedDate = selected, today = today,
                                agendaItems = items, agendaReady = true,
                                settings = MySettings(homeAgendaReverseOrder = reverse)),
                            todayEvents = emptyList(), tomorrowEvents = emptyList(),
                            searchQuery = "", calendarViewMode = HomeCalendarViewMode.AGENDA,
                            listState = rememberLazyListState(), contentBottomPadding = 60.dp,
                            uiSize = 2, isTwoPane = false, serviceEnabled = true, notificationEnabled = true,
                            onSelectDate = { selected = it }, onOpenWeatherDetail = {}, onOpenAccounting = { _, _ -> },
                            onOpenAccessibilitySettings = {}, onOpenNotificationSettings = {}, onAction = {},
                            onEditItem = {}, onRequestDeleteItem = {}, scheduleContent = {}, onCalendarViewModeChange = {},
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("home_agenda_list").performTouchInput { swipeUp() }
        compose.waitForIdle()
        compose.onAllNodesWithTag("agenda_selected_date").assertCountEquals(1)
        compose.onNodeWithTag("agenda_selected_date").assertIsDisplayed()
        compose.runOnIdle {
            if (sameDay) assertEquals(initial, selected) else assertNotEquals(initial, selected)
        }
    }
}
