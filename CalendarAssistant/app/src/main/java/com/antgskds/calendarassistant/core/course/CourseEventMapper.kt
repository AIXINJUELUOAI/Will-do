package com.antgskds.calendarassistant.core.course

import com.antgskds.calendarassistant.core.calendar.RecurringEventUtils
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.model.TimeNode
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object CourseEventMapper {
    private const val LEGACY_COURSE_META_PREFIX = "course-meta:"
    private const val COURSE_RECURRING_META_PREFIX = "course-recurring-meta:"
    private const val COURSE_INSTANCE_META_PREFIX = "course-instance-meta:"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun isCourseTemplateEvent(event: MyEvent): Boolean {
        return event.tag == EventTags.COURSE && event.description.startsWith(LEGACY_COURSE_META_PREFIX)
    }

    fun isCourseRecurringParentEvent(event: MyEvent): Boolean {
        return event.tag == EventTags.COURSE && event.isRecurringParent && event.description.startsWith(COURSE_RECURRING_META_PREFIX)
    }

    fun isCourseDetachedInstanceEvent(event: MyEvent): Boolean {
        return event.tag == EventTags.COURSE && !event.isRecurring && event.description.startsWith(COURSE_INSTANCE_META_PREFIX)
    }

    fun normalizeTemplateEvent(event: MyEvent): MyEvent {
        if (!isCourseTemplateEvent(event)) return event
        return event.copy(
            isRecurring = false,
            isRecurringParent = false,
            recurringSeriesKey = null,
            recurringInstanceKey = null,
            parentRecurringId = null,
            excludedRecurringInstances = emptyList(),
            nextOccurrenceStartMillis = null,
            reminders = emptyList(),
            skipCalendarSync = true
        )
    }

    fun toTemplateEvents(courses: List<Course>, settings: MySettings): List<MyEvent> {
        return courses.map { toTemplateEvent(it, settings, existingParent = null) }
    }

    fun toTemplateEvent(course: Course, settings: MySettings, existingParent: MyEvent? = null): MyEvent {
        if (course.isTemp) {
            return toDetachedInstanceEvent(course, settings)
        }

        val timeNodes = parseTimeNodes(settings.timeTableJson)
        val startNode = timeNodes.find { it.index == course.startNode }
        val endNode = timeNodes.find { it.index == course.endNode }
        val semesterStart = parseSemesterStart(settings.semesterStartDate)
        val firstDate = resolveFirstOccurrenceDate(semesterStart, course.dayOfWeek, course.startWeek)
        val firstStartTime = startNode?.startTime ?: "08:00"
        val firstStartMillis = toMillis(firstDate, firstStartTime)
        val seriesKey = course.id
        val parentId = RecurringEventUtils.buildParentId(seriesKey)
        val existingExcludedDates = existingParent?.excludedRecurringInstances.orEmpty()
            .mapNotNull { key -> keyToDate(seriesKey, key) }
        val mergedExcludedDates = (existingExcludedDates + course.excludedDates).distinct()
        val mergedExcluded = excludedDatesToKeys(seriesKey, mergedExcludedDates, firstStartTime)

        val meta = CourseRecurringMeta(
            teacher = course.teacher,
            dayOfWeek = course.dayOfWeek,
            startNode = course.startNode,
            endNode = course.endNode,
            startWeek = course.startWeek,
            endWeek = course.endWeek,
            weekType = course.weekType,
            parentSeriesKey = null
        )

        return MyEvent(
            id = parentId,
            title = course.name,
            startDate = firstDate,
            endDate = firstDate,
            startTime = firstStartTime,
            endTime = endNode?.endTime ?: "09:40",
            location = course.location,
            description = COURSE_RECURRING_META_PREFIX + json.encodeToString(meta),
            color = course.color,
            reminders = emptyList(),
            tag = EventTags.COURSE,
            isRecurring = true,
            isRecurringParent = true,
            recurringSeriesKey = seriesKey,
            recurringInstanceKey = RecurringEventUtils.buildInstanceKey(seriesKey, firstStartMillis),
            parentRecurringId = null,
            excludedRecurringInstances = mergedExcluded,
            nextOccurrenceStartMillis = firstStartMillis,
            skipCalendarSync = false
        )
    }

    fun buildDetachedInstanceDescription(
        teacher: String,
        startNode: Int,
        endNode: Int,
        parentSeriesKey: String
    ): String {
        val meta = CourseRecurringMeta(
            teacher = teacher,
            dayOfWeek = 1,
            startNode = startNode,
            endNode = endNode,
            startWeek = 1,
            endWeek = 1,
            weekType = 0,
            parentSeriesKey = parentSeriesKey
        )
        return COURSE_INSTANCE_META_PREFIX + json.encodeToString(meta)
    }

    fun detachedParentSeriesKey(event: MyEvent): String? {
        val meta = parseRecurringMeta(event.description, COURSE_INSTANCE_META_PREFIX) ?: return null
        return meta.parentSeriesKey
    }

    fun extractCourses(events: List<MyEvent>, settings: MySettings): List<Course> {
        val fromRecurringParents = events
            .filter(::isCourseRecurringParentEvent)
            .mapNotNull { toCourseFromRecurringParent(it, settings) }

        val fromDetachedInstances = events
            .filter(::isCourseDetachedInstanceEvent)
            .mapNotNull { toCourseFromDetachedInstance(it, settings) }

        val fromLegacyTemplates = events
            .filter(::isCourseTemplateEvent)
            .map { normalizeTemplateEvent(it) }
            .mapNotNull { toLegacyCourse(it, settings) }

        return (fromRecurringParents + fromDetachedInstances + fromLegacyTemplates)
            .sortedBy { it.dayOfWeek * 100 + it.startNode }
    }

    private fun toLegacyCourse(event: MyEvent, settings: MySettings): Course? {
        val meta = parseMeta(event.description)
        val dayOfWeek = meta?.dayOfWeek ?: event.startDate.dayOfWeek.value
        val startNode = meta?.startNode ?: 1
        val endNode = meta?.endNode ?: startNode
        val totalWeeks = settings.totalWeeks.coerceAtLeast(1)

        return Course(
            id = event.id,
            name = event.title,
            location = event.location,
            teacher = meta?.teacher.orEmpty(),
            color = event.color,
            dayOfWeek = dayOfWeek,
            startNode = startNode,
            endNode = endNode,
            startWeek = (meta?.startWeek ?: 1).coerceAtLeast(1),
            endWeek = (meta?.endWeek ?: totalWeeks).coerceAtLeast(1),
            weekType = meta?.weekType ?: 0,
            excludedDates = meta?.excludedDates ?: emptyList(),
            isTemp = meta?.isTemp ?: false,
            parentCourseId = meta?.parentCourseId
        )
    }

    private fun toCourseFromRecurringParent(event: MyEvent, settings: MySettings): Course? {
        val meta = parseRecurringMeta(event.description, COURSE_RECURRING_META_PREFIX) ?: return null
        val seriesKey = event.recurringSeriesKey?.takeIf { it.isNotBlank() }
            ?: event.id.removePrefix("recurring_parent_")
        if (seriesKey.isBlank()) return null

        val excludedDates = event.excludedRecurringInstances
            .mapNotNull { key -> keyToDate(seriesKey, key) }
            .distinct()

        return Course(
            id = seriesKey,
            name = event.title,
            location = event.location,
            teacher = meta.teacher,
            color = event.color,
            dayOfWeek = meta.dayOfWeek.coerceIn(1, 7),
            startNode = meta.startNode,
            endNode = meta.endNode,
            startWeek = meta.startWeek.coerceAtLeast(1),
            endWeek = meta.endWeek.coerceAtLeast(meta.startWeek.coerceAtLeast(1)),
            weekType = meta.weekType,
            excludedDates = excludedDates,
            isTemp = false,
            parentCourseId = null
        )
    }

    private fun toCourseFromDetachedInstance(event: MyEvent, settings: MySettings): Course? {
        val meta = parseRecurringMeta(event.description, COURSE_INSTANCE_META_PREFIX) ?: return null
        val targetWeek = calculateTargetWeek(settings.semesterStartDate, event.startDate)
        return Course(
            id = event.id,
            name = event.title,
            location = event.location,
            teacher = meta.teacher,
            color = event.color,
            dayOfWeek = event.startDate.dayOfWeek.value,
            startNode = meta.startNode,
            endNode = meta.endNode,
            startWeek = targetWeek,
            endWeek = targetWeek,
            weekType = 0,
            excludedDates = emptyList(),
            isTemp = true,
            parentCourseId = meta.parentSeriesKey
        )
    }

    private fun parseMeta(description: String): CourseMeta? {
        if (!description.startsWith(LEGACY_COURSE_META_PREFIX)) return null
        val payload = description.removePrefix(LEGACY_COURSE_META_PREFIX)
        return runCatching { json.decodeFromString<CourseMeta>(payload) }.getOrNull()
    }

    private fun parseRecurringMeta(description: String, prefix: String): CourseRecurringMeta? {
        if (!description.startsWith(prefix)) return null
        val payload = description.removePrefix(prefix)
        return runCatching { json.decodeFromString<CourseRecurringMeta>(payload) }.getOrNull()
    }

    private fun parseSemesterStart(value: String): LocalDate {
        return resolveSemesterAnchor(value)
    }

    private fun resolveFirstOccurrenceDate(semesterStart: LocalDate, dayOfWeek: Int, startWeek: Int): LocalDate {
        val target = DayOfWeek.of(dayOfWeek.coerceIn(1, 7))
        var date = semesterStart
        while (date.dayOfWeek != target) {
            date = date.plusDays(1)
        }
        val weekOffset = (startWeek.coerceAtLeast(1) - 1) * 7L
        return date.plusDays(weekOffset)
    }

    private fun parseTimeNodes(jsonText: String): List<TimeNode> {
        if (jsonText.isBlank()) return emptyList()
        return runCatching { json.decodeFromString<List<TimeNode>>(jsonText) }.getOrElse { emptyList() }
    }

    private fun toDetachedInstanceEvent(course: Course, settings: MySettings): MyEvent {
        val timeNodes = parseTimeNodes(settings.timeTableJson)
        val startNode = timeNodes.find { it.index == course.startNode }
        val endNode = timeNodes.find { it.index == course.endNode }
        val semesterStart = parseSemesterStart(settings.semesterStartDate)
        val targetDate = resolveFirstOccurrenceDate(semesterStart, course.dayOfWeek, course.startWeek)
        val parentSeriesKey = course.parentCourseId.orEmpty()

        return MyEvent(
            id = course.id,
            title = course.name,
            startDate = targetDate,
            endDate = targetDate,
            startTime = startNode?.startTime ?: "08:00",
            endTime = endNode?.endTime ?: "09:40",
            location = course.location,
            description = buildDetachedInstanceDescription(
                teacher = course.teacher,
                startNode = course.startNode,
                endNode = course.endNode,
                parentSeriesKey = parentSeriesKey
            ),
            color = course.color,
            reminders = emptyList(),
            tag = EventTags.COURSE,
            skipCalendarSync = false
        )
    }

    private fun toMillis(date: LocalDate, time: String): Long {
        return runCatching {
            java.time.LocalDateTime.of(date, java.time.LocalTime.parse(time))
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrElse { System.currentTimeMillis() }
    }

    private fun excludedDatesToKeys(seriesKey: String, excludedDates: List<String>, startTime: String): List<String> {
        return excludedDates.mapNotNull { dateStr ->
            runCatching { LocalDate.parse(dateStr) }.getOrNull()?.let { date ->
                val startMillis = toMillis(date, startTime)
                RecurringEventUtils.buildInstanceKey(seriesKey, startMillis)
            }
        }
    }

    private fun keyToDate(seriesKey: String, key: String): String? {
        val prefix = "${seriesKey}_"
        if (!key.startsWith(prefix)) return null
        val millis = key.removePrefix(prefix).toLongOrNull() ?: return null
        return runCatching {
            java.time.Instant.ofEpochMilli(millis)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
                .toString()
        }.getOrNull()
    }

    private fun calculateTargetWeek(semesterStartDate: String, targetDate: LocalDate): Int {
        val semesterStart = parseSemesterStart(semesterStartDate)
        val daysDiff = java.time.temporal.ChronoUnit.DAYS.between(semesterStart, targetDate)
        return (daysDiff / 7).toInt() + 1
    }

    @Serializable
    private data class CourseMeta(
        val teacher: String = "",
        val dayOfWeek: Int,
        val startNode: Int,
        val endNode: Int,
        val startWeek: Int,
        val endWeek: Int,
        val weekType: Int,
        val excludedDates: List<String> = emptyList(),
        val isTemp: Boolean = false,
        val parentCourseId: String? = null
    )

    @Serializable
    private data class CourseRecurringMeta(
        val teacher: String = "",
        val dayOfWeek: Int,
        val startNode: Int,
        val endNode: Int,
        val startWeek: Int,
        val endWeek: Int,
        val weekType: Int,
        val parentSeriesKey: String? = null
    )
}
