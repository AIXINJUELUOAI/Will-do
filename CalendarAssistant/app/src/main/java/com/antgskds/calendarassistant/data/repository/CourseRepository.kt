package com.antgskds.calendarassistant.data.repository

import android.content.Context
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.source.CourseJsonDataSource

class CourseRepository(context: Context) {
    private val courseSource = CourseJsonDataSource(context.applicationContext)

    suspend fun loadCourses(): List<Course> = courseSource.loadCourses()

    fun getAndClearCleanupInfo(): String = courseSource.getAndClearCleanupInfo()

    suspend fun saveCourses(courses: List<Course>) {
        courseSource.saveCourses(courses)
    }

    fun addCourse(current: List<Course>, course: Course): List<Course> {
        return buildList {
            addAll(current)
            add(course)
        }
    }

    fun updateCourse(current: List<Course>, course: Course): List<Course> {
        val mutable = current.toMutableList()
        val index = mutable.indexOfFirst { it.id == course.id }
        if (index != -1) {
            mutable[index] = course
        }
        return mutable
    }

    fun deleteCourse(current: List<Course>, course: Course): List<Course> {
        val mutable = current.toMutableList()
        val removed = mutable.remove(course)
        if (removed && !course.isTemp) {
            val childrenToRemove = mutable.filter { it.parentCourseId == course.id }
            mutable.removeAll(childrenToRemove)
        }
        return mutable
    }
}
