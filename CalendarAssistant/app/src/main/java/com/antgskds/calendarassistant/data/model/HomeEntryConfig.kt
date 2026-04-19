package com.antgskds.calendarassistant.data.model

object HomeEntryKey {
    const val SIDEBAR = "menu"
    const val TODAY = "today"
    const val NOTE = "note"
    const val ALL = "all"
}

fun sanitizeHomeBottomItems(raw: List<String>, noteEnabled: Boolean): List<String> {
    val allowed = if (noteEnabled) {
        setOf(HomeEntryKey.TODAY, HomeEntryKey.NOTE, HomeEntryKey.ALL)
    } else {
        setOf(HomeEntryKey.TODAY, HomeEntryKey.ALL)
    }

    val cleaned = raw
        .map { it.trim().lowercase() }
        .filter { it in allowed }
        .distinct()
        .take(3)

    if (cleaned.isNotEmpty()) return cleaned

    return if (noteEnabled) {
        listOf(HomeEntryKey.TODAY, HomeEntryKey.NOTE, HomeEntryKey.ALL)
    } else {
        listOf(HomeEntryKey.TODAY, HomeEntryKey.ALL)
    }
}

fun sanitizeHomeStartPageKey(raw: String, bottomItems: List<String>): String {
    val normalized = raw.trim().lowercase()
    return if (normalized in bottomItems) normalized else bottomItems.firstOrNull() ?: HomeEntryKey.TODAY
}

fun homeEntryLabel(key: String): String {
    return when (key) {
        HomeEntryKey.SIDEBAR -> "侧边栏"
        HomeEntryKey.TODAY -> "今日日程"
        HomeEntryKey.NOTE -> "便签"
        HomeEntryKey.ALL -> "全部日程"
        else -> key
    }
}
