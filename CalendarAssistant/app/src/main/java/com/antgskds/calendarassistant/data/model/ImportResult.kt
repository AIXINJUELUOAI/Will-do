package com.antgskds.calendarassistant.data.model

/**
 * 导入结果
 *
 * @property successCount 成功导入的数量（新增）
 * @property skippedCount 跳过的数量（重复）
 * @property archiveStatusUpdateCount 归档状态更新的数量
 */
data class ImportResult(
    val successCount: Int = 0,          // 成功导入的数量
    val skippedCount: Int = 0,          // 跳过的数量（重复）
    val archiveStatusUpdateCount: Int = 0  // 归档状态更新的数量
)
