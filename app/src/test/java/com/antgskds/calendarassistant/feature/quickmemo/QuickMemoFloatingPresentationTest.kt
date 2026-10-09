package com.antgskds.calendarassistant.feature.quickmemo

import com.antgskds.calendarassistant.feature.quickmemo.data.local.*
import com.antgskds.calendarassistant.feature.quickmemo.domain.QuickMemoFloatingPresentationPolicy as Policy
import org.junit.Assert.*
import org.junit.Test

class QuickMemoFloatingPresentationTest {
    @Test fun collapsedTitleFallsBackToOriginalBody() {
        val memo = QuickMemoEntity(title = "  我的标题  ", bodyText = "原正文")
        assertEquals("我的标题", Policy.present(memo).compactText)
        assertEquals("原正文", Policy.present(memo.copy(title = "  ")).compactText)
    }

    @Test fun expandedContentUsesNonblankSummaryWithoutReplacingBody() {
        val memo = QuickMemoEntity(title = "标题", bodyText = "原正文")
        val presented = Policy.present(memo, "  **摘要**  ")
        assertTrue(presented.hasSummary)
        assertEquals("**摘要**", presented.expandedText)
        assertEquals("原正文", memo.bodyText)
        assertEquals("原正文", Policy.present(memo, "  ").expandedText)
        assertFalse(Policy.present(memo, "  ").hasSummary)
    }

    @Test fun originalLinkTakesPriorityEvenAfterAddingVoiceAttachment() {
        val url = "https://example.com/video?id=123&token=a%2Fb%3D#part"
        val memo = QuickMemoEntity(type = QuickMemoType.VOICE, sourceUrl = url,
            bodyText = "文案", audioPath = "saved-recording.m4a", audioDurationMs = 1234)
        val presented = Policy.present(memo)
        assertEquals(url, presented.sourceUrl)
        assertFalse(presented.showAudio)
        assertEquals("saved-recording.m4a", memo.audioPath)
        assertEquals(1234L, memo.audioDurationMs)
    }

    @Test fun ordinaryVoiceMemoRetainsPlayback() {
        val memo = QuickMemoEntity(type = QuickMemoType.VOICE, audioPath = "recording.m4a")
        assertTrue(Policy.present(memo).showAudio)
        assertNull(Policy.present(memo).sourceUrl)
        assertEquals("仅音频", Policy.present(memo).compactText)
    }

    @Test fun emptyContentPreservesVoiceStatusAndImageFallback() {
        assertEquals("空白随口记", Policy.present(QuickMemoEntity()).compactText)
        assertEquals("图片随口记", Policy.present(QuickMemoEntity(type = QuickMemoType.IMAGE)).expandedText)
        val voice = QuickMemoEntity(type = QuickMemoType.VOICE)
        assertEquals("转写中", Policy.present(voice.copy(transcriptionStatus = QuickMemoTranscriptionStatus.PENDING)).compactText)
        assertEquals("转写中", Policy.present(voice.copy(transcriptionStatus = QuickMemoTranscriptionStatus.PROCESSING)).compactText)
        assertEquals("转写失败，可重试", Policy.present(voice.copy(transcriptionStatus = QuickMemoTranscriptionStatus.FAILED)).compactText)
        val url = "https://example.com/empty"
        assertEquals(url, Policy.present(QuickMemoEntity(type = QuickMemoType.LINK, sourceUrl = url)).expandedText)
    }

    @Test fun invalidOrExecutableUrlsHaveNoOpenAction() {
        listOf(null, "", "javascript:alert(1)", "file:///sdcard/a", "intent://example.com",
            "https://", "https://user:password@example.com/post", "https://example.com/bad space")
            .forEach { assertNull(it, Policy.sourceUrl(it)) }
        val url = "HTTPS://example.com/post?s=token%2Bfoo&share=abc"
        assertEquals(url, Policy.sourceUrl("  $url  "))
    }
}
