package com.antgskds.calendarassistant.data.state

import com.antgskds.calendarassistant.data.model.MyEvent

/**
 * 胶囊UI状态 - Sealed Interface
 * 定义胶囊显示的所有可能状态
 */
sealed interface CapsuleUiState {
    /**
     * 无胶囊显示
     * - 实况胶囊未开启
     * - 没有活跃的日程/取件码
     * - 所有日程都已过期
     */
    data object None : CapsuleUiState

    /**
     * 有胶囊显示
     * @param capsules 当前应该显示的胶囊列表
     */
    data class Active(
        val capsules: List<CapsuleItem>
    ) : CapsuleUiState {
        /**
         * 胶囊项 - 表示单个要显示的胶囊（日程或聚合取件码）
         */
        data class CapsuleItem(
            val id: String,           // 唯一标识（事件ID或聚合ID）
            val notifId: Int,         // 通知ID（用于NotificationManager）
            val type: Int,            // 类型：1=日程, 2=取件码
            val eventType: String,    // 事件类型：event=日程, temp=取件码, course=课程
            val title: String,        // 标题
            val content: String,      // 内容描述
            val description: String = "", // 事件描述，用于交通场景解析
            val color: Int,           // 颜色（Android Color Int）
            val startMillis: Long,    // 开始时间（毫秒）
            val endMillis: Long       // 结束时间（毫秒）
        )
    }
}
