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

// --- 渲染输出模型 ---

data class EventRenderModel(
    val eventId: String,
    val ruleId: String,
    val ruleName: String,
    val title: String,
    val subtitle: String?,
    val detail: String?,
    val timeRange: String?,
    val statusLabel: String?,
    val statusColor: StatusColor,
    val isTerminal: Boolean,
    val primaryAction: EventAction?,
    val undoAction: EventAction?,
    val iconResId: Int?,
    val actionIcon: ActionIconSpec,
    val isExpired: Boolean,
    val isInProgress: Boolean,
    val isComingSoon: Boolean,
    val isCourse: Boolean,
    val isRecurringParent: Boolean,
    val canEdit: Boolean,
    val isAggregatePickup: Boolean,
    val isAggregate: Boolean = false,
    val subItems: List<EventRenderModel> = emptyList()
)

data class EventAction(
    val actionLabel: String,
    val receiverAction: String,
    val isUndo: Boolean
)

enum class StatusColor { PRIMARY, SUCCESS, WARNING, MUTED }

enum class ActionIconType { UNDO, CHECKIN, RIDE, PICKUP, COMPLETE }

data class ActionIconSpec(
    val type: ActionIconType,
    val color: Long  // ARGB
)

// --- 事件渲染器 ---

/**
 * 统一事件渲染入口。
 * 所有 UI 组件（悬浮窗、列表、胶囊、通知）通过此对象获取渲染数据。
 * 所有方法均为同步，数据来自 RuleRegistry 内存缓存。
 */
object EventPresenter {

    // === 公开 API ===

    /**
     * 渲染单个事件为完整的展示数据模型。
     * UI 组件的唯一入口 — 不再需要关心规则判断逻辑。
     */
    fun present(context: Context, event: MyEvent): EventRenderModel {
        val ruleId = resolveRuleId(event)
        val ruleName = RuleRegistry.getRule(ruleId)?.name ?: ruleId

        // 时间状态
        val now = LocalDateTime.now()
        val isExpired = computeIsExpired(event, now)
        val isInProgress = computeIsInProgress(event, now)
        val isComingSoon = computeIsComingSoon(event, now)
        val isTerminal = event.isCompleted || event.isCheckedIn
        val isCourse = event.eventType == EventType.COURSE
        val isAggregatePickup = isFoodPickup(event.description)

        // 展示内容
        val (title, subtitle, detail) = resolveDisplayContent(event, ruleId, isExpired, isTerminal)

        // 状态标签
        val statusLabel = resolveStatusLabel(event, ruleId, isExpired, isInProgress, isComingSoon, isTerminal)
        val statusColor = resolveStatusColor(event, ruleId, isExpired, isInProgress, isComingSoon, isTerminal)

        // 动作
        val primaryAction = resolvePrimaryAction(ruleId, event, isExpired, isCourse)
        val undoAction = resolveUndoAction(ruleId, event, isTerminal)

        // 图标
        val iconResId = resolveIconResId(ruleId, event, context)
        val actionIcon = resolveActionIcon(event, ruleId, isTerminal)

        // 时间范围
        val timeRange = resolveTimeRange(event, ruleId, isCourse)

        return EventRenderModel(
            eventId = event.id,
            ruleId = ruleId,
            ruleName = ruleName,
            title = title,
            subtitle = subtitle,
            detail = detail,
            timeRange = timeRange,
            statusLabel = statusLabel,
            statusColor = statusColor,
            isTerminal = isTerminal,
            primaryAction = primaryAction,
            undoAction = undoAction,
            iconResId = iconResId,
            actionIcon = actionIcon,
            isExpired = isExpired,
            isInProgress = isInProgress,
            isComingSoon = isComingSoon,
            isCourse = isCourse,
            isRecurringParent = event.isRecurringParent,
            canEdit = !isCourse && !event.isRecurring && !event.isRecurringParent,
            isAggregatePickup = isAggregatePickup
        )
    }

    /**
     * 渲染单个事件为胶囊显示模型。
     */
    fun presentCapsule(context: Context, event: MyEvent, isExpired: Boolean): CapsuleDisplayModel {
        val model = present(context, event)
        return when (model.ruleId) {
            RuleMatchingEngine.RULE_TRAIN -> composeTrainCapsule(model, event, isExpired)
            RuleMatchingEngine.RULE_TAXI -> composeTaxiCapsule(model, event, isExpired)
            RuleMatchingEngine.RULE_PICKUP -> composePickupCapsule(model, event, isExpired)
            RuleMatchingEngine.RULE_FOOD -> composePickupCapsule(model, event, isExpired)
            RuleMatchingEngine.RULE_FLIGHT -> composeFlightCapsule(model, event, isExpired)
            RuleMatchingEngine.RULE_TICKET -> composeTicketCapsule(model, event, isExpired)
            RuleMatchingEngine.RULE_SENDER -> composeSenderCapsule(model, event, isExpired)
            else -> composeGeneralCapsule(model, event, isExpired)
        }
    }

    /**
     * 渲染多个事件为聚合胶囊（如多个取件码合并）。
     */
    fun presentCapsule(context: Context, events: List<MyEvent>): CapsuleDisplayModel {
        if (events.size == 1) {
            val event = events[0]
            return presentCapsule(context, event, computeIsExpired(event, LocalDateTime.now()))
        }

        val hasExpiredItems = events.any { computeIsExpired(it, LocalDateTime.now()) }
        val primaryText = if (hasExpiredItems) {
            "${events.size} 个待取 (含过期)"
        } else {
            "${events.size} 个待取事项"
        }

        val secondaryText = events
            .mapNotNull { evt ->
                val info = parsePickupInfo(evt)
                formatPickupSubtitle(info.platform, info.location)
            }
            .distinct()
            .take(2)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")

        val expandedText = events
            .take(5)
            .mapIndexed { index, evt ->
                val info = parsePickupInfo(evt)
                val isExpired = computeIsExpired(evt, LocalDateTime.now())
                val code = if (evt.isCompleted || isExpired) {
                    preferText(evt.title, info.code, "取件提醒")
                } else {
                    preferText(info.code, evt.title, "取件提醒")
                }
                val detail = formatPickupSubtitle(info.platform, info.location)
                if (detail.isNullOrBlank()) "${index + 1}. $code"
                else "${index + 1}. $code - $detail"
            }
            .joinToString("\n")
            .ifBlank { null }

        val action = if (events.any { !it.isCompleted && !computeIsExpired(it, LocalDateTime.now()) }) {
            CapsuleActionSpec(
                label = "已取",
                receiverAction = EventActionReceiver.ACTION_COMPLETE_SCHEDULE
            )
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

    // === ruleId 解析 ===

    fun resolveRuleId(event: MyEvent): String {
        return RuleMatchingEngine.resolvePayload(event)?.ruleId
            ?: event.tag.ifBlank { RuleMatchingEngine.RULE_GENERAL }
    }

    // === 展示内容解析 ===

    private fun resolveDisplayContent(
        event: MyEvent, ruleId: String, isExpired: Boolean, isTerminal: Boolean
    ): Triple<String, String?, String?> {
        return when (ruleId) {
            RuleMatchingEngine.RULE_TRAIN -> resolveTrainDisplay(event, isExpired)
            RuleMatchingEngine.RULE_TAXI -> resolveTaxiDisplay(event, isExpired, isTerminal)
            RuleMatchingEngine.RULE_PICKUP -> resolvePickupDisplay(event, isExpired)
            RuleMatchingEngine.RULE_FOOD -> resolvePickupDisplay(event, isExpired)
            RuleMatchingEngine.RULE_FLIGHT -> resolveFlightDisplay(event, isExpired)
            RuleMatchingEngine.RULE_TICKET -> resolveTicketDisplay(event, isExpired)
            RuleMatchingEngine.RULE_SENDER -> resolveSenderDisplay(event, isExpired)
            else -> resolveGeneralDisplay(event)
        }
    }

    private fun resolveTrainDisplay(event: MyEvent, isExpired: Boolean): Triple<String, String?, String?> {
        val info = parseTransport(event)
        // 优先使用模板标题，兜底用解析结果
        val templateTitle = RuleDisplayTemplateResolver.renderTitle(event)
        val title = templateTitle?.takeIf { it.isNotBlank() } ?: when {
            info.isCheckedIn -> info.mainDisplay.ifBlank { event.title }
            isExpired -> event.title
            else -> info.mainDisplay.ifBlank { "待检票" }
        }
        // subDisplay = 车次号，location = 目的地/站点
        // 如果 location 已包含在 subDisplay 中则不重复拼接
        val destination = event.location.trim().takeIf { it.isNotBlank() && it != info.subDisplay }
        val subtitle = formatTrainSubtitle(info.subDisplay, destination)
        return Triple(title, subtitle, null)
    }

    private fun resolveTaxiDisplay(event: MyEvent, isExpired: Boolean, isTerminal: Boolean): Triple<String, String?, String?> {
        val info = parseTransport(event)
        val templateTitle = RuleDisplayTemplateResolver.renderTitle(event)
        val title = (templateTitle?.takeIf { it.isNotBlank() } ?: when {
            isTerminal || isExpired -> event.title
            else -> info.mainDisplay.ifBlank { event.title }
        })
        val subtitle = formatTaxiSubtitle(event, info)
        // detail 不重复包含已在 subtitle 中出现的信息
        val locationExtra = event.location.trim().takeIf { loc ->
            loc.isNotBlank() && subtitle != null && !subtitle.contains(loc)
        }
        val detail = listOfNotNull(subtitle, locationExtra).joinToString("\n").ifBlank { null }
        return Triple(title, subtitle, detail)
    }

    private fun resolvePickupDisplay(event: MyEvent, isExpired: Boolean): Triple<String, String?, String?> {
        val info = parsePickupInfo(event)
        val templateTitle = RuleDisplayTemplateResolver.renderTitle(event)
        val title = (templateTitle?.takeIf { it.isNotBlank() } ?: when {
            event.isCompleted || isExpired -> preferText(event.title, info.code, "取件提醒")
            else -> preferText(info.code, event.title, "取件提醒")
        })
        val locationExtra = info.location.trim().takeIf { loc ->
            loc.isNotBlank() && info.platform.isNotBlank() && !loc.contains(info.platform)
        }
        val subtitle = formatPickupSubtitle(info.platform, info.location)
        val detail = locationExtra?.takeIf { it.isNotBlank() }
        return Triple(title, subtitle, detail)
    }

    private fun resolveFlightDisplay(event: MyEvent, isExpired: Boolean): Triple<String, String?, String?> {
        val payload = RuleMatchingEngine.extractPayloadText(event.description).orEmpty()
        val fields = RuleMatchingEngine.splitFields(payload, 3)
        val templateTitle = RuleDisplayTemplateResolver.renderTitle(event)
        val flightNo = fields.getOrNull(0).orEmpty()
        val gate = fields.getOrNull(1).orEmpty()
        val seat = fields.getOrNull(2).orEmpty()
        val title = templateTitle?.takeIf { it.isNotBlank() }
            ?: preferText(flightNo, event.title, "航班提醒")
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
        val title = templateTitle?.takeIf { it.isNotBlank() } ?: when {
            event.isCompleted || isExpired -> preferText(event.title, info.code, "取票提醒")
            else -> preferText(info.code, event.title, "取票提醒")
        }
        val subtitle = formatPickupSubtitle(info.platform, info.location)
        return Triple(title, subtitle, null)
    }

    private fun resolveSenderDisplay(event: MyEvent, isExpired: Boolean): Triple<String, String?, String?> {
        val info = parsePickupInfo(event)
        val templateTitle = RuleDisplayTemplateResolver.renderTitle(event)
        val title = templateTitle?.takeIf { it.isNotBlank() }
            ?: preferText(info.code, event.title, "寄件提醒")
        val subtitle = formatPickupSubtitle(info.platform, info.location)
        return Triple(title, subtitle, null)
    }

    private fun resolveGeneralDisplay(event: MyEvent): Triple<String, String?, String?> {
        val templateTitle = RuleDisplayTemplateResolver.renderTitle(event)
        val title = templateTitle ?: preferText(event.title, "日程提醒")
        val location = event.location.trim().takeIf { it.isNotBlank() }
        val desc = extractDescription(event.description)
        val subtitle = location ?: desc
        val detail = if (location != null && desc != null && desc != location) desc else null
        return Triple(title, subtitle, detail)
    }

    // === 状态标签 ===

    private fun resolveStatusLabel(
        event: MyEvent, ruleId: String,
        isExpired: Boolean, isInProgress: Boolean, isComingSoon: Boolean, isTerminal: Boolean
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

    private fun resolveStatusColor(
        event: MyEvent, ruleId: String,
        isExpired: Boolean, isInProgress: Boolean, isComingSoon: Boolean, isTerminal: Boolean
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

    // === 动作 ===

    private fun resolvePrimaryAction(
        ruleId: String, event: MyEvent, isExpired: Boolean, isCourse: Boolean
    ): EventAction? {
        if (event.isCompleted || event.isCheckedIn || isExpired) return null
        if (isCourse) return null
        if (event.isRecurringParent) return null

        val defaults = RuleActionDefaults.defaultsFor(ruleId)
        val receiverAction = when (ruleId) {
            RuleMatchingEngine.RULE_TRAIN -> EventActionReceiver.ACTION_CHECKIN
            else -> EventActionReceiver.ACTION_COMPLETE_SCHEDULE
        }
        return EventAction(
            actionLabel = defaults.actionLabel,
            receiverAction = receiverAction,
            isUndo = false
        )
    }

    private fun resolveUndoAction(ruleId: String, event: MyEvent, isTerminal: Boolean): EventAction? {
        if (!isTerminal) return null

        val defaults = RuleActionDefaults.defaultsFor(ruleId)
        return EventAction(
            actionLabel = defaults.undoLabel,
            receiverAction = EventActionReceiver.ACTION_COMPLETE_SCHEDULE,
            isUndo = true
        )
    }

    // === 图标 ===

    private fun resolveIconResId(ruleId: String, event: MyEvent, context: Context): Int? {
        // 优先从用户自定义缓存取
        RuleRegistry.getCustomCapsuleIconResId(ruleId)?.let { return it }

        // 其次从默认缓存取
        RuleRegistry.getIconResId(ruleId)?.let { return it }

        // 硬编码回退
        return when (ruleId) {
            RuleMatchingEngine.RULE_PICKUP -> {
                if (isFoodPickup(event.description)) R.drawable.ic_stat_food else R.drawable.ic_stat_package
            }
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

    private fun resolveActionIcon(event: MyEvent, ruleId: String, isTerminal: Boolean): ActionIconSpec {
        return if (isTerminal) {
            ActionIconSpec(ActionIconType.UNDO, 0xFFFFA726)
        } else when (ruleId) {
            RuleMatchingEngine.RULE_TRAIN -> ActionIconSpec(ActionIconType.CHECKIN, 0xFF4CAF50)
            RuleMatchingEngine.RULE_TAXI -> ActionIconSpec(ActionIconType.RIDE, 0xFFFF9800)
            RuleMatchingEngine.RULE_PICKUP -> ActionIconSpec(ActionIconType.PICKUP, 0xFF2196F3)
            else -> ActionIconSpec(ActionIconType.COMPLETE, 0xFF4CAF50)
        }
    }

    // === 时间范围 ===

    private fun resolveTimeRange(event: MyEvent, ruleId: String, isCourse: Boolean): String? {
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

    // === 胶囊组合方法 ===

    private fun composeTrainCapsule(model: EventRenderModel, event: MyEvent, isExpired: Boolean): CapsuleDisplayModel {
        val info = parseTransport(event)
        val secondaryText = formatTrainSubtitle(info.subDisplay, event.location)
        val action = if (!info.isCheckedIn && !isExpired) {
            CapsuleActionSpec(label = "已检票", receiverAction = EventActionReceiver.ACTION_CHECKIN)
        } else null
        return CapsuleDisplayModel(
            shortText = model.title, primaryText = model.title,
            secondaryText = secondaryText, expandedText = secondaryText,
            action = action
        )
    }

    private fun composeTaxiCapsule(model: EventRenderModel, event: MyEvent, isExpired: Boolean): CapsuleDisplayModel {
        val info = parseTransport(event)
        val secondaryText = formatTaxiSubtitle(event, info) ?: "网约车"
        val expandedText = joinLines(secondaryText, sanitize(event.location))
        val action = if (!event.isCompleted && !isExpired) {
            CapsuleActionSpec(label = "已用车", receiverAction = EventActionReceiver.ACTION_COMPLETE_SCHEDULE)
        } else null
        return CapsuleDisplayModel(
            shortText = model.title, primaryText = model.title,
            secondaryText = secondaryText, expandedText = expandedText,
            action = action
        )
    }

    private fun composePickupCapsule(model: EventRenderModel, event: MyEvent, isExpired: Boolean): CapsuleDisplayModel {
        val info = parsePickupInfo(event)
        val secondaryText = formatPickupSubtitle(info.platform, info.location)
        val expandedText = joinLines(secondaryText, summaryText(event.description))
        val action = if (!event.isCompleted && !isExpired) {
            CapsuleActionSpec(label = "已取", receiverAction = EventActionReceiver.ACTION_COMPLETE_SCHEDULE)
        } else null
        return CapsuleDisplayModel(
            shortText = model.title, primaryText = model.title,
            secondaryText = secondaryText, expandedText = expandedText,
            tapOpensPickupList = true, action = action
        )
    }

    private fun composeFlightCapsule(model: EventRenderModel, event: MyEvent, isExpired: Boolean): CapsuleDisplayModel {
        val secondaryText = model.subtitle
        val expandedText = joinLines(secondaryText, sanitize(event.location))
        val action = if (!event.isCompleted && !isExpired) {
            CapsuleActionSpec(label = "已登机", receiverAction = EventActionReceiver.ACTION_COMPLETE_SCHEDULE)
        } else null
        return CapsuleDisplayModel(
            shortText = model.title, primaryText = model.title,
            secondaryText = secondaryText, expandedText = expandedText,
            action = action
        )
    }

    private fun composeTicketCapsule(model: EventRenderModel, event: MyEvent, isExpired: Boolean): CapsuleDisplayModel {
        val info = parsePickupInfo(event)
        val secondaryText = formatPickupSubtitle(info.platform, info.location)
        val expandedText = joinLines(secondaryText, summaryText(event.description))
        val action = if (!event.isCompleted && !isExpired) {
            CapsuleActionSpec(label = "已取", receiverAction = EventActionReceiver.ACTION_COMPLETE_SCHEDULE)
        } else null
        return CapsuleDisplayModel(
            shortText = model.title, primaryText = model.title,
            secondaryText = secondaryText, expandedText = expandedText,
            action = action
        )
    }

    private fun composeSenderCapsule(model: EventRenderModel, event: MyEvent, isExpired: Boolean): CapsuleDisplayModel {
        val info = parsePickupInfo(event)
        val secondaryText = formatPickupSubtitle(info.platform, info.location)
        val expandedText = joinLines(secondaryText, summaryText(event.description))
        val action = if (!event.isCompleted && !isExpired) {
            CapsuleActionSpec(label = "已寄件", receiverAction = EventActionReceiver.ACTION_COMPLETE_SCHEDULE)
        } else null
        return CapsuleDisplayModel(
            shortText = model.title, primaryText = model.title,
            secondaryText = secondaryText, expandedText = expandedText,
            action = action
        )
    }

    private fun composeGeneralCapsule(model: EventRenderModel, event: MyEvent, isExpired: Boolean): CapsuleDisplayModel {
        val primaryText = preferText(model.title, "日程提醒")
        val detailText = sanitize(event.location) ?: summaryText(event.description)
        val timeText = formatTimeRange(event)
        val secondaryText = detailText ?: timeText
        val tertiaryText = if (detailText != null) timeText else null
        val expandedText = joinLines(
            detailText,
            tertiaryText,
            summaryText(event.description)?.takeUnless { it == detailText }
        )
        val action = if (!event.isCompleted && !isExpired && event.eventType != EventType.COURSE) {
            CapsuleActionSpec(label = "已完成", receiverAction = EventActionReceiver.ACTION_COMPLETE_SCHEDULE)
        } else null
        return CapsuleDisplayModel(
            shortText = primaryText, primaryText = primaryText,
            secondaryText = secondaryText, tertiaryText = tertiaryText,
            expandedText = expandedText, action = action
        )
    }

    // === 交通解析 (原 TransportUtils) ===

    private data class TransportInfo(
        val type: String,      // "train" / "taxi" / "none"
        val mainDisplay: String,
        val subDisplay: String,
        val isCheckedIn: Boolean = false
    )

    private fun parseTransport(event: MyEvent): TransportInfo {
        val description = event.description
        if (description.isBlank()) return TransportInfo("none", "", "", false)

        val cleanDesc = description
        val isCheckedIn = event.isCheckedIn ?: false
        val isRideCompleted = event.isCompleted

        val payload = RuleMatchingEngine.resolvePayload(cleanDesc, null)
        return when (payload?.ruleId) {
            RuleMatchingEngine.RULE_TRAIN -> parseTrainPayload(payload.payload, isCheckedIn)
            RuleMatchingEngine.RULE_TAXI -> parseTaxiPayload(payload.payload, isRideCompleted)
            else -> TransportInfo("none", "", "", false)
        }
    }

    private fun parseTrainPayload(payload: String, isCheckedIn: Boolean): TransportInfo {
        val parts = RuleMatchingEngine.splitFields(payload, 3)
        return when {
            parts.size >= 3 -> {
                val gate = parts[1].ifBlank { "" }
                val seat = parts[2].ifBlank { "" }
                val trainNo = parts[0].ifBlank { "" }
                if (isCheckedIn) {
                    TransportInfo("train", seat, trainNo, true)
                } else {
                    TransportInfo("train", if (gate.isNotBlank()) "$gate 检票" else "等待检票", trainNo)
                }
            }
            parts.size == 2 -> {
                val trainNo = parts[0].ifBlank { "" }
                val gateOrSeat = parts[1]
                if (isCheckedIn) {
                    TransportInfo("train", gateOrSeat, trainNo, true)
                } else {
                    TransportInfo("train", if (gateOrSeat.isNotBlank()) "$gateOrSeat 检票" else "等待检票", trainNo)
                }
            }
            parts.size == 1 -> TransportInfo("train", "等待检票", parts[0])
            else -> TransportInfo("none", "", "", false)
        }
    }

    private fun parseTaxiPayload(payload: String, isRideCompleted: Boolean): TransportInfo {
        val parts = RuleMatchingEngine.splitFields(payload, 3)
        return when {
            parts.size >= 3 -> {
                val color = parts[0].ifBlank { "" }
                val carModel = parts[1].ifBlank { "" }
                val licensePlate = parts[2].ifBlank { "" }
                TransportInfo("taxi", licensePlate, "$color $carModel", isRideCompleted)
            }
            parts.size == 2 -> {
                val carModel = parts[0].ifBlank { "" }
                val licensePlate = parts[1].ifBlank { "" }
                TransportInfo("taxi", licensePlate, carModel, isRideCompleted)
            }
            parts.size == 1 -> TransportInfo("taxi", parts[0], "", isRideCompleted)
            else -> TransportInfo("none", "", "", false)
        }
    }

    // === 取件解析 (原 PickupUtils) ===

    private data class PickupInfo(
        val code: String,
        val platform: String,
        val location: String
    )

    private fun parsePickupInfo(event: MyEvent): PickupInfo {
        val (code, platform, location) = parsePickupMicroFormat(event.description)
        return PickupInfo(
            code = code.ifBlank { event.title },
            platform = platform,
            location = location.ifBlank { event.location }
        )
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
        return if (match != null) {
            Triple(match.groupValues[2], match.groupValues[3], match.groupValues[4])
        } else {
            Triple("", "", "")
        }
    }

    private fun isPickupExpired(event: MyEvent): Boolean {
        val now = LocalDateTime.now()
        val endDate = try {
            LocalDate.parse(event.endDate.toString(), DateTimeFormatter.ISO_LOCAL_DATE)
        } catch (e: Exception) {
            return false
        }
        val endTime = try {
            LocalTime.parse(event.endTime, DateTimeFormatter.ofPattern("HH:mm"))
        } catch (e: Exception) {
            return false
        }
        return now.isAfter(LocalDateTime.of(endDate, endTime))
    }

    // === 时间计算 ===

    private fun computeIsExpired(event: MyEvent, now: LocalDateTime): Boolean {
        try {
            val endDate = LocalDate.parse(event.endDate.toString(), DateTimeFormatter.ISO_LOCAL_DATE)
            val endTime = LocalTime.parse(event.endTime, DateTimeFormatter.ofPattern("HH:mm"))
            return now.isAfter(LocalDateTime.of(endDate, endTime))
        } catch (e: Exception) {
            return false
        }
    }

    private fun computeIsInProgress(event: MyEvent, now: LocalDateTime): Boolean {
        try {
            val startDate = LocalDate.parse(event.startDate.toString(), DateTimeFormatter.ISO_LOCAL_DATE)
            val startTime = LocalTime.parse(event.startTime, DateTimeFormatter.ofPattern("HH:mm"))
            val endDate = LocalDate.parse(event.endDate.toString(), DateTimeFormatter.ISO_LOCAL_DATE)
            val endTime = LocalTime.parse(event.endTime, DateTimeFormatter.ofPattern("HH:mm"))
            return !now.isBefore(LocalDateTime.of(startDate, startTime)) &&
                    !now.isAfter(LocalDateTime.of(endDate, endTime))
        } catch (e: Exception) {
            return false
        }
    }

    private fun computeIsComingSoon(event: MyEvent, now: LocalDateTime): Boolean {
        try {
            val startDate = LocalDate.parse(event.startDate.toString(), DateTimeFormatter.ISO_LOCAL_DATE)
            val startTime = LocalTime.parse(event.startTime, DateTimeFormatter.ofPattern("HH:mm"))
            val eventStart = LocalDateTime.of(startDate, startTime)
            val minutesBefore = 30L
            return now.isBefore(eventStart) && now.isAfter(eventStart.minusMinutes(minutesBefore))
        } catch (e: Exception) {
            return false
        }
    }

    // === 格式化工具 ===

    private fun isFoodPickup(description: String?): Boolean {
        return description?.startsWith("【取餐】") == true
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
            val color = parts.getOrNull(0)
            val model = parts.getOrNull(1)
            val combined = joinParts(sanitize(model), sanitize(color))
            if (combined != null) return combined
        }
        val cleanFallback = sanitize(info.subDisplay)
        if (cleanFallback == null) return null
        val tokens = cleanFallback.split(" ").filter { it.isNotBlank() }
        return when {
            tokens.size >= 2 -> "${tokens.drop(1).joinToString(" ")} · ${tokens.first()}"
            else -> cleanFallback
        }
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
        return values.mapNotNull { sanitize(it) }.distinct()
            .takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    private fun joinLines(vararg values: String?): String? {
        return values.mapNotNull { sanitize(it) }.distinct()
            .takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun preferText(vararg values: String): String {
        return values.firstNotNullOfOrNull { sanitize(it) } ?: "提醒"
    }

    private fun sanitize(value: String?): String? {
        val clean = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (clean.equals("null", ignoreCase = true)) null else clean
    }
}
