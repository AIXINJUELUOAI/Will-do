package com.antgskds.calendarassistant.data.state

/**
 * 胶囊类型常量（厂商无关、纯数据）。
 *
 * 从 [com.antgskds.calendarassistant.core.capsule.CapsuleStateManager] 抽出，
 * 让 vendor / display 层（Flyme/Xiaomi 模板、IconUtils、MiuiIslandManager）不再为了拿
 * 这几个 type 常量而反向 import 业务层 CapsuleStateManager——这是「一条线 + 普通/胶囊两分支」
 * 解耦的第一步，便于后续接入新胶囊厂商。
 *
 * 值与原 CapsuleStateManager.TYPE_* 完全一致，行为不变。
 */
object CapsuleType {
    const val SCHEDULE = 1
    const val PICKUP = 2
    const val PICKUP_EXPIRED = 3
    const val NETWORK_SPEED = 4
    const val OCR_PROGRESS = 5
    const val OCR_RESULT = 6
    const val MODEL_LOADING = 7
    const val WEATHER_ALERT = 8
    const val VOICE_TRANSCRIPTION = 9
    const val TEXT_QUICK_MEMO = 10
}
