package com.antgskds.calendarassistant.feature.schedule.domain.course

import com.antgskds.calendarassistant.feature.schedule.data.store.reminder.ReminderPolicy
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.schedule.domain.model.EventTags
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import org.junit.Assert.*
import org.junit.Test

class CourseFeaturePolicyTest {
    private val course = Event(id = 1, startTS = 200, endTS = 300, tag = EventTags.COURSE)

    @Test fun totalSwitchAndGesturePreferenceAreIndependent() {
        val settings = MySettings(courseFeatureEnabled = false)
        assertTrue(CourseFeaturePolicy.enabled(settings))
        assertFalse(CourseFeaturePolicy.swipeEnabled(settings))
        val disabled = ConfigCatalog.items.single { it.key == "course.module_enabled" }.set(settings, 0)
        assertFalse(CourseFeaturePolicy.enabled(disabled))
        assertFalse(disabled.courseFeatureEnabled)
        assertEquals(settings.copy(courseModuleEnabled = false), disabled)
    }

    @Test fun disabledCourseNeverSchedulesButOrdinaryEventsStillDo() {
        val disabled = MySettings(courseModuleEnabled = false)
        assertFalse(CourseFeaturePolicy.allows(course, disabled))
        assertTrue(ReminderPolicy.effectiveReminders(course, disabled).isEmpty())
        val ordinary = course.copy(tag = EventTags.GENERAL)
        assertTrue(CourseFeaturePolicy.allows(ordinary, disabled))
        assertTrue(ReminderPolicy.effectiveReminders(ordinary, disabled).isNotEmpty())
    }

    @Test fun resumingKeepsDataPreferencesAndRecordsReminderCutoff() {
        val before = MySettings(courseModuleEnabled = false, courseFeatureEnabled = false, semesterStartDate = "2026-09-01")
        val resumed = CourseFeaturePolicy.onSettingsChanged(before, before.copy(courseModuleEnabled = true), 100_000)
        assertEquals(before.copy(courseModuleEnabled = true, courseRemindersResumeAtMillis = 100_000), resumed)
        assertFalse(CourseFeaturePolicy.allowsReminder(EventTags.COURSE, 99_999, resumed))
        assertTrue(CourseFeaturePolicy.allowsReminder(EventTags.COURSE, 100_000, resumed))
        assertTrue(CourseFeaturePolicy.allowsReminder(EventTags.GENERAL, 1, resumed))
    }

    @Test fun unrelatedSettingsDoNotMoveCutoffAndOffStillBlocksFutureCourses() {
        val settings = MySettings(courseRemindersResumeAtMillis = 100_000)
        assertEquals(settings, CourseFeaturePolicy.onSettingsChanged(settings, settings, 200_000))
        assertFalse(CourseFeaturePolicy.allowsReminder(EventTags.COURSE, 300_000, settings.copy(courseModuleEnabled = false)))
    }

    @Test fun oldSettingsKeepCoursesEnabledAndExistingGestureChoice() {
        assertTrue(CourseFeaturePolicy.enabled(MySettings()))
        assertTrue(CourseFeaturePolicy.swipeEnabled(MySettings()))
        assertTrue(CourseFeaturePolicy.allowsReminder(EventTags.COURSE, 1, MySettings()))
    }
}
