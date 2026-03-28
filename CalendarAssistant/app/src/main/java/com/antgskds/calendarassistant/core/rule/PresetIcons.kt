package com.antgskds.calendarassistant.core.rule

/**
 * 预设通知图标列表。
 * 后续版本扩充只需在此添加条目，并将对应 drawable 放入 res/drawable/。
 */
object PresetIcons {

    data class PresetIcon(
        val resName: String,
        val label: String
    )

    val CAPSULE_ICON_PRESETS: List<PresetIcon> = listOf(
        // 日程类
        PresetIcon("ic_stat_event", "日程"),
        PresetIcon("ic_stat_train", "列车"),
        PresetIcon("ic_stat_car", "打车"),
        PresetIcon("ic_stat_flight", "航班"),
        // 取件类
        PresetIcon("ic_stat_package", "取件"),
        PresetIcon("ic_stat_food", "取餐"),
        PresetIcon("ic_stat_ticket", "取票"),
        PresetIcon("ic_stat_sender", "寄件"),
        // 其他
        PresetIcon("ic_stat_course", "课程"),
        PresetIcon("ic_stat_net", "网络"),
        PresetIcon("ic_stat_scan", "扫描"),
        PresetIcon("ic_stat_success", "成功")
    )
}
