package com.antgskds.calendarassistant.core.util

import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.MyEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class PickupInfo(
    val code: String,           // 取件码/取餐号
    val platform: String,       // 平台/品牌
    val location: String,       // 地点
    val isExpired: Boolean      // 是否已过期
)

object PickupUtils {
    private val TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")
    private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val PICKUP_PATTERN = Regex("【取(件|餐)】([^|]+)\\|([^|]+)(?:\\|(.*))?")

    fun parsePickupInfo(event: MyEvent): PickupInfo {
        val isExpired = isPickupExpired(event)
        val (code, platform, location) = parseMicroFormat(event.description)
        return PickupInfo(
            code = code.ifBlank { event.title },
            platform = platform,
            location = location.ifBlank { event.location },
            isExpired = isExpired
        )
    }

    fun parseMicroFormat(description: String): Triple<String, String, String> {
        if (description.isBlank()) return Triple("", "", "")
        val match = PICKUP_PATTERN.find(description)
        return if (match != null) {
            Triple(match.groupValues[2], match.groupValues[3], match.groupValues[4])
        } else {
            Triple("", "", "")
        }
    }

    fun isPickupExpired(event: MyEvent): Boolean {
        if (event.tag != EventTags.PICKUP) return false

        val now = LocalDateTime.now()
        val endDate = try {
            LocalDate.parse(event.endDate.toString(), DATE_FORMATTER)
        } catch (e: Exception) {
            return false
        }
        val endTime = try {
            LocalTime.parse(event.endTime, TIME_FORMATTER)
        } catch (e: Exception) {
            return false
        }

        val endDateTime = LocalDateTime.of(endDate, endTime)
        return now.isAfter(endDateTime)
    }

    fun isWithinGracePeriod(event: MyEvent, graceMinutes: Long = 30): Boolean {
        if (event.tag != EventTags.PICKUP) return false

        val now = LocalDateTime.now()
        val endDate = try {
            LocalDate.parse(event.endDate.toString(), DATE_FORMATTER)
        } catch (e: Exception) {
            return false
        }
        val endTime = try {
            LocalTime.parse(event.endTime, TIME_FORMATTER)
        } catch (e: Exception) {
            return false
        }

        val endDateTime = LocalDateTime.of(endDate, endTime)
        val graceEndDateTime = endDateTime.plusMinutes(graceMinutes)
        return now.isBefore(graceEndDateTime)
    }

    fun isPickupEvent(event: MyEvent): Boolean {
        return event.tag == EventTags.PICKUP
    }

    fun isActivePickup(event: MyEvent): Boolean {
        if (!isPickupEvent(event)) return false
        return isWithinGracePeriod(event)
    }
}
