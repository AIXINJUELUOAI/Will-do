package com.antgskds.calendarassistant.feature.schedule.domain.course

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class CourseSingleTeacherTest {
    @Test fun teacherOverrideSurvivesSerializationAndKeepsOriginalCourseUnchanged() {
        val parent = CourseMeta(uid = "course", teacher = "原老师", startNode = 1, endNode = 2)
        val description = CourseEventMapper.buildDetachedInstanceDescription(
            parentMeta = parent, teacher = "代课老师", startNode = 3, endNode = 4,
            originalOccurrenceTs = 1_791_000_000L, originalWeek = 6, originalDate = LocalDate.of(2026, 10, 6),
        )
        val single = requireNotNull(CourseEventMapper.parseMeta(description))
        assertEquals("代课老师", single.teacher)
        assertEquals("course", single.parentCourseUid)
        assertEquals("2026-10-06", single.originalDate)
        assertEquals(3, single.startNode)
        assertEquals("原老师", parent.teacher)
        assertEquals("代课老师 · 教室 · 第3-4节", CourseEventMapper.displayDescription(description, "教室"))
    }
}
