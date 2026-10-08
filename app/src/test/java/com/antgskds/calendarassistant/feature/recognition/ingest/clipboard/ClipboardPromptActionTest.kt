package com.antgskds.calendarassistant.feature.recognition.ingest.clipboard

import com.antgskds.calendarassistant.feature.notification.policy.ClipboardCodePromptDeliveryPolicy
import com.antgskds.calendarassistant.feature.quickmemo.domain.QuickMemoLinkParser
import com.antgskds.calendarassistant.feature.recognition.ingest.instantcode.InstantCodeCandidate
import com.antgskds.calendarassistant.feature.recognition.ingest.instantcode.InstantCodeType
import org.junit.Assert.*
import org.junit.Test

class ClipboardPromptActionTest {
    @Test fun buttonHasOnlyStableKeyWhileSnapshotRetainsCapturedCandidate() {
        val key = ClipboardCodePromptDeliveryPolicy.key(1, "unguessable-session")
        val captured = InstantCodeCandidate(InstantCodeType.PICKUP, "1234-56", "驿站", "地址")
        val metadata = ClipboardPromptAction.encode(captured)
        val action = ClipboardPromptAction.create(key, "添加取件")
        assertEquals(mapOf(ClipboardPromptAction.EXTRA_KEY to key.value), action.payload)
        assertEquals(captured, ClipboardPromptAction.code(metadata))
        assertNull(ClipboardPromptAction.code(emptyMap()))
        assertNotEquals(key, ClipboardCodePromptDeliveryPolicy.key(1, "different-session"))
    }

    @Test fun linkSnapshotRetainsOriginalUrlCaptionAndOfflineDedupKey() {
        val captured = requireNotNull(QuickMemoLinkParser.parse("正文 https://xhslink.cn/o/Test"))
        val restored = ClipboardPromptAction.link(ClipboardPromptAction.encode(captured))
        assertEquals(captured, restored)
        assertNull(ClipboardPromptAction.link(mapOf("url" to "intent://unsafe")))
    }
}
