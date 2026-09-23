package com.antgskds.calendarassistant

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import com.antgskds.calendarassistant.app.ui.theme.material.background.LocalAppBackgroundStyleEnabled
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.antgskds.calendarassistant.feature.schedule.domain.course.CourseEventMapper
import com.antgskds.calendarassistant.feature.schedule.domain.course.CourseMeta
import com.antgskds.calendarassistant.feature.schedule.domain.model.EventTags
import com.antgskds.calendarassistant.feature.schedule.presentation.model.ScheduleDisplayItem
import com.antgskds.calendarassistant.feature.schedule.ui.render.material.MaterialScheduleView
import com.antgskds.calendarassistant.shared.ui.material.component.LocalAppPageBottomPadding
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class CourseGridLayoutRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun tallGridAlignsAndLastRowScrollsAboveSystemInset() = verifyGrid(12)
    @Test fun shortGridFillsViewportAndStillScrollsAboveSystemInset() = verifyGrid(6)

    @Test fun phoneWallpaperKeepsCourseColorAndDoesNotAddGrid() {
        val monday = LocalDate.now().with(java.time.DayOfWeek.MONDAY)
        val start = monday.atTime(9, 0).atZone(ZoneId.systemDefault()).toEpochSecond()
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalAppBackgroundStyleEnabled provides true, LocalDensity provides Density(1f)) {
                    Box(Modifier.size(350.dp, 600.dp).background(Color.Blue).testTag("wallpaper_course")) {
                        MaterialScheduleView(
                            items = listOf(ScheduleDisplayItem(
                                stableKey = "colored", title = "课程", startTS = start, endTS = start + 3600,
                                tag = EventTags.COURSE, color = 0xFFB3261E.toInt(),
                                description = CourseEventMapper.buildParentDescription(CourseMeta(startNode = 1, endNode = 2)),
                                action = ScheduleDisplayItem.ActionTarget.Single(1),
                            )),
                            semesterStartDateStr = monday.toString(), totalWeeks = 20, maxNodes = 10,
                            selectedDate = monday,
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("course_cell_0_0", useUnmergedTree = true).assertDoesNotExist()
        val pixels = compose.onNodeWithTag("course_colored", useUnmergedTree = true).captureToImage().toPixelMap()
        assertEquals(Color(0xFFB3261E), pixels[pixels.width / 2, pixels.height / 2])
        val background = compose.onNodeWithTag("wallpaper_course").captureToImage().toPixelMap()
        assertEquals(Color.Blue, background[background.width - 4, background.height / 2])
    }

    private fun verifyGrid(nodes: Int) {
        val monday = LocalDate.of(2026, 9, 21)
        fun course(key: String, day: Int, start: Int, end: Int): ScheduleDisplayItem {
            val timestamp = monday.plusDays(day.toLong()).atTime(8, 0)
                .atZone(ZoneId.systemDefault()).toEpochSecond()
            return ScheduleDisplayItem(
                stableKey = key, title = key, startTS = timestamp, endTS = timestamp + 3600,
                tag = EventTags.COURSE,
                description = CourseEventMapper.buildParentDescription(CourseMeta(startNode = start, endNode = end)),
                action = ScheduleDisplayItem.ActionTarget.Single(day.toLong()),
            )
        }
        compose.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalDensity provides Density(1.25f),
                    LocalAppPageBottomPadding provides 24.dp,
                ) {
                    Box(Modifier.size(319.dp, 360.dp).testTag("workspace")) {
                        MaterialScheduleView(
                            items = listOf(course("first", 0, 2, 4), course("last", 6, nodes - 1, nodes)),
                            semesterStartDateStr = monday.toString(), totalWeeks = 20, maxNodes = nodes,
                            selectedDate = monday, embeddedInCalendarWorkspace = true,
                        )
                    }
                }
            }
        }
        fun bounds(tag: String): Rect = compose.onNodeWithTag(tag, useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        fun assertSpan(card: String, first: String, last: String) {
            val actual = bounds(card)
            val topCell = bounds(first)
            val bottomCell = bounds(last)
            assertEquals(topCell.left, actual.left, 0.01f)
            assertEquals(topCell.top, actual.top, 0.01f)
            assertEquals(bottomCell.right, actual.right, 0.01f)
            assertEquals(bottomCell.bottom, actual.bottom, 0.01f)
        }
        assertSpan("course_first", "course_cell_1_0", "course_cell_3_0")
        assertEquals(bounds("workspace").bottom, bounds("course_viewport").bottom, 0.01f)
        compose.onNodeWithTag("course_viewport").performSemanticsAction(SemanticsActions.ScrollBy) {
            it(0f, 100_000f)
        }
        compose.waitForIdle()
        assertSpan("course_last", "course_cell_${nodes - 2}_6", "course_cell_${nodes - 1}_6")
        assertEquals(bounds("course_viewport").bottom - 30f, bounds("course_last").bottom, 0.01f)
    }
}
