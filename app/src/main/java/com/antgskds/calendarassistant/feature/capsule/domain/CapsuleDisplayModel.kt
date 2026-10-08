package com.antgskds.calendarassistant.feature.capsule.domain

data class CapsuleActionSpec(
    val label: String,
    val receiverAction: String,
    val extraLongKey: String? = null,
    val extraLongValue: Long? = null,
    val stringExtras: Map<String, String> = emptyMap(),
    val openQuickMemoId: Long? = null,
)

data class CapsuleDisplayModel(
    val shortText: String,
    val primaryText: String,
    val secondaryText: String? = null,
    val tertiaryText: String? = null,
    val expandedText: String? = null,
    val isCompact: Boolean = false,
    val tapOpensPickupList: Boolean = false,
    val tapEventId: String? = null,
    val tapQuickMemoId: String? = null,
    val action: CapsuleActionSpec? = null,
    val actions: List<CapsuleActionSpec> = emptyList(),
    val tapOpensAccounting: Boolean = false,
    val tapImagePinId: Long? = null,
    val tapOpensQuickMemoDetail: Boolean = false,
) {
    val effectiveActions: List<CapsuleActionSpec>
        get() = actions.ifEmpty { action?.let(::listOf).orEmpty() }

    companion object {
        fun imagePin(id: Long, count: Int, endAction: CapsuleActionSpec): CapsuleDisplayModel {
            require(count > 0)
            val compactText = if (count == 1) "图片挂起" else "共 ${count} 张"
            return CapsuleDisplayModel(
                shortText = compactText,
                primaryText = "图片挂起",
                secondaryText = if (count == 1) "点击查看" else "$compactText，点击查看",
                expandedText = if (count == 1) "图片挂起，点击查看" else "图片挂起，$compactText，点击后左右滑动查看",
                tapImagePinId = id,
                action = endAction,
            )
        }
    }
}
