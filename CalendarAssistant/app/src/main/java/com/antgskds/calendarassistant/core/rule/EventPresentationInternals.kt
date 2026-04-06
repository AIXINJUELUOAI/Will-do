package com.antgskds.calendarassistant.core.rule

import android.content.Context
import com.antgskds.calendarassistant.R
import com.antgskds.calendarassistant.data.model.EventType
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.service.capsule.CapsuleActionSpec
import com.antgskds.calendarassistant.service.capsule.CapsuleDisplayModel
import com.antgskds.calendarassistant.service.receiver.EventActionReceiver
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

internal object EventPresentationInternals {
    data class TransportInfo(
        val type: String,
        val mainDisplay: String,
        val subDisplay: String,
        val isCheckedIn: Boolean = false
    )

    data class PickupInfo(
        val code: String,
        val platform: String,
        val location: String
    )

    fun resolveRuleId(event: MyEvent): String {
        return RuleMatchingEngine.resolvePayload(event)?.ruleId
            ?: event.tag.ifBlank { RuleMatchingEngine.RULE_GENERAL }
    }

    fun resolveDisplayContent(
        event: MyEvent,
        ruleId: String,
        isExpired: Boolean,
        isTerminal: Boolean
    ): Triple<String, String?, String?> {
        return when (ruleId) {
            RuleMatchingEngine.RULE_TRAIN -> resolveTrainDisplay(event, isExpired)
            RuleMatchingEngine.RULE_TAXI -> resolveTaxiDisplay(event, isExpired, isTerminal)
            RuleMatchingEngine.RULE_PICKUP, RuleMatchingEngine.RULE_FOOD -> resolvePickupDisplay(event, isExpired)
            RuleMatchingEngine.RULE_FLIGHT -> resolveFlightDisplay(event)
            RuleMatchingEngine.RULE_TICKET -> resolveTicketDisplay(event, isExpired)
            RuleMatchingEngine.RULE_SENDER -> resolveSenderDisplay(event)
            else -> resolveGeneralDisplay(event)
        }
    }

    fun resolveStatusLabel(
        event: MyEvent,
        ruleId: String,
        isExpired: Boolean,
        isInProgress: Boolean,
        isComingSoon: Boolean
    ): String? {
        return when {
            event.isRecurringParent -> "重复"
            isExpired -> "已结束"
            event.isCheckedIn -> "已检票"
            event.isCompleted -> RuleActionDefaults.defaultsFor(ruleId).terminal.name
            isInProgress -> "进行中"
            isComingSoon -> "即将开始"
            else -> null
        }
    }

    fun resolveStatusColor(
        event: MyEvent,
        isExpired: Boolean,
        isInProgress: Boolean,
        isComingSoon: Boolean
    ): StatusColor {
        return when {
            event.isRecurringParent -> StatusColor.PRIMARY
            isExpired -> StatusColor.MUTED
            event.isCheckedIn -> StatusColor.SUCCESS
            event.isCompleted -> StatusColor.MUTED
            isInProgress -> StatusColor.PRIMARY
            isComingSoon -> StatusColor.WARNING
            else -> StatusColor.PRIMARY
        }
    }

    fun resolvePrimaryAction(ruleId: String, event: MyEvent, isExpired: Boolean, isCourse: Boolean): EventAction? {
        if (event.isCompleted || event.isCheckedIn || isExpired || isCourse || event.isRecurringParent) return null
        val defaults = RuleActionDefaults.defaultsFor(ruleId)
        val receiverAction = when (ruleId) {
            RuleMatchingEngine.RULE_TRAIN -> EventActionReceiver.ACTION_CHECKIN
            else -> EventActionReceiver.ACTION_COMPLETE_SCHEDULE
        }
        return EventAction(defaults.actionLabel, receiverAction, isUndo = false)
    }

    fun resolveUndoAction(ruleId: String, isTerminal: Boolean): EventAction? {
        if (!isTerminal) return null
        val defaults = RuleActionDefaults.defaultsFor(ruleId)
        return EventAction(defaults.undoLabel, EventActionReceiver.ACTION_COMPLETE_SCHEDULE, isUndo = true)
    }

    fun resolveIconResId(ruleId: String, event: MyEvent): Int? {
        RuleRegistry.getCustomCapsuleIconResId(ruleId)?.let { return it }
        RuleRegistry.getIconResId(ruleId)?.let { return it }
        return when (ruleId) {
            RuleMatchingEngine.RULE_PICKUP -> if (isFoodPickup(event.description)) R.drawable.ic_stat_food else R.drawable.ic_stat_package
            RuleMatchingEngine.RULE_FOOD -> R.drawable.ic_stat_food
            RuleMatchingEngine.RULE_TRAIN -> R.drawable.ic_stat_train
            RuleMatchingEngine.RULE_TAXI -> R.drawable.ic_stat_car
            RuleMatchingEngine.RULE_FLIGHT -> R.drawable.ic_stat_flight
            RuleMatchingEngine.RULE_TICKET -> R.drawable.ic_stat_ticket
            RuleMatchingEngine.RULE_SENDER -> R.drawable.ic_stat_sender
            EventType.COURSE -> R.drawable.ic_stat_course
            EventType.EVENT, RuleMatchingEngine.RULE_GENERAL -> R.drawable.ic_stat_event
            else -> R.drawable.ic_notification_small
        }
    }

    fun resolveActionIcon(ruleId: String, isTerminal: Boolean): ActionIconSpec {
        return if (isTerminal) {
            ActionIconSpec(ActionIconType.UNDO, 0xFFFFA726)
        } else when (ruleId) {
            RuleMatchingEngine.RULE_TRAIN -> ActionIconSpec(ActionIconType.CHECKIN, 0xFF4CAF50)
            RuleMatchingEngine.RULE_TAXI -> ActionIconSpec(ActionIconType.RIDE, 0xFFFF9800)
            RuleMatchingEngine.RULE_PICKUP -> ActionIconSpec(ActionIconType.PICKUP, 0xFF2196F3)
            else -> ActionIconSpec(ActionIconType.COMPLETE, 0xFF4CAF50)
        }
    }

    fun resolveTimeRange(event: MyEvent, ruleId: String, isCourse: Boolean): String? {
        if (isCourse || ruleId == RuleMatchingEngine.RULE_GENERAL) {
            val start = event.startTime.trim().takeIf { it.isNotEmpty() }
            val end = event.endTime.trim().takeIf { it.isNotEmpty() }
            return when {
                start != null && end != null -> "$start-$end"
                start != null -> start
                else -> end
            }
        }
        return null
    }

    fun composeCapsule(model: EventRenderModel, event: MyEvent, isExpired: Boolean): CapsuleDisplayModel {
        return when (model.ruleId) {
            RuleMatchingEngine.RULE_TRAIN -> composeTrainCapsule(model, event, isExpired)
            RuleMatchingEngine.RULE_TAXI -> composeTaxiCapsule(model, event, isExpired)
            RuleMatchingEngine.RULE_PICKUP, RuleMatchingEngine.RULE_FOOD -> composePickupCapsule(model, event, isExpired)
            RuleMatchingEngine.RULE_FLIGHT -> composeFlightCapsule(model, event, isExpired)
            RuleMatchingEngine.RULE_TICKET -> composeTicketCapsule(model, event, isExpired)
            RuleMatchingEngine.RULE_SENDER -> composeSenderCapsule(model, event, isExpired)
            else -> composeGeneralCapsule(model, event, isExpired)
        }
    }

    fun composeAggregatePickupCapsule(events: List<MyEvent>): CapsuleDisplayModel {
        val hasExpiredItems = events.any { computeIsExpired(it, LocalDateTime.now()) }
        val primaryText = if (hasExpiredItems) "${events.size} 个待取 (含过期)" else "${events.size} 个待取事项"
        val secondaryText = events.mapNotNull {
            val info = parsePickupInfo(it)
            formatPickupSubtitle(info.platform, info.location)
        }.distinct().take(2).takeIf { it.isNotEmpty() }?.joinToString(" · ")
        val expandedText = events.take(5).mapIndexed { index, evt ->
            val info = parsePickupInfo(evt)
            val expired = computeIsExpired(evt, LocalDateTime.now())
            val code = if (evt.isCompleted || expired) preferText(evt.title, info.code, "取件提醒") else preferText(info.code, evt.title, "取件提醒")
            val detail = formatPickupSubtitle(info.platform, info.location)
            if (detail.isNullOrBlank()) "${index + 1}. $code" else "${index + 1}. $code - $detail"
        }.joinToString("\n").ifBlank { null }
        val action = if (events.any { !it.isCompleted && !computeIsExpired(it, LocalDateTime.now()) }) {
            CapsuleActionSpec(label = "已取", receiverAction = EventActionReceiver.ACTION_COMPLETE_SCHEDULE)
        } else null
        return CapsuleDisplayModel(
            shortText = primaryText,
            primaryText = primaryText,
            secondaryText = secondaryText,
            expandedText = expandedText,
            tapOpensPickupList = true,
            action = action
        )
    }

    fun parsePickupInfo(event: MyEvent): PickupInfo {
        val (code, platform, location) = parsePickupMicroFormat(event.description)
        return PickupInfo(code.ifBlank { event.title }, platform, location.ifBlank { event.location })
    }

    fun computeIsExpired(event: MyEvent, now: LocalDateTime): Boolean {
        return try {
            val endDate = LocalDate.parse(event.endDate.toString(), DateTimeFormatter.ISO_LOCAL_DATE)
            val endTime = LocalTime.parse(event.endTime, DateTimeFormatter.ofPattern("HH:mm"))
            now.isAfter(LocalDateTime.of(endDate, endTime))
        } catch (_: Exception) {
            false
        }
    }

    fun computeIsInProgress(event: MyEvent, now: LocalDateTime): Boolean {
        return try {
            val startDate = LocalDate.parse(event.startDate.toString(), DateTimeFormatter.ISO_LOCAL_DATE)
            val startTime = LocalTime.parse(event.startTime, DateTimeFormatter.ofPattern("HH:mm"))
            val endDate = LocalDate.parse(event.endDate.toString(), DateTimeFormatter.ISO_LOCAL_DATE)
            val endTime = LocalTime.parse(event.endTime, DateTimeFormatter.ofPattern("HH:mm"))
            !now.isBefore(LocalDateTime.of(startDate, startTime)) && !now.isAfter(LocalDateTime.of(endDate, endTime))
        } catch (_: Exception) {
            false
        }
    }

    fun computeIsComingSoon(event: MyEvent, now: LocalDateTime): Boolean {
        return try {
            val startDate = LocalDate.parse(event.startDate.toString(), DateTimeFormatter.ISO_LOCAL_DATE)
            val startTime = LocalTime.parse(event.startTime, DateTimeFormatter.ofPattern("HH:mm"))
            val eventStart = LocalDateTime.of(startDate, startTime)
            now.isBefore(eventStart) && now.isAfter(eventStart.minusMinutes(30))
        } catch (_: Exception) {
            false
        }
    }

    fun isFoodPickup(description: String?): Boolean = description?.startsWith("【取餐】") == true

    private fun resolveTrainDisplay(event: MyEvent, isExpired: Boolean): Triple<String, String?, String?> {
        val info = parseTransport(event)
        val templateTitle = RuleDisplayTemplateResolver.renderTitle(event)
        val title = templateTitle?.takeIf { it.isNotBlank() } ?: when {
            info.isCheckedIn -> info.mainDisplay.ifBlank { event.title }
            isExpired -> event.title
            else -> info.mainDisplay.ifBlank { "待检票" }
        }
        val destination = event.location.trim().takeIf { it.isNotBlank() && it != info.subDisplay }
        return Triple(title, formatTrainSubtitle(info.subDisplay, destination), null)
    }

    private fun resolveTaxiDisplay(event: MyEvent, isExpired: Boolean, isTerminal: Boolean): Triple<String, String?, String?> {
        val info = parseTransport(event)
        val templateTitle = RuleDisplayTemplateResolver.renderTitle(event)
        val title = templateTitle?.takeIf { it.isNotBlank() } ?: if (isTerminal || isExpired) event.title else info.mainDisplay.ifBlank { event.title }
        val subtitle = formatTaxiSubtitle(event, info)
        val locationExtra = event.location.trim().takeIf { it.isNotBlank() && subtitle != null && !subtitle.contains(it) }
        return Triple(title, subtitle, listOfNotNull(subtitle, locationExtra).joinToString("\n").ifBlank { null })
    }

    private fun resolvePickupDisplay(event: MyEvent, isExpired: Boolean): Triple<String, String?, String?> {
        val info = parsePickupInfo(event)
        val templateTitle = RuleDisplayTemplateResolver.renderTitle(event)
        val title = templateTitle?.takeIf { it.isNotBlank() } ?: if (event.isCompleted || isExpired) {
            preferText(event.title, info.code, "取件提醒")
        } else {
            preferText(info.code, event.title, "取件提醒")
        }
        val locationExtra = info.location.trim().takeIf { it.isNotBlank() && info.platform.isNotBlank() && !it.contains(info.platform) }
        return Triple(title, formatPickupSubtitle(info.platform, info.location), locationExtra?.takeIf { it.isNotBlank() })
    }

    private fun resolveFlightDisplay(event: MyEvent): Triple<String, String?, String?> {
        val fields = RuleMatchingEngine.splitFields(RuleMatchingEngine.extractPayloadText(event.description).orEmpty(), 3)
        val templateTitle = RuleDisplayTemplateResolver.renderTitle(event)
        val flightNo = fields.getOrNull(0).orEmpty()
        val gate = fields.getOrNull(1).orEmpty()
        val seat = fields.getOrNull(2).orEmpty()
        val title = templateTitle?.takeIf { it.isNotBlank() } ?: preferText(flightNo, event.title, "航班提醒")
        val subtitle = buildString {
            if (gate.isNotBlank()) append("$gate 登机口")
            if (seat.isNotBlank()) {
                if (isNotEmpty()) append(" · ")
                append(seat)
            }
        }.trim().ifBlank { null }
        return Triple(title, subtitle, null)
    }

    private fun resolveTicketDisplay(event: MyEvent, isExpired: Boolean): Triple<String, String?, String?> {
        val info = parsePickupInfo(event)
        val templateTitle = RuleDisplayTemplateResolver.renderTitle(event)
        val title = templateTitle?.takeIf { it.isNotBlank() } ?: if (event.isCompleted || isExpired) {
            preferText(event.title, info.code, "取票提醒")
        } else {
            preferText(info.code, event.title, "取票提醒")
        }
        return Triple(title, formatPickupSubtitle(info.platform, info.location), null)
    }

    private fun resolveSenderDisplay(event: MyEvent): Triple<String, String?, String?> {
        val info = parsePickupInfo(event)
        val templateTitle = RuleDisplayTemplateResolver.renderTitle(event)
        val title = templateTitle?.takeIf { it.isNotBlank() } ?: preferText(info.code, event.title, "寄件提醒")
        return Triple(title, formatPickupSubtitle(info.platform, info.location), null)
    }

    private fun resolveGeneralDisplay(event: MyEvent): Triple<String, String?, String?> {
        val templateTitle = RuleDisplayTemplateResolver.renderTitle(event)
        val title = templateTitle ?: preferText(event.title, "日程提醒")
        val location = event.location.trim().takeIf { it.isNotBlank() }
        val desc = extractDescription(event.description)
        return Triple(title, location ?: desc, if (location != null && desc != null && desc != location) desc else null)
    }

    private fun composeTrainCapsule(model: EventRenderModel, event: MyEvent, isExpired: Boolean): CapsuleDisplayModel {
        val info = parseTransport(event)
        val secondaryText = formatTrainSubtitle(info.subDisplay, event.location)
        val action = if (!info.isCheckedIn && !isExpired) CapsuleActionSpec("已检票", EventActionReceiver.ACTION_CHECKIN) else null
        return CapsuleDisplayModel(
            shortText = model.title,
            primaryText = model.title,
            secondaryText = secondaryText,
            expandedText = secondaryText,
            action = action
        )
    }

    private fun composeTaxiCapsule(model: EventRenderModel, event: MyEvent, isExpired: Boolean): CapsuleDisplayModel {
        val info = parseTransport(event)
        val secondaryText = formatTaxiSubtitle(event, info) ?: "网约车"
        val expandedText = joinLines(secondaryText, sanitize(event.location))
        val action = if (!event.isCompleted && !isExpired) CapsuleActionSpec("已用车", EventActionReceiver.ACTION_COMPLETE_SCHEDULE) else null
        return CapsuleDisplayModel(
            shortText = model.title,
            primaryText = model.title,
            secondaryText = secondaryText,
            expandedText = expandedText,
            action = action
        )
    }

    private fun composePickupCapsule(model: EventRenderModel, event: MyEvent, isExpired: Boolean): CapsuleDisplayModel {
        val info = parsePickupInfo(event)
        val secondaryText = formatPickupSubtitle(info.platform, info.location)
        val expandedText = joinLines(secondaryText, summaryText(event.description))
        val action = if (!event.isCompleted && !isExpired) CapsuleActionSpec("已取", EventActionReceiver.ACTION_COMPLETE_SCHEDULE) else null
        return CapsuleDisplayModel(
            shortText = model.title,
            primaryText = model.title,
            secondaryText = secondaryText,
            expandedText = expandedText,
            tapOpensPickupList = true,
            action = action
        )
    }

    private fun composeFlightCapsule(model: EventRenderModel, event: MyEvent, isExpired: Boolean): CapsuleDisplayModel {
        val secondaryText = model.subtitle
        val expandedText = joinLines(secondaryText, sanitize(event.location))
        val action = if (!event.isCompleted && !isExpired) CapsuleActionSpec("已登机", EventActionReceiver.ACTION_COMPLETE_SCHEDULE) else null
        return CapsuleDisplayModel(
            shortText = model.title,
            primaryText = model.title,
            secondaryText = secondaryText,
            expandedText = expandedText,
            action = action
        )
    }

    private fun composeTicketCapsule(model: EventRenderModel, event: MyEvent, isExpired: Boolean): CapsuleDisplayModel {
        val info = parsePickupInfo(event)
        val secondaryText = formatPickupSubtitle(info.platform, info.location)
        val expandedText = joinLines(secondaryText, summaryText(event.description))
        val action = if (!event.isCompleted && !isExpired) CapsuleActionSpec("已取", EventActionReceiver.ACTION_COMPLETE_SCHEDULE) else null
        return CapsuleDisplayModel(
            shortText = model.title,
            primaryText = model.title,
            secondaryText = secondaryText,
            expandedText = expandedText,
            action = action
        )
    }

    private fun composeSenderCapsule(model: EventRenderModel, event: MyEvent, isExpired: Boolean): CapsuleDisplayModel {
        val info = parsePickupInfo(event)
        val secondaryText = formatPickupSubtitle(info.platform, info.location)
        val expandedText = joinLines(secondaryText, summaryText(event.description))
        val action = if (!event.isCompleted && !isExpired) CapsuleActionSpec("已寄件", EventActionReceiver.ACTION_COMPLETE_SCHEDULE) else null
        return CapsuleDisplayModel(
            shortText = model.title,
            primaryText = model.title,
            secondaryText = secondaryText,
            expandedText = expandedText,
            action = action
        )
    }

    private fun composeGeneralCapsule(model: EventRenderModel, event: MyEvent, isExpired: Boolean): CapsuleDisplayModel {
        val primaryText = preferText(model.title, "日程提醒")
        val detailText = sanitize(event.location) ?: summaryText(event.description)
        val timeText = formatTimeRange(event)
        val secondaryText = detailText ?: timeText
        val tertiaryText = if (detailText != null) timeText else null
        val expandedText = joinLines(detailText, tertiaryText, summaryText(event.description)?.takeUnless { it == detailText })
        val action = if (!event.isCompleted && !isExpired && event.eventType != EventType.COURSE) CapsuleActionSpec("已完成", EventActionReceiver.ACTION_COMPLETE_SCHEDULE) else null
        return CapsuleDisplayModel(primaryText, primaryText, secondaryText, expandedText = expandedText, tertiaryText = tertiaryText, action = action)
    }

    private fun parseTransport(event: MyEvent): TransportInfo {
        if (event.description.isBlank()) return TransportInfo("none", "", "", false)
        val payload = RuleMatchingEngine.resolvePayload(event.description, null)
        return when (payload?.ruleId) {
            RuleMatchingEngine.RULE_TRAIN -> parseTrainPayload(payload.payload, event.isCheckedIn)
            RuleMatchingEngine.RULE_TAXI -> parseTaxiPayload(payload.payload, event.isCompleted)
            else -> TransportInfo("none", "", "", false)
        }
    }

    private fun parseTrainPayload(payload: String, isCheckedIn: Boolean): TransportInfo {
        val parts = RuleMatchingEngine.splitFields(payload, 3)
        return when {
            parts.size >= 3 -> {
                val trainNo = parts[0].ifBlank { "" }
                val gate = parts[1].ifBlank { "" }
                val seat = parts[2].ifBlank { "" }
                if (isCheckedIn) TransportInfo("train", seat, trainNo, true)
                else TransportInfo("train", if (gate.isNotBlank()) "$gate 检票" else "等待检票", trainNo)
            }
            parts.size == 2 -> {
                val trainNo = parts[0].ifBlank { "" }
                val gateOrSeat = parts[1]
                if (isCheckedIn) TransportInfo("train", gateOrSeat, trainNo, true)
                else TransportInfo("train", if (gateOrSeat.isNotBlank()) "$gateOrSeat 检票" else "等待检票", trainNo)
            }
            parts.size == 1 -> TransportInfo("train", "等待检票", parts[0])
            else -> TransportInfo("none", "", "", false)
        }
    }

    private fun parseTaxiPayload(payload: String, isRideCompleted: Boolean): TransportInfo {
        val parts = RuleMatchingEngine.splitFields(payload, 3)
        return when {
            parts.size >= 3 -> TransportInfo("taxi", parts[2].ifBlank { "" }, "${parts[0].ifBlank { "" }} ${parts[1].ifBlank { "" }}".trim(), isRideCompleted)
            parts.size == 2 -> TransportInfo("taxi", parts[1].ifBlank { "" }, parts[0].ifBlank { "" }, isRideCompleted)
            parts.size == 1 -> TransportInfo("taxi", parts[0], "", isRideCompleted)
            else -> TransportInfo("none", "", "", false)
        }
    }

    private fun parsePickupMicroFormat(description: String): Triple<String, String, String> {
        if (description.isBlank()) return Triple("", "", "")
        val payload = RuleMatchingEngine.resolvePayload(description, RuleMatchingEngine.RULE_PICKUP)
        if (payload?.ruleId == RuleMatchingEngine.RULE_PICKUP) {
            val fields = RuleMatchingEngine.splitFields(payload.payload, 3)
            return Triple(fields[0], fields[1], fields[2])
        }
        val pattern = Regex("【取(件|餐)】([^|]+)\\|([^|]+)(?:\\|(.*))?")
        val match = pattern.find(description)
        return if (match != null) Triple(match.groupValues[2], match.groupValues[3], match.groupValues[4]) else Triple("", "", "")
    }

    private fun formatTrainSubtitle(trainNo: String?, destination: String?): String? {
        val cleanTrainNo = sanitize(trainNo)
        val cleanDestination = sanitize(destination)
        return when {
            cleanTrainNo != null && cleanDestination != null -> "$cleanTrainNo -> $cleanDestination"
            cleanTrainNo != null -> cleanTrainNo
            else -> cleanDestination
        }
    }

    private fun formatTaxiSubtitle(event: MyEvent, info: TransportInfo): String? {
        val payload = RuleMatchingEngine.resolvePayload(event.description, null)
        if (payload?.ruleId == RuleMatchingEngine.RULE_TAXI) {
            val parts = RuleMatchingEngine.splitFields(payload.payload, 3)
            val combined = joinParts(sanitize(parts.getOrNull(1)), sanitize(parts.getOrNull(0)))
            if (combined != null) return combined
        }
        val cleanFallback = sanitize(info.subDisplay) ?: return null
        val tokens = cleanFallback.split(" ").filter { it.isNotBlank() }
        return if (tokens.size >= 2) "${tokens.drop(1).joinToString(" ")} · ${tokens.first()}" else cleanFallback
    }

    private fun formatPickupSubtitle(platform: String?, location: String?): String? {
        val cleanPlatform = sanitize(platform)
        val cleanLocation = sanitize(location)
        return when {
            cleanPlatform != null && cleanLocation != null && cleanLocation.contains(cleanPlatform) -> cleanLocation
            cleanPlatform != null && cleanLocation != null -> "$cleanPlatform · $cleanLocation"
            cleanLocation != null -> cleanLocation
            else -> cleanPlatform
        }
    }

    private fun formatTimeRange(event: MyEvent): String? {
        val start = sanitize(event.startTime)
        val end = sanitize(event.endTime)
        return when {
            start != null && end != null -> "$start-$end"
            start != null -> start
            else -> end
        }
    }

    private fun extractDescription(description: String?): String? {
        val clean = sanitize(description) ?: return null
        val rulePayload = RuleMatchingEngine.resolvePayload(clean, null)
        if (rulePayload != null && rulePayload.ruleId != RuleMatchingEngine.RULE_GENERAL) return null
        val payload = rulePayload?.payload?.trim().orEmpty()
        val text = if (payload.isNotBlank()) payload else clean
        return text.substringBefore('\n').trim().ifBlank { null }
    }

    private fun summaryText(description: String?): String? {
        val clean = sanitize(description) ?: return null
        val rulePayload = RuleMatchingEngine.resolvePayload(clean, null)
        if (rulePayload != null && rulePayload.ruleId != RuleMatchingEngine.RULE_GENERAL) return null
        val payload = rulePayload?.payload?.trim().orEmpty()
        val text = if (payload.isNotBlank()) payload else clean
        return text.substringBefore('\n').trim().ifBlank { null }
    }

    private fun joinParts(vararg values: String?): String? {
        return values.mapNotNull { sanitize(it) }.distinct().takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun joinLines(vararg values: String?): String? {
        return values.mapNotNull { sanitize(it) }.distinct().takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun preferText(vararg values: String): String {
        return values.firstNotNullOfOrNull { sanitize(it) } ?: "提醒"
    }

    private fun sanitize(value: String?): String? {
        val clean = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (clean.equals("null", ignoreCase = true)) null else clean
    }
}
