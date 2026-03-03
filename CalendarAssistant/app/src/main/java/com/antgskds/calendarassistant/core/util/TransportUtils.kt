package com.antgskds.calendarassistant.core.util

enum class TransportType {
    NONE,
    TRAIN,
    RIDE
}

data class TransportInfo(
    val type: TransportType,
    val mainDisplay: String,
    val subDisplay: String,
    val isCheckedIn: Boolean = false,
    val rawDescription: String = ""
)

object TransportUtils {
    private const val CHECKED_IN_SUFFIX = "(已检票)"
    private const val RIDE_COMPLETED_SUFFIX = "(已用车)"

    fun parse(description: String): TransportInfo {
        if (description.isBlank()) {
            return TransportInfo(TransportType.NONE, "", "", false, description)
        }

        val cleanDesc = description.removeSuffix(CHECKED_IN_SUFFIX).removeSuffix(RIDE_COMPLETED_SUFFIX)
        val isCheckedIn = description.endsWith(CHECKED_IN_SUFFIX)
        val isRideCompleted = description.endsWith(RIDE_COMPLETED_SUFFIX)

        val trainPattern = Regex("""【列车|列车""")
        val ridePattern = Regex("""【用车|用车""")

        return when {
            trainPattern.containsMatchIn(cleanDesc) -> parseTrain(cleanDesc, isCheckedIn)
            ridePattern.containsMatchIn(cleanDesc) -> parseRide(cleanDesc, isRideCompleted)
            else -> TransportInfo(TransportType.NONE, "", "", false, description)
        }
    }

    private fun parseTrain(description: String, isCheckedIn: Boolean): TransportInfo {
        val content = extractContent(description, "列车") ?: return TransportInfo(TransportType.NONE, "", "", false, description)
        val parts = content.split("|").map { it.trim() }

        return when {
            parts.size >= 3 -> {
                val gate = parts[1].ifBlank { "" }
                val seat = parts[2].ifBlank { "" }
                val trainNo = parts[0].ifBlank { "" }

                if (isCheckedIn) {
                    TransportInfo(
                        type = TransportType.TRAIN,
                        mainDisplay = seat,
                        subDisplay = trainNo,
                        isCheckedIn = true,
                        rawDescription = description
                    )
                } else {
                    val mainDisplay = if (gate.isNotBlank()) "$gate 检票" else "等待检票"
                    TransportInfo(
                        type = TransportType.TRAIN,
                        mainDisplay = mainDisplay,
                        subDisplay = trainNo,
                        isCheckedIn = false,
                        rawDescription = description
                    )
                }
            }
            parts.size == 2 -> {
                val trainNo = parts[0].ifBlank { "" }
                val gateOrSeat = parts[1]

                if (isCheckedIn) {
                    TransportInfo(
                        type = TransportType.TRAIN,
                        mainDisplay = gateOrSeat,
                        subDisplay = trainNo,
                        isCheckedIn = true,
                        rawDescription = description
                    )
                } else {
                    val mainDisplay = if (gateOrSeat.isNotBlank()) "$gateOrSeat 检票" else "等待检票"
                    TransportInfo(
                        type = TransportType.TRAIN,
                        mainDisplay = mainDisplay,
                        subDisplay = trainNo,
                        isCheckedIn = false,
                        rawDescription = description
                    )
                }
            }
            parts.size == 1 -> {
                TransportInfo(
                    type = TransportType.TRAIN,
                    mainDisplay = "等待检票",
                    subDisplay = parts[0],
                    isCheckedIn = false,
                    rawDescription = description
                )
            }
            else -> TransportInfo(TransportType.NONE, "", "", false, description)
        }
    }

    private fun parseRide(description: String, isRideCompleted: Boolean): TransportInfo {
        val content = extractContent(description, "用车") ?: return TransportInfo(TransportType.NONE, "", "", false, description)
        val parts = content.split("|").map { it.trim() }

        return when {
            parts.size >= 2 -> {
                val carModel = parts[0].ifBlank { "" }
                val licensePlate = parts[1].ifBlank { "" }

                TransportInfo(
                    type = TransportType.RIDE,
                    mainDisplay = licensePlate,
                    subDisplay = carModel,
                    isCheckedIn = isRideCompleted,
                    rawDescription = description
                )
            }
            parts.size == 1 -> {
                TransportInfo(
                    type = TransportType.RIDE,
                    mainDisplay = parts[0],
                    subDisplay = "",
                    isCheckedIn = isRideCompleted,
                    rawDescription = description
                )
            }
            else -> TransportInfo(TransportType.NONE, "", "", false, description)
        }
    }

    private fun extractContent(description: String, anchor: String): String? {
        val cnPattern = "【$anchor】"
        val enPattern = "[$anchor]"

        return when {
            description.contains(cnPattern) -> {
                description.substringAfter(cnPattern).trim()
            }
            description.contains(enPattern) -> {
                description.substringAfter(enPattern).trim()
            }
            else -> null
        }
    }
}
