package com.antgskds.calendarassistant.core.course

import com.antgskds.calendarassistant.calendar.helpers.REMINDER_OFF
import com.antgskds.calendarassistant.calendar.helpers.SOURCE_SIMPLE_CALENDAR
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.EventTags
import com.antgskds.calendarassistant.calendar.models.toEpochSeconds
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.MySettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

object CourseEventMapper {
    const val COURSE_HEADER = "【课程】"
    private const val COURSE_ASCII_PREFIX = "[course]"
    private const val LEGACY_RECURRING_PREFIX = "course-recurring-meta:"
    private const val LEGACY_INSTANCE_PREFIX = "course-instance-meta:"
    private const val LEGACY_TEMPLATE_PREFIX = "course-meta:"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
    }
    private val exdateFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
        .withZone(ZoneOffset.UTC)

    fun isCourseEvent(event: Event): Boolean = event.tag == EventTags.COURSE
    fun isCourseParent(event: Event): Boolean = isCourseEvent(event) && event.isRecurring && event.parentId == 0L
    fun isCourseException(event: Event): Boolean = isCourseEvent(event) && event.parentId != 0L

    fun newCourseId(course: Course): String {
        if (course.id.isNotBlank()) return course.id
        return UUID.randomUUID().toString()
    }

    fun stableImportId(
        name: String,
        teacher: String,
        room: String,
        dayOfWeek: Int,
        startNode: Int,
        endNode: Int,
        startWeek: Int,
        endWeek: Int,
        weekType: Int
    ): String {
        val raw = listOf(name, teacher, room, dayOfWeek, startNode, endNode, startWeek, endWeek, weekType)
            .joinToString("|") { it.toString().trim() }
        val digest = MessageDigest.getInstance("SHA-1").digest(raw.toByteArray())
        return digest.take(10).joinToString("") { "%02x".format(it) }
    }

    fun buildParentDescription(meta: CourseMeta): String = COURSE_HEADER + json.encodeToString(meta.copy(v = 1))

    fun buildDetachedInstanceDescription(
        parentMeta: CourseMeta,
        startNode: Int,
        endNode: Int,
        originalOccurrenceTs: Long,
        originalWeek: Int,
        originalDate: LocalDate
    ): String {
        val meta = parentMeta.copy(
            startNode = startNode,
            endNode = endNode,
            parentCourseUid = parentMeta.uid,
            originalOccurrenceTs = originalOccurrenceTs,
            originalWeek = originalWeek,
            originalDate = originalDate.toString()
        )
        return COURSE_HEADER + json.encodeToString(meta)
    }

    fun parseMeta(description: String?): CourseMeta? {
        val text = description?.trim().orEmpty()
        if (text.isBlank()) return null
        val payload = when {
            text.startsWith(COURSE_HEADER) -> text.removePrefix(COURSE_HEADER).trim()
            text.startsWith(COURSE_ASCII_PREFIX) -> text.removePrefix(COURSE_ASCII_PREFIX).trim()
            text.startsWith(LEGACY_RECURRING_PREFIX) -> text.removePrefix(LEGACY_RECURRING_PREFIX).trim()
            text.startsWith(LEGACY_INSTANCE_PREFIX) -> text.removePrefix(LEGACY_INSTANCE_PREFIX).trim()
            text.startsWith(LEGACY_TEMPLATE_PREFIX) -> text.removePrefix(LEGACY_TEMPLATE_PREFIX).trim()
            else -> return null
        }
        return runCatching { json.decodeFromString<CourseMeta>(payload) }.getOrNull()
    }

    fun displayDescription(description: String?, location: String = ""): String {
        val meta = parseMeta(description) ?: return description.orEmpty()
        return buildString {
            if (meta.teacher.isNotBlank()) append(meta.teacher)
            if (location.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(location)
            }
            if (meta.startNode > 0 && meta.endNode >= meta.startNode) {
                if (isNotEmpty()) append(" · ")
                append("第${meta.startNode}-${meta.endNode}节")
            }
        }
    }

    fun toParentEvent(
        course: Course,
        settings: MySettings,
        existingParent: Event? = null,
        additionalExcludedOccurrenceTs: List<Long> = emptyList()
    ): Event {
        val courseId = newCourseId(course)
        val normalized = course.copy(
            id = courseId,
            dayOfWeek = course.dayOfWeek.coerceIn(1, 7),
            startNode = course.startNode.coerceAtLeast(1),
            endNode = course.endNode.coerceAtLeast(course.startNode.coerceAtLeast(1)),
            startWeek = course.startWeek.coerceAtLeast(1),
            endWeek = course.endWeek.coerceAtLeast(course.startWeek.coerceAtLeast(1)),
            weekType = course.weekType.coerceIn(0, 2)
        )
        val firstWeek = firstActiveWeek(normalized.startWeek, normalized.endWeek, normalized.weekType)
            ?: normalized.startWeek
        val firstDate = resolveOccurrenceDate(settings.semesterStartDate, normalized.dayOfWeek, firstWeek)
        val (startTs, endTs) = mapNodesToEpochRange(
            settings = settings,
            date = firstDate,
            startNode = normalized.startNode,
            endNode = normalized.endNode
        )
        val occurrenceCount = occurrenceCount(normalized.startWeek, normalized.endWeek, normalized.weekType).coerceAtLeast(1)
        val interval = if (normalized.weekType == 0) 1 else 2
        val meta = CourseMeta(
            uid = courseId,
            teacher = normalized.teacher,
            dayOfWeek = normalized.dayOfWeek,
            startNode = normalized.startNode,
            endNode = normalized.endNode,
            startWeek = normalized.startWeek,
            endWeek = normalized.endWeek,
            weekType = normalized.weekType
        )
        val excludedDateOccurrenceTs = normalized.excludedDates.mapNotNull { dateText ->
            runCatching { LocalDate.parse(dateText) }.getOrNull()?.let { date ->
                mapNodesToEpochRange(settings, date, normalized.startNode, normalized.endNode).first
            }
        }
        val exdates = (
            existingParent?.exdates.orEmpty() +
                excludedDateOccurrenceTs.map(::formatExdateUtc) +
                additionalExcludedOccurrenceTs.map(::formatExdateUtc)
            ).distinct()

        return Event(
            id = existingParent?.id,
            startTS = startTs,
            endTS = endTs,
            title = normalized.name,
            location = normalized.location,
            description = buildParentDescription(meta),
            reminder1Minutes = existingParent?.reminder1Minutes ?: REMINDER_OFF,
            reminder2Minutes = existingParent?.reminder2Minutes ?: REMINDER_OFF,
            reminder3Minutes = existingParent?.reminder3Minutes ?: REMINDER_OFF,
            rrule = "FREQ=WEEKLY;INTERVAL=$interval;COUNT=$occurrenceCount",
            exdates = exdates,
            importId = existingParent?.importId ?: "",
            timeZone = existingParent?.timeZone ?: ZoneId.systemDefault().id,
            parentId = 0L,
            lastUpdated = existingParent?.lastUpdated ?: 0L,
            source = existingParent?.source ?: SOURCE_SIMPLE_CALENDAR,
            color = normalized.color,
            tag = EventTags.COURSE,
            archivedAt = existingParent?.archivedAt
        )
    }

    fun toCourse(parent: Event, settings: MySettings): Course? {
        if (!isCourseParent(parent)) return null
        val meta = parseMeta(parent.description) ?: return null
        return Course(
            id = meta.uid.ifBlank { parent.id?.toString().orEmpty() },
            name = parent.title,
            location = parent.location,
            teacher = meta.teacher,
            color = parent.color,
            dayOfWeek = meta.dayOfWeek.coerceIn(1, 7),
            startNode = meta.startNode.coerceAtLeast(1),
            endNode = meta.endNode.coerceAtLeast(meta.startNode.coerceAtLeast(1)),
            startWeek = meta.startWeek.coerceAtLeast(1),
            endWeek = meta.endWeek.coerceAtLeast(meta.startWeek.coerceAtLeast(1)),
            weekType = meta.weekType.coerceIn(0, 2),
            excludedDates = parent.exdates.mapNotNull { exdateToLocalDate(it) },
            isTemp = false,
            parentCourseId = null
        )
    }

    fun extractParentCourses(events: List<Event>, settings: MySettings): List<Course> {
        return events.mapNotNull { toCourse(it, settings) }
            .sortedBy { it.dayOfWeek * 100 + it.startNode }
    }

    fun findParentByCourseId(events: List<Event>, courseId: String): Event? {
        return events.firstOrNull { event ->
            isCourseParent(event) && parseMeta(event.description)?.uid == courseId
        }
    }

    fun childOriginalWeek(child: Event, settings: MySettings): Int? {
        val meta = parseMeta(child.description)
        meta?.originalWeek?.takeIf { it > 0 }?.let { return it }
        val originalTs = meta?.originalOccurrenceTs?.takeIf { it > 0 } ?: child.startTS
        val originalDate = Instant.ofEpochSecond(originalTs).atZone(ZoneId.systemDefault()).toLocalDate()
        return calculateSemesterWeek(settings.semesterStartDate, originalDate).takeIf { it > 0 }
    }

    fun occurrenceTsForWeek(course: Course, settings: MySettings, week: Int): Long? {
        if (week !in course.startWeek..course.endWeek) return null
        if (course.weekType == 1 && week % 2 == 0) return null
        if (course.weekType == 2 && week % 2 != 0) return null
        val date = resolveOccurrenceDate(settings.semesterStartDate, course.dayOfWeek, week)
        return toEpochSeconds(date, nodeStartTime(settings, course.startNode))
    }

    fun mapNodesToEpochRange(
        settings: MySettings,
        date: LocalDate,
        startNode: Int,
        endNode: Int
    ): Pair<Long, Long> {
        val safeStartNode = startNode.coerceAtLeast(1)
        val safeEndNode = endNode.coerceAtLeast(safeStartNode)
        val startTime = nodeStartTime(settings, safeStartNode)
        val endTime = nodeEndTime(settings, safeEndNode, startTime)
        val startTs = toEpochSeconds(date, startTime)
        val endTs = toEpochSeconds(date, endTime).let { if (it > startTs) it else startTs + 45 * 60 }
        return startTs to endTs
    }

    fun resolveOccurrenceDate(semesterStartDate: String?, dayOfWeek: Int, week: Int): LocalDate {
        val semesterStart = resolveSemesterAnchor(semesterStartDate)
        val target = DayOfWeek.of(dayOfWeek.coerceIn(1, 7))
        var date = semesterStart
        while (date.dayOfWeek != target) {
            date = date.plusDays(1)
        }
        return date.plusWeeks((week.coerceAtLeast(1) - 1).toLong())
    }

    fun nodeStartTime(settings: MySettings, nodeIndex: Int): String {
        val nodes = resolveNodes(settings)
        return nodes.firstOrNull { it.index == nodeIndex }?.startTime ?: "08:00"
    }

    fun nodeEndTime(settings: MySettings, nodeIndex: Int, fallbackStart: String = "08:00"): String {
        val nodes = resolveNodes(settings)
        return nodes.firstOrNull { it.index == nodeIndex }?.endTime ?: runCatching {
            LocalTime.parse(fallbackStart).plusMinutes(TimeTableLayoutUtils.DEFAULT_COURSE_DURATION_MINUTES.toLong()).toString()
        }.getOrDefault("09:40")
    }

    fun formatExdateUtc(occurrenceTs: Long): String = exdateFormatter.format(Instant.ofEpochSecond(occurrenceTs))

    private fun exdateToLocalDate(exdate: String): String? {
        return runCatching {
            Instant.from(exdateFormatter.parse(exdate.trim()))
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
                .toString()
        }.getOrNull()
    }

    private fun resolveNodes(settings: MySettings) =
        TimeTableLayoutUtils.parseNodes(settings.timeTableJson).takeIf { it.isNotEmpty() }
            ?: TimeTableLayoutUtils.generateNodes(TimeTableLayoutUtils.defaultConfig())

    private fun firstActiveWeek(startWeek: Int, endWeek: Int, weekType: Int): Int? {
        return (startWeek..endWeek).firstOrNull { week ->
            weekType == 0 || (weekType == 1 && week % 2 != 0) || (weekType == 2 && week % 2 == 0)
        }
    }

    private fun occurrenceCount(startWeek: Int, endWeek: Int, weekType: Int): Int {
        return (startWeek..endWeek).count { week ->
            weekType == 0 || (weekType == 1 && week % 2 != 0) || (weekType == 2 && week % 2 == 0)
        }
    }
}

@Serializable
data class CourseMeta(
    val v: Int = 1,
    val uid: String = "",
    val teacher: String = "",
    val dayOfWeek: Int = 1,
    val startNode: Int = 1,
    val endNode: Int = 1,
    val startWeek: Int = 1,
    val endWeek: Int = 1,
    val weekType: Int = 0,
    val parentCourseUid: String? = null,
    val originalOccurrenceTs: Long? = null,
    val originalWeek: Int? = null,
    val originalDate: String? = null
)
