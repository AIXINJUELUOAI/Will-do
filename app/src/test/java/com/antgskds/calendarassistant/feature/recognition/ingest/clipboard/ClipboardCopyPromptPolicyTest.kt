package com.antgskds.calendarassistant.feature.recognition.ingest.clipboard

import org.junit.Assert.*
import org.junit.Test

class ClipboardCopyPromptPolicyTest {
    @Test fun duplicateListenerAndForegroundReadOfSameCopyOnlyPromptOnce() {
        val policy = ClipboardCopyPromptPolicy()
        val listener = policy.instanceKey("content-hash", 1000)
        assertFalse(policy.shouldSkip(listener))
        policy.markPrompted(listener)
        assertTrue(policy.shouldSkip(policy.instanceKey("content-hash", 1000)))
        assertTrue(policy.shouldSkip(policy.instanceKey("content-hash", 1000)))
    }

    @Test fun recopyOfLinkPickupOrMealCodeAllowsPromptImmediately() {
        listOf("link-hash", "pickup-hash", "meal-hash").forEach { hash ->
            val policy = ClipboardCopyPromptPolicy()
            val old = policy.instanceKey(hash, 1000)
            policy.markPrompted(old)
            // 以前的五秒内容实例复用也不能阻止本次重新复制。
            val new = policy.instanceKey(hash, 1001)
            assertNotEquals(old, new)
            assertFalse(policy.shouldSkip(new))
            policy.markPrompted(new)
            assertTrue(policy.shouldSkip(new))
        }
    }

    @Test fun failedDeliveryDoesNotConsumeInstanceAndLaterRecopyCanRetry() {
        val policy = ClipboardCopyPromptPolicy()
        val key = policy.instanceKey("hash", 1000)
        assertFalse(policy.shouldSkip(key))
        // 失败时不调用 markPrompted，前台检查仍可补提示。
        assertFalse(policy.shouldSkip(key))
        policy.markPrompted(key)
        // 关闭、过期、确认失败均不会禁止一个新的复制实例。
        assertFalse(policy.shouldSkip(policy.instanceKey("hash", 2000)))
    }

    @Test fun contentHashAlsoDistinguishesDifferentContentsWithSameTimestamp() {
        val policy = ClipboardCopyPromptPolicy()
        policy.markPrompted(policy.instanceKey("first", 1000))
        assertFalse(policy.shouldSkip(policy.instanceKey("second", 1000)))
    }

    @Test fun missingTimestampDoesNotInventNewCopiesOnEachRead() {
        val policy = ClipboardCopyPromptPolicy()
        policy.markPrompted(policy.instanceKey("hash", 0))
        assertTrue(policy.shouldSkip(policy.instanceKey("hash", 0)))
        assertTrue(policy.shouldSkip(policy.instanceKey("hash", -1)))
    }
}
