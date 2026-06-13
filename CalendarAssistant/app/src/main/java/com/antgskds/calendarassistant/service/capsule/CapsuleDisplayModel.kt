package com.antgskds.calendarassistant.service.capsule

data class CapsuleActionSpec(
    val label: String,
    val receiverAction: String,
    val extraLongKey: String? = null,
    val extraLongValue: Long? = null
)

data class CapsuleDisplayModel(
    val shortText: String,
    val primaryText: String,
    val secondaryText: String? = null,
    val tertiaryText: String? = null,
    val expandedText: String? = null,
    val tapOpensPickupList: Boolean = false,
    val action: CapsuleActionSpec? = null
)
