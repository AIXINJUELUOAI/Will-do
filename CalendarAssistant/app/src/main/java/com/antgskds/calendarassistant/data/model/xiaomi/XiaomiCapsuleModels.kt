package com.antgskds.calendarassistant.data.model.xiaomi

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class XiaomiBaseInfo(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val type: Int = 2,
    val title: String,
    val content: String,
    val subTitle: String? = null,
    val subContent: String? = null,
    val picFunction: String? = null,
    val showDivider: Boolean? = null,
    val showContentDivider: Boolean? = null,
    val colorTitle: String? = null,
    val colorTitleDark: String? = null,
    val colorContent: String? = null,
    val colorContentDark: String? = null,
    val colorSubContent: String? = null,
    val colorSubContentDark: String? = null
)

@Serializable
data class XiaomiPicInfo(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val type: Int = 1,
    val pic: String,
    val picDark: String? = null,
    val actionInfo: XiaomiActionInfo? = null
)

@Serializable
data class XiaomiHintInfo(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val type: Int = 1,
    val title: String,
    val content: String,
    val picContent: String? = null,
    val colorTitle: String? = null,
    val colorTitleDark: String? = null,
    val colorContent: String? = null,
    val colorContentDark: String? = null,
    val colorContentBg: String? = null,
    val actionInfo: XiaomiActionInfo
)

@Serializable
data class XiaomiActionInfo(
    val type: Int? = null,
    val clickWithCollapse: Boolean? = null,
    val action: String? = null,
    val actionIcon: String? = null,
    val actionIconDark: String? = null,
    val actionTitle: String? = null,
    val actionTitleColor: String? = null,
    val actionTitleColorDark: String? = null,
    val actionBgColor: String? = null,
    val actionBgColorDark: String? = null,
    val actionIntentType: Int? = null,
    val actionIntent: String? = null
)

@Serializable
data class XiaomiFocusParamV2(
    val protocol: Int? = null,
    val aodTitle: String? = null,
    val aodPic: String? = null,
    val business: String? = null,
    val ticker: String? = null,
    val enableFloat: Boolean? = null,
    @SerialName("param_island")
    val paramIsland: XiaomiParamIsland? = null,
    val baseInfo: XiaomiBaseInfo? = null,
    val picInfo: XiaomiPicInfo? = null,
    val hintInfo: XiaomiHintInfo? = null
)

@Serializable
data class XiaomiFocusExtra(
    @SerialName("param_v2")
    val paramV2: XiaomiFocusParamV2
)

@Serializable
data class XiaomiParamIsland(
    val highlightColor: String? = null,
    val bigIslandArea: XiaomiBigIslandArea? = null,
    val smallIslandArea: XiaomiSmallIslandArea? = null
)

@Serializable
data class XiaomiBigIslandArea(
    val imageTextInfoLeft: XiaomiImageTextInfoLeft? = null,
    val textInfo: XiaomiIslandTextInfo? = null,
    val islandTimeout: Int? = null
)

@Serializable
data class XiaomiSmallIslandArea(
    val picInfo: XiaomiIslandPicInfo
)

@Serializable
data class XiaomiImageTextInfoLeft(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val type: Int = 1,
    val picInfo: XiaomiIslandPicInfo,
    val textInfo: XiaomiIslandTextInfo
)

@Serializable
data class XiaomiIslandTextInfo(
    val title: String,
    val showHighlightColor: Boolean? = null
)

@Serializable
data class XiaomiIslandPicInfo(
    @EncodeDefault(EncodeDefault.Mode.ALWAYS)
    val type: Int = 1,
    val pic: String
)
