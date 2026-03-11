package com.antgskds.calendarassistant.core.course

import com.antgskds.calendarassistant.data.model.TimeNode
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class TimeTableLayoutConfig(
    val morningCount: Int = 4,
    val afternoonCount: Int = 4,
    val nightCount: Int = 4,
    val morningStart: LocalTime = LocalTime.of(8, 0),
    val afternoonStart: LocalTime = LocalTime.of(14, 0),
    val nightStart: LocalTime = LocalTime.of(19, 0),
    val customBreaks: Map<Int, Int> = emptyMap(),
    val customDurations: Map<Int, Int> = emptyMap()
) {
    val totalNodes: Int get() = morningCount + afternoonCount + nightCount
    val lunchBoundaryNode: Int get() = morningCount
    val dinnerBoundaryNode: Int get() = morningCount + afternoonCount
    val afternoonStartNode: Int get() = morningCount + 1
    val nightStartNode: Int get() = morningCount + afternoonCount + 1
}

object TimeTableLayoutUtils {

    const val MIN_SECTION_COUNT = 2
    const val MAX_SECTION_COUNT = 6
    const val MIN_NIGHT_SECTION_COUNT = 0
    const val MAX_NIGHT_SECTION_COUNT = 4
    const val DEFAULT_COURSE_DURATION_MINUTES = 45
    const val MIN_DURATION_MINUTES = 30
    const val MAX_DURATION_MINUTES = 90
    const val DEFAULT_BREAK_MINUTES = 10

    private const val PERIOD_MORNING = "morning"
    private const val PERIOD_AFTERNOON = "afternoon"
    private const val PERIOD_NIGHT = "night"

    private val formatter = DateTimeFormatter.ofPattern("HH:mm")
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    fun defaultConfig(): TimeTableLayoutConfig = TimeTableLayoutConfig()

    fun parseNodes(jsonString: String): List<TimeNode> {
        return try {
            if (jsonString.isBlank()) {
                emptyList()
            } else {
                json.decodeFromString<List<TimeNode>>(jsonString).sortedBy { it.index }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun nodeCountFromJson(jsonString: String): Int {
        return parseNodes(jsonString).size.takeIf { it > 0 } ?: defaultConfig().totalNodes
    }

    fun generateNodes(config: TimeTableLayoutConfig): List<TimeNode> {
        val normalizedConfig = normalizeConfig(config)
        val nodes = mutableListOf<TimeNode>()

        for (index in 1..normalizedConfig.totalNodes) {
            val previousEnd = nodes.lastOrNull()?.let {
                safeParseTime(it.endTime, normalizedConfig.morningStart)
            }
            val startTime = when {
                index == 1 -> when {
                    normalizedConfig.morningCount > 0 -> normalizedConfig.morningStart
                    normalizedConfig.afternoonCount > 0 -> normalizedConfig.afternoonStart
                    else -> normalizedConfig.nightStart
                }
                normalizedConfig.afternoonCount > 0 && index == normalizedConfig.afternoonStartNode -> {
                    val anchor = normalizedConfig.afternoonStart
                    if (previousEnd != null && previousEnd.isAfter(anchor)) previousEnd else anchor
                }
                normalizedConfig.nightCount > 0 && index == normalizedConfig.nightStartNode -> {
                    val anchor = normalizedConfig.nightStart
                    if (previousEnd != null && previousEnd.isAfter(anchor)) previousEnd else anchor
                }
                else -> {
                    (previousEnd ?: normalizedConfig.morningStart).plusMinutes(
                        (normalizedConfig.customBreaks[index - 1] ?: DEFAULT_BREAK_MINUTES).toLong()
                    )
                }
            }
            val durationMinutes = durationForNode(index, normalizedConfig)
            val endTime = startTime.plusMinutes(durationMinutes.toLong())

            nodes += TimeNode(
                index = index,
                startTime = startTime.format(formatter),
                endTime = endTime.format(formatter),
                period = periodForIndex(index, normalizedConfig)
            )
        }

        return nodes
    }

    fun inferConfig(nodes: List<TimeNode>): TimeTableLayoutConfig {
        if (nodes.isEmpty()) return defaultConfig()

        val sortedNodes = nodes.sortedBy { it.index }
        val totalNodes = sortedNodes.size
        val (morningCount, afternoonCount, nightCount) = inferSectionCounts(sortedNodes, totalNodes)

        val morningStart = safeParseTime(sortedNodes.first().startTime, defaultConfig().morningStart)
        val afternoonStart = if (afternoonCount > 0 && morningCount < sortedNodes.size) {
            sortedNodes[morningCount].startTime.let { safeParseTime(it, defaultConfig().afternoonStart) }
        } else {
            defaultConfig().afternoonStart
        }
        val nightStart = if (nightCount > 0 && morningCount + afternoonCount < sortedNodes.size) {
            sortedNodes[morningCount + afternoonCount].startTime.let {
                safeParseTime(it, defaultConfig().nightStart)
            }
        } else {
            defaultConfig().nightStart
        }

        val config = TimeTableLayoutConfig(
            morningCount = morningCount,
            afternoonCount = afternoonCount,
            nightCount = nightCount,
            morningStart = morningStart,
            afternoonStart = afternoonStart,
            nightStart = nightStart,
            customBreaks = inferCustomBreaks(sortedNodes, morningCount, afternoonCount),
            customDurations = inferCustomDurations(sortedNodes)
        )

        return normalizeConfig(config)
    }

    fun sanitizeCustomBreaks(customBreaks: Map<Int, Int>, config: TimeTableLayoutConfig): Map<Int, Int> {
        val blockedKeys = setOf(config.lunchBoundaryNode, config.dinnerBoundaryNode)

        return customBreaks
            .filterKeys { it in 1 until config.totalNodes && it !in blockedKeys }
            .mapValues { (_, minutes) -> minutes.coerceAtLeast(1) }
    }

    fun sanitizeCustomDurations(customDurations: Map<Int, Int>, config: TimeTableLayoutConfig): Map<Int, Int> {
        return customDurations
            .filterKeys { it in 1..config.totalNodes }
            .mapValues { (_, minutes) -> minutes.coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES) }
            .filterValues { it != DEFAULT_COURSE_DURATION_MINUTES }
    }

    fun durationForNode(nodeIndex: Int, config: TimeTableLayoutConfig): Int {
        return config.customDurations[nodeIndex] ?: DEFAULT_COURSE_DURATION_MINUTES
    }

    private fun inferSectionCounts(nodes: List<TimeNode>, totalNodes: Int): Triple<Int, Int, Int> {
        inferSectionCountsFromPeriod(nodes)?.let { return it }
        inferSectionCountsFromGaps(nodes, totalNodes)?.let { return it }
        return legacySectionCounts(totalNodes)
    }

    private fun inferSectionCountsFromPeriod(nodes: List<TimeNode>): Triple<Int, Int, Int>? {
        val periods = nodes.mapNotNull { normalizePeriod(it.period) }
        if (periods.size != nodes.size) return null

        val morningCount = periods.count { it == PERIOD_MORNING }
        val afternoonCount = periods.count { it == PERIOD_AFTERNOON }
        val nightCount = periods.count { it == PERIOD_NIGHT }

        return if (isRenderableSectionCounts(morningCount, afternoonCount, nightCount)) {
            Triple(morningCount, afternoonCount, nightCount)
        } else {
            null
        }
    }

    private fun inferSectionCountsFromGaps(nodes: List<TimeNode>, totalNodes: Int): Triple<Int, Int, Int>? {
        val gaps = nodes.zipWithNext().mapNotNull { (current, next) ->
            val currentEnd = safeParseTimeOrNull(current.endTime)
            val nextStart = safeParseTimeOrNull(next.startTime)
            if (currentEnd == null || nextStart == null) {
                null
            } else {
                current.index to Duration.between(currentEnd, nextStart).toMinutes().toInt()
            }
        }.sortedByDescending { it.second }

        val significantBreaks = gaps.filter { it.second >= DEFAULT_BREAK_MINUTES * 2 }
        if (significantBreaks.size < 2) return null

        val topBreaks = significantBreaks.take(2).sortedBy { it.first }

        val morningCount = topBreaks[0].first
        val afternoonCount = topBreaks[1].first - topBreaks[0].first
        val nightCount = totalNodes - topBreaks[1].first

        return if (isRenderableSectionCounts(morningCount, afternoonCount, nightCount)) {
            Triple(morningCount, afternoonCount, nightCount)
        } else {
            null
        }
    }

    private fun inferCustomDurations(nodes: List<TimeNode>): Map<Int, Int> {
        val durations = mutableMapOf<Int, Int>()

        nodes.forEach { node ->
            val start = safeParseTimeOrNull(node.startTime) ?: return@forEach
            val end = safeParseTimeOrNull(node.endTime) ?: return@forEach
            val duration = Duration.between(start, end).toMinutes().toInt()
            if (duration > 0 && duration != DEFAULT_COURSE_DURATION_MINUTES) {
                durations[node.index] = duration.coerceIn(MIN_DURATION_MINUTES, MAX_DURATION_MINUTES)
            }
        }

        return durations
    }

    private fun inferCustomBreaks(
        nodes: List<TimeNode>,
        morningCount: Int,
        afternoonCount: Int
    ): Map<Int, Int> {
        val blockedKeys = setOf(morningCount, morningCount + afternoonCount)
        val inferredBreaks = mutableMapOf<Int, Int>()

        nodes.zipWithNext().forEach { (current, next) ->
            if (current.index in blockedKeys) return@forEach

            val currentEnd = safeParseTimeOrNull(current.endTime) ?: return@forEach
            val nextStart = safeParseTimeOrNull(next.startTime) ?: return@forEach
            val gapMinutes = Duration.between(currentEnd, nextStart).toMinutes().toInt()

            if (gapMinutes > 0 && gapMinutes != DEFAULT_BREAK_MINUTES) {
                inferredBreaks[current.index] = gapMinutes
            }
        }

        return inferredBreaks
    }

    private fun normalizeConfig(config: TimeTableLayoutConfig): TimeTableLayoutConfig {
        val morningCount = config.morningCount.coerceAtLeast(0)
        val afternoonCount = config.afternoonCount.coerceAtLeast(0)
        val nightCount = config.nightCount.coerceAtLeast(0)

        val normalizedConfig = config.copy(
            morningCount = morningCount,
            afternoonCount = afternoonCount,
            nightCount = nightCount
        )

        return normalizedConfig.copy(
            customBreaks = sanitizeCustomBreaks(normalizedConfig.customBreaks, normalizedConfig),
            customDurations = sanitizeCustomDurations(normalizedConfig.customDurations, normalizedConfig)
        )
    }

    private fun legacySectionCounts(totalNodes: Int): Triple<Int, Int, Int> {
        return when {
            totalNodes <= 0 -> Triple(0, 0, 0)
            totalNodes <= 4 -> Triple(totalNodes, 0, 0)
            totalNodes <= 8 -> Triple(4, totalNodes - 4, 0)
            else -> Triple(4, 4, totalNodes - 8)
        }
    }

    private fun isRenderableSectionCounts(morningCount: Int, afternoonCount: Int, nightCount: Int): Boolean {
        return morningCount >= 0 && afternoonCount >= 0 && nightCount >= 0 &&
            (morningCount + afternoonCount + nightCount) > 0
    }

    private fun periodForIndex(index: Int, config: TimeTableLayoutConfig): String {
        return when {
            index <= config.morningCount -> PERIOD_MORNING
            index <= config.morningCount + config.afternoonCount -> PERIOD_AFTERNOON
            else -> PERIOD_NIGHT
        }
    }

    private fun normalizePeriod(period: String): String? {
        return when (period.lowercase()) {
            PERIOD_MORNING -> PERIOD_MORNING
            PERIOD_AFTERNOON -> PERIOD_AFTERNOON
            PERIOD_NIGHT -> PERIOD_NIGHT
            else -> null
        }
    }

    private fun safeParseTime(value: String, fallback: LocalTime): LocalTime {
        return safeParseTimeOrNull(value) ?: fallback
    }

    private fun safeParseTimeOrNull(value: String): LocalTime? {
        return try {
            LocalTime.parse(value, formatter)
        } catch (_: Exception) {
            null
        }
    }
}
