package com.antgskds.calendarassistant.shared.management.resource.notification.display.normal

object ScheduleNormalDisplay {
    val reminderOptions: List<Pair<Int, String>> = listOf(
        0 to "日程开始时",
        5 to "5分钟前",
        10 to "10分钟前",
        15 to "15分钟前",
        30 to "30分钟前",
        60 to "1小时前",
        120 to "2小时前",
        360 to "6小时前",
        1440 to "1天前",
        2880 to "2天前"
    )

    fun reminderTitleFallback(): String = "日程提醒"

    fun startLabel(): String = "日程开始"

    fun advanceLabel(minutes: Int): String = "提前${minutes}分钟"

    fun detailFallback(): String = "点击查看详情"

    fun reminderContent(presentationContent: String?, label: String): String {
        return presentationContent ?: label.ifEmpty { detailFallback() }
    }

    fun pickupFallback(): String = "取件码"

    fun pickupDoneAction(): String = "已取"

    fun pickupInitialContent(title: String, subtitle: String?, detail: String?, description: String): NormalNotificationContent {
        val contentText = subtitle ?: pickupFallback()
        return NormalNotificationContent(
            title = title,
            contentText = contentText,
            bigText = subtitle ?: detail ?: description
        )
    }

    fun dailySummaryShortTitle(isMorning: Boolean): String {
        return if (isMorning) "今日提醒" else "明日预告"
    }

    fun dailySummaryTitle(shortTitle: String, weatherText: String): String {
        return if (weatherText.isNotBlank()) "$shortTitle|$weatherText" else shortTitle
    }

    fun unnamedEventTitle(): String = "未命名日程"

    fun dailySummaryContent(count: Int, titles: List<String>): String {
        return "您有 $count 个日程：${titles.joinToString("，")}"
    }

    fun dailySummaryMoreLine(count: Int): String = "以及其他 $count 个日程"
}
