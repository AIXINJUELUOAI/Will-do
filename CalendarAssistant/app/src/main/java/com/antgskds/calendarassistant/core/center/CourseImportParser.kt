package com.antgskds.calendarassistant.core.center

import android.graphics.Color
import com.antgskds.calendarassistant.core.course.CourseEventMapper
import com.antgskds.calendarassistant.core.course.TimeTableLayoutUtils
import com.antgskds.calendarassistant.data.model.Course
import com.antgskds.calendarassistant.data.model.TimeNode
import com.antgskds.calendarassistant.data.model.external.wakeup.WakeUpCourseBaseDTO
import com.antgskds.calendarassistant.data.model.external.wakeup.WakeUpScheduleDTO
import com.antgskds.calendarassistant.data.model.external.wakeup.WakeUpSettingsDTO
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

object CourseImportParser {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        isLenient = true
        prettyPrint = true
    }
    private val compactJson = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }
    private val icsDateTimeSeconds = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val icsDateTimeMinutes = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm")
    private val icsDate = DateTimeFormatter.ofPattern("yyyyMMdd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun parseExternalContent(content: String): ParsedCourseImport {
        val trimmed = content.trim()
        if (trimmed.contains("BEGIN:VCALENDAR", ignoreCase = true) && trimmed.contains("BEGIN:VEVENT", ignoreCase = true)) {
            return parseIcs(trimmed)
        }

        return parseWakeUpFile(trimmed)
    }

    fun parseWakeUpShareData(data: String): ParsedCourseImport {
        val segments = data.lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        if (segments.size < 5) error("WakeUp 分享数据格式不完整")

        val timetableInfo = runCatching { compactJson.decodeFromString<JsonObject>(segments[0]) }.getOrNull()
        val lessonTimes = runCatching { compactJson.decodeFromString<JsonArray>(segments[1]) }.getOrNull().orEmpty()
        val configInfo = compactJson.decodeFromString<JsonObject>(segments[2])
        val courseBases = compactJson.decodeFromString<JsonArray>(segments[3])
        val schedules = compactJson.decodeFromString<JsonArray>(segments[4])

        val baseMap = courseBases.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj.intValue("id") ?: return@mapNotNull null
            id to WakeUpBase(
                id = id,
                courseName = obj.stringValue("courseName").orEmpty(),
                teacher = obj.stringValue("teacher").orEmpty(),
                color = obj.stringValue("color").orEmpty()
            )
        }.toMap()

        val timeNodes = parseWakeUpLessonTimes(lessonTimes)
        val totalWeeks = listOfNotNull(
            configInfo.intValue("maxWeek"),
            timetableInfo?.intValue("maxWeek"),
            schedules.mapNotNull { it.jsonObject.intValue("endWeek") }.maxOrNull()
        ).firstOrNull { it > 0 }
        val semesterStartDate = normalizeDateText(configInfo.stringValue("startDate") ?: timetableInfo?.stringValue("startDate"))
        val courses = schedules.mapIndexedNotNull { index, element ->
            val obj = element.jsonObject
            val courseId = obj.intValue("id") ?: return@mapIndexedNotNull null
            val base = baseMap[courseId] ?: return@mapIndexedNotNull null
            val day = obj.intValue("day") ?: return@mapIndexedNotNull null
            val startNode = obj.intValue("startNode") ?: return@mapIndexedNotNull null
            val step = obj.intValue("step") ?: return@mapIndexedNotNull null
            val startWeek = obj.intValue("startWeek") ?: return@mapIndexedNotNull null
            val endWeek = obj.intValue("endWeek") ?: return@mapIndexedNotNull null
            val type = obj.intValue("type") ?: 0
            val teacher = obj.stringValue("teacher").orEmpty().ifBlank { base.teacher }
            val room = obj.stringValue("room").orEmpty()
            buildCourse(
                name = base.courseName,
                teacher = teacher,
                room = room,
                colorText = base.color,
                fallbackColorIndex = index,
                dayOfWeek = day,
                startNode = startNode,
                endNode = startNode + step - 1,
                startWeek = startWeek,
                endWeek = endWeek,
                weekType = type
            )
        }
        if (courses.isEmpty()) error("未解析到 WakeUp 分享课程")

        return buildParsedImport(
            sourceType = CourseImportSourceType.WAKEUP_SHARE,
            sourceName = "WakeUp 分享口令",
            courses = courses,
            semesterStartDate = semesterStartDate,
            totalWeeks = totalWeeks ?: courses.maxOfOrNull { it.endWeek },
            timeNodes = timeNodes
        )
    }

    fun extractWakeUpKey(text: String): String? {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return null

        val patterns = listOf(
            """分享口令为[「『"]?([A-Za-z0-9]+)[」』"]?""".toRegex(),
            "key=([A-Za-z0-9]+)".toRegex(),
            "口令[：:]\\s*([A-Za-z0-9]+)".toRegex(),
            "([A-Fa-f0-9]{6,})".toRegex()
        )
        patterns.forEach { pattern ->
            pattern.find(trimmed)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return trimmed.takeIf { it.matches("[A-Za-z0-9]{6,}".toRegex()) }
    }

    private fun parseWakeUpFile(content: String): ParsedCourseImport {
        val lines = content.lines().map { it.trim() }.filter { it.isNotBlank() }
        val baseMap = mutableMapOf<Int, WakeUpBase>()
        val schedules = mutableListOf<WakeUpScheduleDTO>()
        val timeNodes = mutableListOf<TimeNode>()
        var semesterStartDate: String? = null
        var totalWeeks: Int? = null

        lines.forEach { line ->
            when {
                line.startsWith("{") && line.contains("\"startDate\"") -> {
                    runCatching { compactJson.decodeFromString<WakeUpSettingsDTO>(line) }.getOrNull()?.let { settings ->
                        semesterStartDate = normalizeDateText(settings.startDate) ?: semesterStartDate
                        totalWeeks = settings.maxWeek.takeIf { it > 0 } ?: totalWeeks
                    } ?: runCatching { compactJson.decodeFromString<JsonObject>(line) }.getOrNull()?.let { obj ->
                        semesterStartDate = normalizeDateText(obj.stringValue("startDate")) ?: semesterStartDate
                        totalWeeks = obj.intValue("maxWeek")?.takeIf { it > 0 } ?: totalWeeks
                    }
                }

                line.startsWith("[") && line.contains("\"courseName\"") -> {
                    runCatching { compactJson.decodeFromString<List<WakeUpCourseBaseDTO>>(line) }.getOrNull()
                        ?.forEach { base ->
                            baseMap[base.id] = WakeUpBase(
                                id = base.id,
                                courseName = base.courseName,
                                teacher = base.teacher,
                                color = base.color
                            )
                        }
                }

                line.startsWith("[") && line.contains("\"startNode\"") && line.contains("\"step\"") && line.contains("\"day\"") -> {
                    schedules += runCatching { compactJson.decodeFromString<List<WakeUpScheduleDTO>>(line) }.getOrNull().orEmpty()
                }

                line.startsWith("[") && line.contains("\"startTime\"") && line.contains("\"endTime\"") -> {
                    runCatching { compactJson.decodeFromString<JsonArray>(line) }.getOrNull()?.let { array ->
                        timeNodes += parseWakeUpLessonTimes(array)
                    }
                }
            }
        }

        val courses = schedules.mapIndexedNotNull { index, schedule ->
            val base = baseMap[schedule.id] ?: return@mapIndexedNotNull null
            val teacher = schedule.teacher.ifBlank { base.teacher }
            buildCourse(
                name = base.courseName,
                teacher = teacher,
                room = schedule.room,
                colorText = base.color,
                fallbackColorIndex = index,
                dayOfWeek = schedule.day,
                startNode = schedule.startNode,
                endNode = schedule.startNode + schedule.step - 1,
                startWeek = schedule.startWeek,
                endWeek = schedule.endWeek,
                weekType = schedule.type
            )
        }
        if (courses.isEmpty()) error("未解析到有效外部课表")

        return buildParsedImport(
            sourceType = CourseImportSourceType.WAKEUP_FILE,
            sourceName = "WakeUp 文件",
            courses = courses,
            semesterStartDate = semesterStartDate,
            totalWeeks = totalWeeks ?: courses.maxOfOrNull { it.endWeek },
            timeNodes = timeNodes
        )
    }

    private fun parseIcs(content: String): ParsedCourseImport {
        val events = extractIcsEvents(unfoldIcsLines(content))
        if (events.isEmpty()) error("未解析到 ICS 课程事件")

        val rawEvents = events.mapNotNull(::parseIcsRawEvent)
        if (rawEvents.isEmpty()) error("未解析到有效 ICS 课程")

        val semesterStart = rawEvents.minOf { it.start.toLocalDate() }.let { earliest ->
            earliest.minusDays((earliest.dayOfWeek.value - 1).toLong())
        }
        val courses = rawEvents.mapIndexedNotNull { index, raw ->
            val startDate = raw.start.toLocalDate()
            val startWeek = weekForDate(semesterStart, startDate)
            val interval = raw.rrule["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            val count = raw.rrule["COUNT"]?.toIntOrNull()?.coerceAtLeast(1)
            val untilDate = raw.rrule["UNTIL"]?.let { until ->
                parseIcsDateTime(until, emptyMap(), raw.start.zone)?.toLocalDate()
            }
            val endWeek = when {
                count != null -> startWeek + (count - 1) * interval
                untilDate != null -> weekForDate(semesterStart, untilDate).coerceAtLeast(startWeek)
                else -> startWeek
            }
            val weekType = if (interval == 2) {
                if (startWeek % 2 == 0) 2 else 1
            } else {
                0
            }
            buildCourse(
                name = raw.title,
                teacher = raw.teacher,
                room = raw.room,
                colorText = "",
                fallbackColorIndex = index,
                dayOfWeek = raw.start.dayOfWeek.value,
                startNode = raw.startNode,
                endNode = raw.endNode,
                startWeek = startWeek,
                endWeek = endWeek,
                weekType = weekType,
                excludedDates = raw.excludedDates
            )
        }
        if (courses.isEmpty()) error("未解析到有效 ICS 课程")

        val timeNodes = inferIcsTimeNodes(rawEvents)
        return buildParsedImport(
            sourceType = CourseImportSourceType.ICS,
            sourceName = "ICS 文件",
            courses = courses,
            semesterStartDate = semesterStart.toString(),
            totalWeeks = courses.maxOfOrNull { it.endWeek },
            timeNodes = timeNodes
        )
    }

    private fun parseWakeUpLessonTimes(array: Iterable<JsonElement>): List<TimeNode> {
        return array.mapNotNull { element ->
            val obj = element.jsonObject
            val index = obj.intValue("node") ?: obj.intValue("period") ?: obj.intValue("index") ?: return@mapNotNull null
            val startTime = normalizeTimeText(obj.stringValue("startTime")) ?: return@mapNotNull null
            val endTime = normalizeTimeText(obj.stringValue("endTime")) ?: return@mapNotNull null
            if (startTime == endTime) return@mapNotNull null
            TimeNode(index = index, startTime = startTime, endTime = endTime)
        }
    }

    private fun buildParsedImport(
        sourceType: CourseImportSourceType,
        sourceName: String,
        courses: List<Course>,
        semesterStartDate: String?,
        totalWeeks: Int?,
        timeNodes: List<TimeNode>
    ): ParsedCourseImport {
        val normalizedNodes = normalizeTimeNodes(timeNodes)
        val config = normalizedNodes.takeIf { it.isNotEmpty() }?.let { TimeTableLayoutUtils.inferConfig(it) }
        val tableNodes = config?.let { TimeTableLayoutUtils.generateNodes(it) }
            ?.takeIf { it.size == normalizedNodes.size }
            ?: normalizedNodes
        return ParsedCourseImport(
            sourceType = sourceType,
            sourceName = sourceName,
            courses = courses,
            semesterStartDate = semesterStartDate,
            totalWeeks = totalWeeks?.takeIf { it > 0 },
            timeTableJson = tableNodes.takeIf { it.isNotEmpty() }?.let { json.encodeToString(it) },
            timeTableConfigJson = config?.let { TimeTableLayoutUtils.encodeLayoutConfig(it) },
            timeNodeCount = tableNodes.size
        )
    }

    private fun buildCourse(
        name: String,
        teacher: String,
        room: String,
        colorText: String,
        fallbackColorIndex: Int,
        dayOfWeek: Int,
        startNode: Int,
        endNode: Int,
        startWeek: Int,
        endWeek: Int,
        weekType: Int,
        excludedDates: List<String> = emptyList()
    ): Course {
        val normalizedName = name.ifBlank { "未命名课程" }
        val normalizedTeacher = teacher.trim()
        val normalizedRoom = room.trim()
        val normalizedStartNode = startNode.coerceAtLeast(1)
        val normalizedEndNode = endNode.coerceAtLeast(normalizedStartNode)
        val normalizedStartWeek = startWeek.coerceAtLeast(1)
        val normalizedEndWeek = endWeek.coerceAtLeast(normalizedStartWeek)
        val normalizedWeekType = weekType.coerceIn(0, 2)
        val id = CourseEventMapper.stableImportId(
            name = normalizedName,
            teacher = normalizedTeacher,
            room = normalizedRoom,
            dayOfWeek = dayOfWeek,
            startNode = normalizedStartNode,
            endNode = normalizedEndNode,
            startWeek = normalizedStartWeek,
            endWeek = normalizedEndWeek,
            weekType = normalizedWeekType
        )
        return Course(
            id = id,
            name = normalizedName,
            location = normalizedRoom,
            teacher = normalizedTeacher,
            color = parseColor(colorText) ?: paletteColor(fallbackColorIndex),
            dayOfWeek = dayOfWeek.coerceIn(1, 7),
            startNode = normalizedStartNode,
            endNode = normalizedEndNode,
            startWeek = normalizedStartWeek,
            endWeek = normalizedEndWeek,
            weekType = normalizedWeekType,
            excludedDates = excludedDates.distinct()
        )
    }

    private fun normalizeTimeNodes(nodes: List<TimeNode>): List<TimeNode> {
        return nodes.asSequence()
            .filter { it.index > 0 && it.startTime.isNotBlank() && it.endTime.isNotBlank() }
            .distinctBy { it.index }
            .sortedBy { it.index }
            .toList()
    }

    private fun unfoldIcsLines(content: String): List<String> {
        val result = mutableListOf<String>()
        content.replace("\r\n", "\n").replace('\r', '\n').lines().forEach { line ->
            if ((line.startsWith(" ") || line.startsWith("\t")) && result.isNotEmpty()) {
                result[result.lastIndex] = result.last() + line.drop(1)
            } else if (line.isNotBlank()) {
                result += line
            }
        }
        return result
    }

    private fun extractIcsEvents(lines: List<String>): List<List<IcsProperty>> {
        val events = mutableListOf<List<IcsProperty>>()
        var current: MutableList<IcsProperty>? = null
        lines.forEach { line ->
            when {
                line.equals("BEGIN:VEVENT", ignoreCase = true) -> current = mutableListOf()
                line.equals("END:VEVENT", ignoreCase = true) -> {
                    current?.let { events += it }
                    current = null
                }
                current != null -> parseIcsProperty(line)?.let { current?.add(it) }
            }
        }
        return events
    }

    private fun parseIcsRawEvent(properties: List<IcsProperty>): IcsRawEvent? {
        val title = properties.firstValue("SUMMARY")?.let(::unescapeIcsText)?.trim().orEmpty()
        if (title.isBlank()) return null
        val startProperty = properties.firstByName("DTSTART") ?: return null
        val endProperty = properties.firstByName("DTEND") ?: return null
        val start = parseIcsDateTime(startProperty.value, startProperty.params, ZoneId.systemDefault()) ?: return null
        val end = parseIcsDateTime(endProperty.value, endProperty.params, start.zone) ?: return null
        val description = properties.firstValue("DESCRIPTION")?.let(::unescapeIcsText).orEmpty()
        val locationValue = properties.firstValue("LOCATION")?.let(::unescapeIcsText).orEmpty()
        val (startNode, endNode) = parseSectionNodes(description) ?: parseSectionNodes(locationValue) ?: inferNodeFromTime(start, end)
        val descriptionLines = description.lines().map { it.trim() }.filter { it.isNotBlank() }
        val sectionLineIndex = descriptionLines.indexOfFirst { parseSectionNodes(it) != null }
        val roomFromDescription = if (sectionLineIndex >= 0) descriptionLines.getOrNull(sectionLineIndex + 1).orEmpty() else ""
        val teacherFromDescription = if (sectionLineIndex >= 0) descriptionLines.getOrNull(sectionLineIndex + 2).orEmpty() else ""
        val rrule = properties.firstValue("RRULE")?.let(::parseRrule).orEmpty()
        val excludedDates = properties.filterByName("EXDATE").flatMap { property ->
            property.value.split(',').mapNotNull { raw ->
                parseIcsDateTime(raw.trim(), property.params, start.zone)?.toLocalDate()?.toString()
            }
        }

        return IcsRawEvent(
            title = title,
            room = roomFromDescription.ifBlank { cleanupLocation(locationValue, teacherFromDescription) },
            teacher = teacherFromDescription,
            start = start,
            end = end,
            startNode = startNode,
            endNode = endNode,
            rrule = rrule,
            excludedDates = excludedDates
        )
    }

    private fun inferIcsTimeNodes(events: List<IcsRawEvent>): List<TimeNode> {
        val ranges = mutableMapOf<Int, Pair<LocalTime, LocalTime>>()
        events.forEach { event ->
            val startNode = event.startNode
            val endNode = event.endNode.coerceAtLeast(startNode)
            val nodeCount = endNode - startNode + 1
            val start = event.start.toLocalTime().withSecond(0).withNano(0)
            val end = event.end.toLocalTime().withSecond(0).withNano(0)
            val totalMinutes = Duration.between(start, end).toMinutes().toInt()
            if (nodeCount == 1) {
                ranges.putIfAbsent(startNode, start to end)
            } else if (totalMinutes > 0) {
                val duration = TimeTableLayoutUtils.DEFAULT_COURSE_DURATION_MINUTES
                val breakMinutes = ((totalMinutes - duration * nodeCount).toFloat() / (nodeCount - 1))
                    .toInt()
                    .coerceAtLeast(0)
                var cursor = start
                for (index in startNode..endNode) {
                    val nodeEnd = if (index == endNode) end else cursor.plusMinutes(duration.toLong())
                    ranges.putIfAbsent(index, cursor to nodeEnd)
                    cursor = nodeEnd.plusMinutes(breakMinutes.toLong())
                }
            }
        }
        return ranges.toSortedMap().map { (index, range) ->
            TimeNode(
                index = index,
                startTime = range.first.format(timeFormatter),
                endTime = range.second.format(timeFormatter)
            )
        }
    }

    private fun parseIcsProperty(line: String): IcsProperty? {
        val separator = line.indexOf(':')
        if (separator <= 0) return null
        val header = line.substring(0, separator)
        val value = line.substring(separator + 1)
        val parts = header.split(';')
        val name = parts.firstOrNull()?.uppercase().orEmpty()
        if (name.isBlank()) return null
        val params = parts.drop(1).mapNotNull { part ->
            val equals = part.indexOf('=')
            if (equals <= 0) null else {
                val key = part.substring(0, equals).uppercase()
                val paramValue = part.substring(equals + 1).trim().removeSurrounding("\"")
                key to paramValue
            }
        }.toMap()
        return IcsProperty(name, params, value)
    }

    private fun parseIcsDateTime(value: String, params: Map<String, String>, defaultZone: ZoneId): ZonedDateTime? {
        val raw = value.trim()
        if (raw.isBlank()) return null
        if (params["VALUE"].equals("DATE", ignoreCase = true) || raw.length == 8) {
            return runCatching { LocalDate.parse(raw, icsDate).atStartOfDay(defaultZone) }.getOrNull()
        }
        if (raw.endsWith("Z", ignoreCase = true)) {
            val utcText = raw.dropLast(1)
            val utcDateTime = runCatching { LocalDateTime.parse(utcText, icsDateTimeSeconds) }
                .getOrElse { runCatching { LocalDateTime.parse(utcText, icsDateTimeMinutes) }.getOrNull() }
                ?: return null
            return utcDateTime.atZone(ZoneOffset.UTC).withZoneSameInstant(defaultZone)
        }
        val localDateTime = runCatching { LocalDateTime.parse(raw, icsDateTimeSeconds) }
            .getOrElse { runCatching { LocalDateTime.parse(raw, icsDateTimeMinutes) }.getOrNull() }
            ?: return null
        val zone = params["TZID"]?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: defaultZone
        return localDateTime.atZone(zone)
    }

    private fun parseSectionNodes(text: String): Pair<Int, Int>? {
        val pattern = "第\\s*(\\d+)\\s*(?:[-~—–至到]\\s*(\\d+))?\\s*节".toRegex()
        val match = pattern.find(text) ?: return null
        val start = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        val end = match.groupValues.getOrNull(2)?.toIntOrNull() ?: start
        return start to end.coerceAtLeast(start)
    }

    private fun inferNodeFromTime(start: ZonedDateTime, end: ZonedDateTime): Pair<Int, Int> {
        val minutesFromMidnight = start.toLocalTime().toSecondOfDay() / 60
        val index = (minutesFromMidnight / 60).coerceAtLeast(0) + 1
        val duration = Duration.between(start, end).toMinutes().toInt().coerceAtLeast(1)
        val step = ((duration + TimeTableLayoutUtils.DEFAULT_COURSE_DURATION_MINUTES - 1) /
            TimeTableLayoutUtils.DEFAULT_COURSE_DURATION_MINUTES).coerceAtLeast(1)
        return index to (index + step - 1)
    }

    private fun parseRrule(value: String): Map<String, String> {
        return value.split(';').mapNotNull { part ->
            val pieces = part.split('=', limit = 2)
            if (pieces.size != 2) null else pieces[0].uppercase() to pieces[1]
        }.toMap()
    }

    private fun weekForDate(semesterStart: LocalDate, date: LocalDate): Int {
        return (ChronoUnit.DAYS.between(semesterStart, date) / 7).toInt() + 1
    }

    private fun unescapeIcsText(value: String): String {
        return value
            .replace("\\n", "\n")
            .replace("\\N", "\n")
            .replace("\\,", ",")
            .replace("\\;", ";")
            .replace("\\\\", "\\")
    }

    private fun cleanupLocation(location: String, teacher: String): String {
        return if (teacher.isNotBlank() && location.endsWith(teacher)) {
            location.removeSuffix(teacher).trim()
        } else {
            location.trim()
        }
    }

    private fun normalizeDateText(value: String?): String? {
        val parts = value?.trim()?.split('-') ?: return null
        if (parts.size != 3) return null
        val year = parts[0].padStart(4, '0')
        val month = parts[1].padStart(2, '0')
        val day = parts[2].padStart(2, '0')
        val normalized = "$year-$month-$day"
        return runCatching { LocalDate.parse(normalized).toString() }.getOrNull()
    }

    private fun normalizeTimeText(value: String?): String? {
        val text = value?.trim()?.replace('：', ':')?.replace('.', ':') ?: return null
        val parsed = runCatching { LocalTime.parse(text) }
            .getOrElse { runCatching { LocalTime.parse(text, timeFormatter) }.getOrNull() }
        return parsed?.format(timeFormatter)
    }

    private fun parseColor(value: String): Int? {
        val normalized = value.trim()
        if (normalized.isBlank()) return null
        return runCatching { Color.parseColor(normalized) }.getOrNull()
            ?: normalized.toIntOrNull()
    }

    private fun paletteColor(index: Int): Int {
        val colors = intArrayOf(
            Color.rgb(66, 133, 244),
            Color.rgb(52, 168, 83),
            Color.rgb(251, 188, 5),
            Color.rgb(234, 67, 53),
            Color.rgb(156, 39, 176),
            Color.rgb(0, 150, 136),
            Color.rgb(255, 112, 67),
            Color.rgb(63, 81, 181)
        )
        return colors[index % colors.size]
    }

    private fun List<IcsProperty>.firstByName(name: String): IcsProperty? = firstOrNull { it.name == name }
    private fun List<IcsProperty>.firstValue(name: String): String? = firstByName(name)?.value
    private fun List<IcsProperty>.filterByName(name: String): List<IcsProperty> = filter { it.name == name }

    private fun JsonObject.stringValue(key: String): String? = this[key]?.jsonPrimitive?.content
    private fun JsonObject.intValue(key: String): Int? = this[key]?.jsonPrimitive?.content?.toIntOrNull()

    private data class WakeUpBase(
        val id: Int,
        val courseName: String,
        val teacher: String,
        val color: String
    )

    private data class IcsProperty(
        val name: String,
        val params: Map<String, String>,
        val value: String
    )

    private data class IcsRawEvent(
        val title: String,
        val room: String,
        val teacher: String,
        val start: ZonedDateTime,
        val end: ZonedDateTime,
        val startNode: Int,
        val endNode: Int,
        val rrule: Map<String, String>,
        val excludedDates: List<String>
    )
}
