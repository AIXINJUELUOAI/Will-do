package com.antgskds.calendarassistant.feature.home.domain

import com.antgskds.calendarassistant.feature.schedule.domain.ScheduleDisplayHelper
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.*
import org.junit.Test

class HomeAgendaTest {
    private val today = LocalDate.of(2026, 9, 19)
    private val window = today.plusDays(ConfigCatalog.HOME_AGENDA_PAGE_DAYS.toLong())

    @Test fun singleLastEventEndsTheListWithoutFuturePlaceholders() {
        val snapshot = ScheduleDisplayHelper.buildAgendaSnapshot(listOf(event(1, today)), window)
        val rows = HomeAgendaMapper.rows(snapshot.items, emptySet(), "", false)
        assertEquals(listOf(today), rows.map { it.date })
        assertEquals("single:1", rows.last().item!!.stableKey)
        assertTrue(snapshot.futureItems.isEmpty())
        assertTrue(HomeAgendaMapper.rows(emptyList(), emptySet(), "", false).isEmpty())
    }

    @Test fun ordinaryFutureAndPastEventsAreNotClippedToSevenDaysAndSearchIsGlobal() {
        val snapshot = ScheduleDisplayHelper.buildAgendaSnapshot(listOf(
            event(1, today.minusMonths(2), title = "复诊"),
            event(2, today.plusMonths(3), title = "复诊"),
            event(3, today, title = "午饭"),
            event(4, today, title = "复诊").copy(archivedAt = 1),
        ), window)
        val rows = HomeAgendaMapper.rows(snapshot.items, setOf(today.plusDays(3)), "复诊", false)
        assertEquals(listOf(today.minusMonths(2), today.plusMonths(3)), rows.map { it.date })
        assertTrue(snapshot.futureItems.isEmpty())
    }

    @Test fun infiniteRepeatExpandsOnlyThroughWindowAndAddsSevenDaysOnExplicitLoad() {
        val parent = event(1, today).copy(rrule = "FREQ=DAILY")
        val first = ScheduleDisplayHelper.buildAgendaSnapshot(listOf(parent), window)
        val next = ScheduleDisplayHelper.buildAgendaSnapshot(listOf(parent), window.plusDays(ConfigCatalog.HOME_AGENDA_PAGE_DAYS.toLong()))
        assertEquals(8, first.items.size) // Today plus the next seven days.
        assertEquals(15, next.items.size)
        assertEquals(window, first.items.maxOf { it.startDate })
        assertEquals(window.plusDays(1), first.futureItems.single().startDate)
        assertTrue(next.items.map { it.stableKey }.containsAll(first.items.map { it.stableKey }))
        assertTrue(next.futureItems.isNotEmpty())
    }

    @Test fun finiteRepeatsEndNaturallyEvenWhenBeyondInitialWindow() {
        val counted = event(1, today).copy(rrule = "FREQ=WEEKLY;COUNT=3")
        val until = event(2, today).copy(rrule = "FREQ=DAILY;UNTIL=20261001")
        val snapshot = ScheduleDisplayHelper.buildAgendaSnapshot(listOf(counted, until), window)
        assertEquals(3, snapshot.items.count { it.title == counted.title && it.stableKey.startsWith("rec:1:") })
        assertTrue(snapshot.items.any { it.startDate == today.plusDays(14) })
        assertTrue(snapshot.items.any { it.startDate == LocalDate.of(2026, 10, 1) })
        assertTrue(snapshot.futureItems.isEmpty())
    }

    @Test fun recurringExceptionsAreNotDuplicatedAndArchivedParentsDoNotLoadForever() {
        val parent = event(1, today).copy(rrule = "FREQ=DAILY", exdates = listOf("20260920T090000Z"))
        val child = event(2, today.plusDays(2)).copy(parentId = 1)
        val snapshot = ScheduleDisplayHelper.buildAgendaSnapshot(listOf(parent, child), window)
        assertTrue(snapshot.items.none { it.startDate == today.plusDays(1) })
        assertEquals(1, snapshot.items.count { it.startDate == today.plusDays(2) })
        assertEquals("single:2", snapshot.items.single { it.startDate == today.plusDays(2) }.stableKey)
        assertTrue(ScheduleDisplayHelper.buildAgendaSnapshot(listOf(parent.copy(archivedAt = 1)), window).futureItems.isEmpty())
    }

    @Test fun scheduledOnlyHidesBillOnlyDatesWhileCompleteFillsGaps() {
        val items = ScheduleDisplayHelper.buildAgendaSnapshot(listOf(event(1, today)), window).items
        val rows = HomeAgendaMapper.rows(items, setOf(today, today.plusDays(3)), "", false)
        assertEquals(listOf(today), rows.map { it.date })
        val complete = HomeAgendaMapper.rows(items, setOf(today.plusDays(3)), "", false, onlyScheduled = false)
        assertEquals((0L..3L).map { today.plusDays(it) }, complete.map { it.date })
        assertNull(complete.last().item)
        assertEquals(4, complete.count { it.startsDay })
    }

    @Test fun reversingDatesKeepsSameDayTimeAscendingAndKeysStableForPrepending() {
        val items = ScheduleDisplayHelper.buildAgendaSnapshot(listOf(
            event(1, today, hour = 16), event(2, today, hour = 9), event(3, today.plusDays(1)),
        ), window).items
        val forward = HomeAgendaMapper.rows(items, emptySet(), "", false)
        val reverse = HomeAgendaMapper.rows(items, emptySet(), "", true)
        assertEquals(listOf("single:2", "single:1"), reverse.filter { it.date == today }.map { it.item!!.stableKey })
        assertEquals(today.plusDays(1), reverse.first().date)
        assertEquals(forward.map { it.key }.toSet(), reverse.map { it.key }.toSet())
        assertEquals(1, reverse.filter { it.date == today }.count { it.startsDay })
        val appended = HomeAgendaMapper.rows(items + ScheduleDisplayHelper.eventToSingleItem(event(4, today.plusDays(5))), emptySet(), "", true)
        assertEquals(reverse.map { it.key }, appended.drop(1).map { it.key })
    }

    @Test fun selectedDateAnchorsToExactOrNearestGroupInEitherDirection() {
        val items = ScheduleDisplayHelper.buildAgendaSnapshot(listOf(
            event(1, today.minusDays(3)), event(2, today.plusDays(2)), event(3, today.plusDays(2), hour = 16),
        ), window).items
        for (reverse in listOf(false, true)) {
            val rows = HomeAgendaMapper.rows(items, emptySet(), "", reverse)
            assertEquals(today.plusDays(2), rows[HomeAgendaMapper.anchorIndex(rows, today)].date)
            assertEquals(today.minusDays(3), rows[HomeAgendaMapper.anchorIndex(rows, today.minusDays(3))].date)
            assertTrue(rows[HomeAgendaMapper.anchorIndex(rows, today)].startsDay)
        }
    }

    @Test fun futureSearchLoaderOnlyAppearsForMatchingRecurringSources() {
        val snapshot = ScheduleDisplayHelper.buildAgendaSnapshot(listOf(
            event(1, today).copy(title = "每日锻炼", rrule = "FREQ=DAILY"),
            event(2, today.plusMonths(3)).copy(title = "出差"),
        ), window)
        assertTrue(snapshot.futureItems.any { HomeAgendaMapper.matches(it, "锻炼") })
        assertFalse(snapshot.futureItems.any { HomeAgendaMapper.matches(it, "出差") })
        assertEquals(1, HomeAgendaMapper.rows(snapshot.items, emptySet(), "出差", false).size)
    }

    @Test fun newDirectionDefaultsDownAndRegisteredSettingChangesOnlyItsOwnField() {
        val defaults = MySettings()
        assertFalse(defaults.homeAgendaReverseOrder)
        val setting = ConfigCatalog.items.single { it.key == "home.agenda_reverse" }
        assertEquals(defaults.copy(homeAgendaReverseOrder = true), setting.set(defaults, 1))
        assertEquals(0, setting.get(defaults))
        assertEquals(1, setting.get(setting.set(defaults, 1)))
    }

    @Test fun emptyWeekWithLaterMonthlyOccurrenceKeepsLoadingAvailable() {
        val parent = event(1, today).copy(rrule = "FREQ=MONTHLY")
        val first = ScheduleDisplayHelper.buildAgendaSnapshot(listOf(parent), window)
        val next = ScheduleDisplayHelper.buildAgendaSnapshot(
            listOf(parent), window.plusDays(ConfigCatalog.HOME_AGENDA_PAGE_DAYS.toLong()),
        )
        assertEquals(first.items.map { it.stableKey }, next.items.map { it.stableKey })
        assertEquals(today.plusMonths(1), next.futureItems.single().startDate)
        for (reverse in listOf(false, true)) {
            val hint = HomeAgendaMapper.loadHint(next.futureItems.isNotEmpty(), false, true, reverse, false)
            assertTrue(hint.contains("暂无日程"))
            assertTrue(hint.contains(if (reverse) "下拉继续加载" else "上滑继续加载"))
            assertFalse(hint.contains("没有更多"))
        }
        assertEquals("没有更多日程了", HomeAgendaMapper.loadHint(false, false, true, false, false))
    }

    @Test fun loadFeedbackRespectsProgressAndGestureDirection() {
        assertTrue(HomeAgendaMapper.loadHint(true, false, false, false, false).startsWith("上滑"))
        assertTrue(HomeAgendaMapper.loadHint(true, false, false, true, false).startsWith("下拉"))
        assertTrue(HomeAgendaMapper.loadHint(true, false, true, false, true).startsWith("松手"))
        assertTrue(HomeAgendaMapper.loadHint(false, true, true, false, true).startsWith("正在加载"))
    }

    @Test fun completeEmptyWindowIncludesAllLoadedDaysAndSearchNeverPadsDates() {
        val complete = HomeAgendaMapper.rows(emptyList(), emptySet(), "", false, false, today..window)
        assertEquals(8, complete.size)
        assertEquals(today, complete.first().date)
        assertEquals(window, complete.last().date)
        assertTrue(HomeAgendaMapper.rows(emptyList(), setOf(today), "不存在", false, false, today..window).isEmpty())
    }

    @Test fun completeModeReversesDatesWithoutChangingKeys() {
        val forward = HomeAgendaMapper.rows(emptyList(), emptySet(), "", false, false, today..window)
        val reverse = HomeAgendaMapper.rows(emptyList(), emptySet(), "", true, false, today..window)
        assertEquals(forward.map { it.key }.reversed(), reverse.map { it.key })
    }

    @Test fun selectionStaysUntilItLeavesAndThenFollowsTheExitEdge() {
        val dates = listOf(today, today.plusDays(1), today.plusDays(2))
        assertEquals(today.plusDays(1), HomeAgendaMapper.followSelection(today.plusDays(1), dates, true))
        assertEquals(today.plusDays(1), HomeAgendaMapper.followSelection(today.plusDays(1), dates, false))
        assertEquals(today, HomeAgendaMapper.followSelection(today.minusDays(1), dates, true))
        assertEquals(today.plusDays(2), HomeAgendaMapper.followSelection(today.minusDays(1), dates, false))
    }

    @Test fun reverseOrderAndFastFlingUseVisualEdgeInsteadOfChronologicalOrder() {
        val dates = listOf(today.plusDays(10), today.plusDays(9))
        assertEquals(dates.first(), HomeAgendaMapper.followSelection(today, dates, true))
        assertEquals(dates.last(), HomeAgendaMapper.followSelection(today, dates, false))
        assertEquals(today, HomeAgendaMapper.followSelection(today, emptyList(), true))
    }

    @Test fun displayModeDefaultsToScheduledAndDoesNotChangeDateOrderSetting() {
        val defaults = MySettings()
        assertTrue(defaults.homeAgendaOnlyScheduled)
        val setting = ConfigCatalog.items.single { it.key == "home.agenda_only_scheduled" }
        assertEquals(defaults.copy(homeAgendaOnlyScheduled = false), setting.set(defaults, 0))
        assertEquals(1, setting.get(defaults))
    }

    private fun event(id: Long, date: LocalDate, hour: Int = 9, title: String = "日程"): Event {
        val start = date.atTime(hour, 0).toEpochSecond(ZoneOffset.UTC)
        return Event(id = id, startTS = start, endTS = start + 3600, title = title, timeZone = "UTC")
    }
}
