package com.antgskds.calendarassistant

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.antgskds.calendarassistant.calendar.data.EventsDatabase
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.core.center.CalendarCenter
import com.antgskds.calendarassistant.core.course.CourseEventMapper
import com.antgskds.calendarassistant.core.migration.LegacyDataMigrationCoordinator
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.repository.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class LegacyMigrationInstrumentedTest {
    @Test
    fun manualV3ImportIsIdempotent() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dao = EventsDatabase.getInstance(context).eventsDao()
        val title = "instrumented-v3-import-idempotent"
        val importId = "instrumented-v3-import-idempotent-001"
        val coordinator = LegacyDataMigrationCoordinator(
            context = context,
            calendarCenter = CalendarCenter.getInstance(context),
            settingsRepository = SettingsRepository(context)
        )

        fun cleanup() {
            (dao.getAllEventsOrTasks() + dao.getArchivedEvents())
                .filter { it.title == title || it.importId == importId }
                .mapNotNull { it.id }
                .forEach { dao.deleteEvent(it) }
        }

        cleanup()
        try {
            val backupJson = """
                {
                  "version": 3,
                  "events": [
                    {
                      "startTS": 1900000000,
                      "endTS": 1900003600,
                      "title": "$title",
                      "location": "instrumented-room",
                      "description": "manual import idempotent test",
                      "reminder1Minutes": 15,
                      "rrule": "",
                      "exdates": [],
                      "importId": "$importId",
                      "timeZone": "Asia/Shanghai",
                      "lastUpdated": 1900000000,
                      "source": "instrumented-test",
                      "color": -16776961,
                      "state": 0,
                      "tag": "general"
                    }
                  ],
                  "archivedEvents": []
                }
            """.trimIndent()

            val first = coordinator.importEventsData(backupJson).getOrThrow()
            val afterFirst = (dao.getAllEventsOrTasks() + dao.getArchivedEvents())
                .count { it.title == title || it.importId == importId }

            val second = coordinator.importEventsData(backupJson).getOrThrow()
            val afterSecond = (dao.getAllEventsOrTasks() + dao.getArchivedEvents())
                .count { it.title == title || it.importId == importId }

            assertEquals(1, first.successCount)
            assertEquals(1, afterFirst)
            assertEquals(0, second.successCount)
            assertTrue(second.skippedCount >= 1)
            assertEquals(1, afterSecond)
        } finally {
            cleanup()
        }
    }

    @Test
    fun courseImportUsesTimetableMapping() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dao = EventsDatabase.getInstance(context).eventsDao()
        val title = "instrumented-course-timetable"
        val settingsRepository = SettingsRepository(context)
        val originalSettings = settingsRepository.loadSettings()
        val coordinator = LegacyDataMigrationCoordinator(
            context = context,
            calendarCenter = CalendarCenter.getInstance(context),
            settingsRepository = settingsRepository
        )

        fun cleanup() {
            (dao.getAllEventsOrTasks() + dao.getArchivedEvents())
                .filter { it.title == title }
                .mapNotNull { it.id }
                .forEach { dao.deleteEvent(it) }
        }

        cleanup()
        try {
            settingsRepository.saveSettings(
                originalSettings.copy(
                    semesterStartDate = "2026-04-20",
                    totalWeeks = 20,
                    timeTableJson = """
                        [
                          {"index":1,"startTime":"08:30","endTime":"09:15","period":"morning"},
                          {"index":2,"startTime":"09:25","endTime":"10:10","period":"morning"}
                        ]
                    """.trimIndent(),
                    timeTableConfigJson = ""
                )
            )

            val coursesJson = """
                [
                  {
                    "id": "instrumented-course-timetable-001",
                    "name": "$title",
                    "location": "Room-A101",
                    "teacher": "Teacher-A",
                    "color": -15584170,
                    "dayOfWeek": 6,
                    "startNode": 1,
                    "endNode": 2,
                    "startWeek": 1,
                    "endWeek": 1,
                    "weekType": 0
                  }
                ]
            """.trimIndent()

            val result = coordinator.importEventsData(coursesJson).getOrThrow()
            val event = (dao.getAllEventsOrTasks() + dao.getArchivedEvents()).single { it.title == title }
            val zone = ZoneId.of("Asia/Shanghai")
            val expectedStart = LocalDateTime.of(2026, 4, 25, 8, 30).atZone(zone).toEpochSecond()
            val expectedEnd = LocalDateTime.of(2026, 4, 25, 10, 10).atZone(zone).toEpochSecond()

            assertEquals(1, result.successCount)
            assertEquals(EventTags.COURSE, event.tag)
            assertEquals(expectedStart, event.startTS)
            assertEquals(expectedEnd, event.endTS)
            assertEquals("FREQ=WEEKLY;INTERVAL=1;COUNT=1", event.rrule)
            assertTrue(event.description.startsWith("【课程】"))
        } finally {
            cleanup()
            settingsRepository.saveSettings(originalSettings)
        }
    }

    @Test
    fun courseParentCanBeRebasedWhenTimetableChanges() {
        val oldSettings = MySettings(
            semesterStartDate = "2026-04-20",
            timeTableJson = """
                [
                  {"index":1,"startTime":"08:30","endTime":"09:15","period":"morning"},
                  {"index":2,"startTime":"09:25","endTime":"10:10","period":"morning"}
                ]
            """.trimIndent()
        )
        val newSettings = oldSettings.copy(
            timeTableJson = """
                [
                  {"index":1,"startTime":"09:00","endTime":"09:45","period":"morning"},
                  {"index":2,"startTime":"09:55","endTime":"10:40","period":"morning"}
                ]
            """.trimIndent()
        )
        val course = Course(
            id = "instrumented-course-rebase",
            name = "instrumented-course-rebase",
            color = -15584170,
            dayOfWeek = 6,
            startNode = 1,
            endNode = 2,
            startWeek = 1,
            endWeek = 1,
            excludedDates = listOf("2026-04-25")
        )
        val oldParent = CourseEventMapper.toParentEvent(course, oldSettings)
        val rebasedCourse = CourseEventMapper.toCourse(oldParent, oldSettings)!!
        val newParent = CourseEventMapper.toParentEvent(
            course = rebasedCourse,
            settings = newSettings,
            existingParent = oldParent.copy(id = 42L, exdates = emptyList())
        )
        val zone = ZoneId.of("Asia/Shanghai")
        val expectedStart = LocalDateTime.of(2026, 4, 25, 9, 0).atZone(zone).toEpochSecond()
        val expectedEnd = LocalDateTime.of(2026, 4, 25, 10, 40).atZone(zone).toEpochSecond()

        assertEquals(expectedStart, newParent.startTS)
        assertEquals(expectedEnd, newParent.endTS)
        assertEquals(listOf("20260425T010000Z"), newParent.exdates)
    }
}
