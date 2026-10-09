package com.antgskds.calendarassistant

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.antgskds.calendarassistant.feature.quickmemo.data.QuickMemoRepository
import com.antgskds.calendarassistant.feature.quickmemo.data.local.*
import com.antgskds.calendarassistant.feature.schedule.data.db.EventsDatabase
import com.antgskds.calendarassistant.platform.floating.ui.contract.FloatingInputMode
import com.antgskds.calendarassistant.platform.floating.ui.render.material.TimeWheelList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** 需真机执行；主机编译仅检查回归用例可构建。 */
class QuickMemoFloatingRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun linkWithAudioHasOnlyOneOpenActionInBothCardStates() {
        val url = "https://example.com/video?s=abc%2F123"
        val memo = QuickMemoEntity(id = 42, type = QuickMemoType.VOICE, title = "收藏标题",
            bodyText = "原文案", sourceUrl = url, audioPath = "recording.m4a")
        var opened: String? = null
        compose.setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(380.dp, 600.dp)) {
                    TimeWheelList(scheduleItems = emptyList(), quickMemos = listOf(memo),
                        quickMemoSummaries = mapOf(42L to "**摘要内容**"), currentMode = FloatingInputMode.NOTE,
                        windowWidthPx = 1000f, hapticEnabled = false, onOpenQuickMemoLink = { opened = it })
                }
            }
        }
        compose.onNodeWithText("收藏标题").assertIsDisplayed()
        compose.onNodeWithText("原文案").assertDoesNotExist()
        compose.onNodeWithContentDescription("播放语音").assertDoesNotExist()
        compose.onNodeWithText("收藏标题").performClick()
        compose.onNodeWithText("原文案").assertDoesNotExist()
        compose.onNodeWithContentDescription("播放语音").assertDoesNotExist()
        compose.onNodeWithContentDescription("打开原链接").performClick()
        compose.runOnIdle { assertEquals(url, opened) }
    }

    @Test fun editingUsesOriginalBodyAndPreservesDraftAcrossBackgroundUpdatesAndFailure() {
        var memo by mutableStateOf(QuickMemoEntity(id = 42, title = "原始标题", bodyText = "原始正文"))
        var callback: ((Boolean) -> Unit)? = null
        var saved: Pair<String, String>? = null
        compose.setContent {
            MaterialTheme {
                Box(Modifier.requiredSize(380.dp, 600.dp)) {
                    TimeWheelList(scheduleItems = emptyList(), quickMemos = listOf(memo),
                        quickMemoSummaries = mapOf(42L to "摘要内容"), currentMode = FloatingInputMode.NOTE,
                        windowWidthPx = 1000f, hapticEnabled = false,
                        onSaveQuickMemo = { _, title, body, complete -> saved = title to body; callback = complete })
                }
            }
        }
        compose.onNodeWithText("原始标题").performClick()
        compose.onNodeWithText("原始标题").performTouchInput {
            swipe(Offset(width * 0.8f, centerY), Offset(width * 0.4f, centerY), 500)
        }
        compose.onNodeWithContentDescription("编辑").performClick()
        compose.onAllNodes(hasSetTextAction())[0].assertTextEquals("原始标题").performTextReplacement("用户标题")
        compose.onAllNodes(hasSetTextAction())[1].assertTextEquals("原始正文").performTextReplacement("用户正文")
        compose.runOnIdle { memo = memo.copy(title = "后台标题", bodyText = "后台正文", updatedAt = memo.updatedAt + 1) }
        compose.onAllNodes(hasSetTextAction())[0].assertTextEquals("用户标题")
        compose.onAllNodes(hasSetTextAction())[1].assertTextEquals("用户正文")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle {
            assertEquals("用户标题" to "用户正文", saved)
            requireNotNull(callback)(false)
        }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(2)
        compose.onAllNodes(hasSetTextAction())[1].assertTextEquals("用户正文")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { requireNotNull(callback)(true) }
        compose.onAllNodes(hasSetTextAction()).assertCountEquals(0)
    }

    @Test fun contentUpdatePreservesStoredLinkAttachmentsAndFolder() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, EventsDatabase::class.java).build()
        try {
            val dao = db.quickMemoDao()
            val original = QuickMemoEntity(type = QuickMemoType.VOICE, title = "旧标题", bodyText = "旧正文",
                sourceUrl = "https://example.com/post?s=token", linkKey = "example-post",
                audioPath = "recording.m4a", imagePath = "photo.jpg", audioDurationMs = 4321,
                folderId = "folder", sortRank = 10, todoState = QuickMemoTodoState.ACTIVE)
            val id = dao.insertQuickMemo(original)
            val before = requireNotNull(dao.getQuickMemo(id))
            QuickMemoRepository(dao).updateContent(id, "新标题", "新正文")
            val after = requireNotNull(dao.getQuickMemo(id))
            assertEquals(before.copy(title = "新标题", bodyText = "新正文", updatedAt = after.updatedAt), after)
        } finally { db.close() }
    }
}
