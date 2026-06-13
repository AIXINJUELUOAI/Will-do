package com.antgskds.calendarassistant.core.weather

import com.antgskds.calendarassistant.data.model.WeatherAlertData

object WeatherWarningText {
    const val MESSAGE_TYPE_ALERT = "alert"
    const val MESSAGE_TYPE_UPDATE = "update"
    const val MESSAGE_TYPE_CANCEL = "cancel"

    fun officialTitle(alert: WeatherAlertData): String {
        val color = colorName(alert.colorCode)
        val event = alert.eventName.ifBlank { extractEventName(alert.headline) }.ifBlank { "天气" }
        val suffix = when {
            isCancel(alert) -> "预警解除"
            isUpdate(alert) -> "预警更新"
            else -> "预警"
        }
        return listOf(color, event, suffix)
            .filter { it.isNotBlank() }
            .joinToString("")
            .replace("预警预警", "预警")
    }

    fun officialShortText(alert: WeatherAlertData): String {
        val event = alert.eventName.ifBlank { extractEventName(alert.headline) }.ifBlank { "天气" }
        return when {
            isCancel(alert) -> "${event}解除"
            isUpdate(alert) -> "${event}更新"
            else -> officialTitle(alert).removeSuffix("预警").ifBlank { event }
        }
    }

    fun isCancel(alert: WeatherAlertData): Boolean {
        val code = alert.messageTypeCode.lowercase()
        val text = listOf(alert.headline, alert.description, alert.instruction).joinToString("。")
        return code == MESSAGE_TYPE_CANCEL || text.contains("解除") || text.contains("取消")
    }

    fun isUpdate(alert: WeatherAlertData): Boolean {
        val code = alert.messageTypeCode.lowercase()
        val text = listOf(alert.headline, alert.description).joinToString("。")
        return code == MESSAGE_TYPE_UPDATE || text.contains("更新") || text.contains("继续发布")
    }

    fun colorName(value: String): String {
        return when (value.lowercase()) {
            "white" -> "白色"
            "gray", "grey" -> "灰色"
            "blue" -> "蓝色"
            "green" -> "绿色"
            "yellow" -> "黄色"
            "amber" -> "琥珀色"
            "orange" -> "橙色"
            "red" -> "红色"
            "purple" -> "紫色"
            "black" -> "黑色"
            "unknown", "minor", "moderate", "severe", "extreme" -> ""
            else -> value
        }
    }

    private fun extractEventName(value: String): String {
        val normalized = value.replace("预警信号", "预警").replace("发布", "")
        val start = listOf(
            "雷暴大风", "雷雨大风", "短时强降雨", "强降雨", "暴雨", "大风", "台风", "暴雪",
            "强降温", "低温雨雪冰冻", "低温冻害", "低温", "寒潮", "寒冷", "霜冻", "冰冻", "冻雨",
            "道路结冰", "高温", "热浪", "干热风", "冰雹", "雷电", "强对流", "大雾", "霾", "沙尘",
            "森林火险", "草原火险", "地质灾害", "山洪", "内涝", "洪水", "重污染", "农业气象风险"
        )
            .firstOrNull { normalized.contains(it) }
        return start.orEmpty()
    }
}
