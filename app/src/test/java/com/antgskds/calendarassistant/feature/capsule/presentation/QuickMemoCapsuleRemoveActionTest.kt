package com.antgskds.calendarassistant.feature.capsule.presentation

import com.antgskds.calendarassistant.feature.capsule.domain.CapsuleActionSpec
import com.antgskds.calendarassistant.platform.receiver.EventActionReceiver
import org.junit.Assert.*
import org.junit.Test

class QuickMemoCapsuleRemoveActionTest {
    @Test fun sharedDisplayKeepsIndependentRemoveTargets() {
        val pinned = CapsuleMessageComposer.composeTextQuickMemo("喝水", 42)
        val reminder = CapsuleMessageComposer.composeTextQuickMemo(
            "喝水", 42,
            removeAction = CapsuleActionSpec("移除", EventActionReceiver.ACTION_CLEAR_QUICK_MEMO_REMINDER, EventActionReceiver.EXTRA_QUICK_MEMO_REMINDER_ID, 7),
        )
        assertEquals(pinned.primaryText, reminder.primaryText)
        assertEquals(pinned.tapQuickMemoId, reminder.tapQuickMemoId)
        assertEquals(EventActionReceiver.ACTION_CLEAR_TEXT_QUICK_MEMO, pinned.action?.receiverAction)
        assertEquals(EventActionReceiver.ACTION_CLEAR_QUICK_MEMO_REMINDER, reminder.action?.receiverAction)
        assertEquals(42L, pinned.action?.extraLongValue)
        assertEquals(7L, reminder.action?.extraLongValue)
    }
}
