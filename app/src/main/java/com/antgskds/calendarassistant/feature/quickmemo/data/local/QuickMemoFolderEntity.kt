package com.antgskds.calendarassistant.feature.quickmemo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 单层 UUID 归属；删除时由 DAO 事务移回未分组，保留全部记录。 */
@Entity(tableName = "quick_memo_folders")
data class QuickMemoFolderEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
