package com.antgskds.calendarassistant.data.model

object HomeEntryKey {
    const val SIDEBAR = "menu"
    const val TODAY = "today"
    const val ALL = "all"
    const val NOTE = "note"
}

fun sanitizeHomeBottomItems(raw: List<String>): List<String> {
    val allowed = setOf(HomeEntryKey.TODAY, HomeEntryKey.ALL, HomeEntryKey.NOTE)

    val cleaned = raw
        .map { it.trim().lowercase() }
        .filter { it in allowed }
        .distinct()
        .take(3)

    if (cleaned.isNotEmpty()) return cleaned

    return listOf(HomeEntryKey.TODAY, HomeEntryKey.ALL, HomeEntryKey.NOTE)
}

fun sanitizeHomeStartPageKey(raw: String, bottomItems: List<String>): String {
    val normalized = raw.trim().lowercase()
    return if (normalized in bottomItems) normalized else bottomItems.firstOrNull() ?: HomeEntryKey.TODAY
}

fun homeEntryLabel(key: String): String {
    return when (key) {
        HomeEntryKey.SIDEBAR -> "侧边栏"
        HomeEntryKey.TODAY -> "今日日程"
        HomeEntryKey.ALL -> "全部日程"
        HomeEntryKey.NOTE -> "便签"
        else -> key
    }
}
