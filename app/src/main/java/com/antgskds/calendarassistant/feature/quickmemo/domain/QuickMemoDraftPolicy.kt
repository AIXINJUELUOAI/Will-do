package com.antgskds.calendarassistant.feature.quickmemo.domain

import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoTodoState

/** Non-positive IDs identify UI drafts, never Room rows or external entry targets. */
object QuickMemoDraftPolicy {
    fun isDraft(id: Long): Boolean = id <= 0L

    fun hasContent(memo: QuickMemoEntity, hasReminders: Boolean = false): Boolean =
        memo.title.isNotBlank() || memo.bodyText.isNotBlank() ||
            !memo.imagePath.isNullOrBlank() || !memo.audioPath.isNullOrBlank() ||
            !memo.sourceUrl.isNullOrBlank() || memo.todoState != QuickMemoTodoState.NONE ||
            memo.reminderAt != null || hasReminders
}
