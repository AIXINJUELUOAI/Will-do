package com.antgskds.calendarassistant.data.model

enum class UiStyle(val label: String, val description: String) {
    MATERIAL3(
        label = "Material Design 3",
        description = "当前默认界面风格"
    ),
    MIUI(
        label = "MIUI / HyperOS",
        description = "小米系统风格界面"
    );

    companion object {
        fun fromName(name: String): UiStyle {
            return entries.firstOrNull { it.name == name } ?: MATERIAL3
        }
    }
}
