package com.antgskds.calendarassistant.feature.quickmemo.domain

import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoTodoState
import org.junit.Assert.*
import org.junit.Test

class QuickMemoDraftPolicyTest {
    @Test fun untouchedOrWhitespaceOnlyDraftsStayOutsideTheList() {
        assertFalse(QuickMemoDraftPolicy.hasContent(QuickMemoEntity()))
        assertFalse(QuickMemoDraftPolicy.hasContent(QuickMemoEntity(title = "  ", bodyText = "\n\t")))
        assertTrue(QuickMemoDraftPolicy.isDraft(-1L))
        assertTrue(QuickMemoDraftPolicy.isDraft(-2L))
        assertFalse(QuickMemoDraftPolicy.isDraft(1L))
    }

    @Test fun titleAndAttachmentsDoNotRequireBodyText() {
        listOf(
            QuickMemoEntity(title = "测试标题"),
            QuickMemoEntity(imagePath = "image.jpg"),
            QuickMemoEntity(audioPath = "audio.m4a"),
            QuickMemoEntity(sourceUrl = "https://example.test/content"),
        ).forEach { assertTrue(QuickMemoDraftPolicy.hasContent(it)) }
    }

    @Test fun reminderOnlyAndTodoOnlyRecordsMustNeverBeDiscarded() {
        assertTrue(QuickMemoDraftPolicy.hasContent(QuickMemoEntity(), hasReminders = true))
        assertTrue(QuickMemoDraftPolicy.hasContent(QuickMemoEntity(reminderAt = 100L)))
        assertTrue(QuickMemoDraftPolicy.hasContent(QuickMemoEntity(todoState = QuickMemoTodoState.ACTIVE)))
        assertTrue(QuickMemoDraftPolicy.hasContent(QuickMemoEntity(todoState = QuickMemoTodoState.COMPLETED)))
    }
}
