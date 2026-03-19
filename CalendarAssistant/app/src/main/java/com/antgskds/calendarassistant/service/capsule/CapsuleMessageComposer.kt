package com.antgskds.calendarassistant.service.capsule

import com.antgskds.calendarassistant.core.util.PickupUtils
import com.antgskds.calendarassistant.core.util.TransportUtils
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.EventType
import com.antgskds.calendarassistant.data.model.MyEvent
import com.antgskds.calendarassistant.service.receiver.EventActionReceiver

object CapsuleMessageComposer {

    fun composeNetworkSpeed(speed: NetworkSpeedMonitor.NetworkSpeed): CapsuleDisplayModel {
        return CapsuleDisplayModel(
            shortText = speed.formattedSpeed,
            primaryText = speed.formattedSpeed,
            secondaryText = "下载速度",
            expandedText = "下载速度"
        )
    }

    fun composeOcrProgress(title: String, content: String): CapsuleDisplayModel {
        val primary = sanitize(title) ?: "正在分析"
        val secondary = sanitize(content)
        return CapsuleDisplayModel(
            shortText = primary,
            primaryText = primary,
            secondaryText = secondary,
            expandedText = secondary
        )
    }

    fun composeOcrResult(title: String, content: String): CapsuleDisplayModel {
        val primary = sanitize(title) ?: "分析完成"
        val secondary = sanitize(content)
        val expanded = joinLines(secondary)
        return CapsuleDisplayModel(
            shortText = primary,
            primaryText = primary,
            secondaryText = secondary,
            expandedText = expanded
        )
    }

    fun composeSchedule(
        event: MyEvent,
        isExpired: Boolean
    ): CapsuleDisplayModel {
        return when (event.tag) {
            EventTags.TRAIN -> composeTrain(event, isExpired)
            EventTags.TAXI -> composeTaxi(event, isExpired)
            else -> composeGeneral(event, isExpired)
        }
    }

    fun composePickup(
        event: MyEvent,
        isExpired: Boolean
    ): CapsuleDisplayModel {
        val pickupInfo = PickupUtils.parsePickupInfo(event)
        val shortText = if (event.isCompleted || isExpired) {
            preferText(event.title, pickupInfo.code, "取件提醒")
        } else {
            preferText(pickupInfo.code, event.title, "取件提醒")
        }
        val secondaryText = formatPickupSecondary(pickupInfo.platform, pickupInfo.location)
        val expandedText = joinLines(
            secondaryText,
            summaryText(event.description)
        )
        val action = if (!event.isCompleted && !isExpired) {
            CapsuleActionSpec(
                label = "已取",
                receiverAction = EventActionReceiver.ACTION_COMPLETE_SCHEDULE
            )
        } else {
            null
        }

        return CapsuleDisplayModel(
            shortText = shortText,
            primaryText = shortText,
            secondaryText = secondaryText,
            expandedText = expandedText,
            tapOpensPickupList = true,
            action = action
        )
    }

    fun composeAggregatePickup(
        pickupEvents: List<MyEvent>,
        hasExpiredItems: Boolean
    ): CapsuleDisplayModel {
        val primaryText = if (hasExpiredItems) {
            "${pickupEvents.size} 个待取 (含过期)"
        } else {
            "${pickupEvents.size} 个待取事项"
        }

        val secondaryText = pickupEvents
            .map { PickupUtils.parsePickupInfo(it) }
            .mapNotNull { formatPickupSecondary(it.platform, it.location) }
            .distinct()
            .take(2)
            .takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")

        val expandedText = pickupEvents
            .take(5)
            .mapIndexed { index, event ->
                val pickupInfo = PickupUtils.parsePickupInfo(event)
                val code = if (event.isCompleted || PickupUtils.isPickupExpired(event)) {
                    preferText(event.title, pickupInfo.code, "取件提醒")
                } else {
                    preferText(pickupInfo.code, event.title, "取件提醒")
                }
                val detail = formatPickupSecondary(pickupInfo.platform, pickupInfo.location)
                if (detail.isNullOrBlank()) {
                    "${index + 1}. $code"
                } else {
                    "${index + 1}. $code - $detail"
                }
            }
            .joinToString("\n")
            .ifBlank { null }

        val action = if (pickupEvents.any { !it.isCompleted && !PickupUtils.isPickupExpired(it) }) {
            CapsuleActionSpec(
                label = "已取",
                receiverAction = EventActionReceiver.ACTION_COMPLETE_SCHEDULE
            )
        } else {
            null
        }

        return CapsuleDisplayModel(
            shortText = primaryText,
            primaryText = primaryText,
            secondaryText = secondaryText,
            expandedText = expandedText,
            tapOpensPickupList = true,
            action = action
        )
    }

    private fun composeTrain(event: MyEvent, isExpired: Boolean): CapsuleDisplayModel {
        val transportInfo = TransportUtils.parse(event.description, event.isCheckedIn)
        val shortText = when {
            transportInfo.isCheckedIn -> preferText(transportInfo.mainDisplay, event.title, "出行提醒")
            isExpired -> preferText(event.title, transportInfo.mainDisplay, "出行提醒")
            else -> preferText(transportInfo.mainDisplay, event.title, "待检票")
        }
        val secondaryText = formatTrainSecondary(transportInfo.subDisplay, event.location)
        val action = if (!transportInfo.isCheckedIn && !isExpired) {
            CapsuleActionSpec(
                label = "已检票",
                receiverAction = EventActionReceiver.ACTION_CHECKIN
            )
        } else {
            null
        }

        return CapsuleDisplayModel(
            shortText = shortText,
            primaryText = shortText,
            secondaryText = secondaryText,
            expandedText = secondaryText,
            action = action
        )
    }

    private fun composeTaxi(event: MyEvent, isExpired: Boolean): CapsuleDisplayModel {
        val transportInfo = TransportUtils.parse(event.description, event.isCheckedIn)
        val shortText = if (event.isCompleted || isExpired) {
            preferText(event.title, transportInfo.mainDisplay, "出行提醒")
        } else {
            preferText(transportInfo.mainDisplay, event.title, "出行提醒")
        }
        val secondaryText = formatTaxiSecondary(event.description, transportInfo.subDisplay) ?: "网约车"
        val expandedText = joinLines(
            secondaryText,
            sanitize(event.location)
        )
        val action = if (!event.isCompleted && !isExpired) {
            CapsuleActionSpec(
                label = "已用车",
                receiverAction = EventActionReceiver.ACTION_COMPLETE_SCHEDULE
            )
        } else {
            null
        }

        return CapsuleDisplayModel(
            shortText = shortText,
            primaryText = shortText,
            secondaryText = secondaryText,
            expandedText = expandedText,
            action = action
        )
    }

    private fun composeGeneral(event: MyEvent, isExpired: Boolean): CapsuleDisplayModel {
        val primaryText = preferText(event.title, "日程提醒")
        val detailText = sanitize(event.location) ?: summaryText(event.description)
        val timeText = formatTimeRange(event)
        val secondaryText = detailText ?: timeText
        val tertiaryText = if (detailText != null) timeText else null
        val expandedText = joinLines(
            detailText,
            tertiaryText,
            summaryText(event.description)?.takeUnless { it == detailText }
        )
        val action = if (!event.isCompleted && !isExpired) {
            CapsuleActionSpec(
                label = if (event.eventType == EventType.COURSE) "已结束" else "已完成",
                receiverAction = EventActionReceiver.ACTION_COMPLETE_SCHEDULE
            )
        } else {
            null
        }

        return CapsuleDisplayModel(
            shortText = primaryText,
            primaryText = primaryText,
            secondaryText = secondaryText,
            tertiaryText = tertiaryText,
            expandedText = expandedText,
            action = action
        )
    }

    private fun formatPickupSecondary(platform: String?, location: String?): String? {
        val cleanPlatform = sanitize(platform)
        val cleanLocation = sanitize(location)
        return when {
            cleanPlatform != null && cleanLocation != null && cleanLocation.contains(cleanPlatform) -> cleanLocation
            cleanPlatform != null && cleanLocation != null -> "$cleanPlatform · $cleanLocation"
            cleanLocation != null -> cleanLocation
            else -> cleanPlatform
        }
    }

    private fun formatTrainSecondary(trainNo: String?, destination: String?): String? {
        val cleanTrainNo = sanitize(trainNo)
        val cleanDestination = sanitize(destination)
        return when {
            cleanTrainNo != null && cleanDestination != null -> "$cleanTrainNo -> $cleanDestination"
            cleanTrainNo != null -> cleanTrainNo
            else -> cleanDestination
        }
    }

    private fun formatTaxiSecondary(description: String, fallback: String?): String? {
        val payload = extractTaggedPayload(description, "用车")
        if (payload != null) {
            val parts = payload.split("|").map { it.trim() }
            val color = parts.getOrNull(0)
            val model = parts.getOrNull(1)
            val combined = joinParts(sanitize(model), sanitize(color), separator = " · ")
            if (combined != null) {
                return combined
            }
        }

        val cleanFallback = sanitize(fallback)
        if (cleanFallback == null) {
            return null
        }

        val tokens = cleanFallback.split(" ").filter { it.isNotBlank() }
        return when {
            tokens.size >= 2 -> "${tokens.drop(1).joinToString(" ")} · ${tokens.first()}"
            else -> cleanFallback
        }
    }

    private fun extractTaggedPayload(description: String, tag: String): String? {
        return when {
            description.contains("【$tag】") -> description.substringAfter("【$tag】").trim()
            description.contains("[$tag]") -> description.substringAfter("[$tag]").trim()
            else -> null
        }
    }

    private fun summaryText(description: String?): String? {
        val clean = sanitize(description) ?: return null
        if (clean.startsWith("【列车】") || clean.startsWith("【用车】") || clean.startsWith("【取件】") || clean.startsWith("【取餐】")) {
            return null
        }
        return clean.substringBefore('\n').trim().ifBlank { null }
    }

    private fun formatTimeRange(event: MyEvent): String? {
        if (event.tag != EventTags.GENERAL && event.eventType != EventType.COURSE) {
            return null
        }

        val start = sanitize(event.startTime)
        val end = sanitize(event.endTime)
        return when {
            start != null && end != null -> "$start-$end"
            start != null -> start
            else -> end
        }
    }

    private fun joinParts(vararg values: String?, separator: String = " · "): String? {
        val filtered = values.mapNotNull { sanitize(it) }.distinct()
        return filtered.takeIf { it.isNotEmpty() }?.joinToString(separator)
    }

    private fun joinLines(vararg values: String?): String? {
        val filtered = values.mapNotNull { sanitize(it) }.distinct()
        return filtered.takeIf { it.isNotEmpty() }?.joinToString("\n")
    }

    private fun preferText(vararg values: String): String {
        return values.firstNotNullOfOrNull { sanitize(it) } ?: "提醒"
    }

    private fun sanitize(value: String?): String? {
        val clean = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return if (clean.equals("null", ignoreCase = true)) null else clean
    }
}
