package com.antgskds.calendarassistant.feature.recognition.application.ai

import com.antgskds.calendarassistant.feature.recognition.application.ai.model.RemotePrompts
import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.settings.data.model.migrateToMultimodalConfig
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultimodalMigrationTest {
    @Test fun version12ProtocolAddsReceiptSummaryRulesOnceAndPreservesCustomText() {
        val old = "自定义开头\n【统一图文输出协议 v12】\n旧账单规则\n此协议优先于旧提示词中“仅输出 events”或“仅识别日程”的限制，其他自定义日程规则继续生效。\n自定义结尾"
        val updated = AiPrompts.normalize(RemotePrompts(version = 11, mmUnifiedPrompt = old))
        assertTrue(updated.mmUnifiedPrompt.startsWith("自定义开头"))
        assertTrue(updated.mmUnifiedPrompt.contains("自定义结尾"))
        assertFalse(updated.mmUnifiedPrompt.contains("统一图文输出协议 v12"))
        assertFalse(updated.mmUnifiedPrompt.contains("旧账单规则"))
        assertTrue(updated.mmUnifiedPrompt.contains("统一图文输出协议 v13"))
        assertTrue(updated.mmUnifiedPrompt.contains("收款汇总首页例外"))
        assertEquals(updated.mmUnifiedPrompt, updated.userTextPrompt)
        assertEquals(updated, AiPrompts.normalize(updated))
    }
    @Test fun version11ProtocolUpgradesTransferRulesOnceAndPreservesCustomText() {
        val old = "自定义开头\n【统一图文输出协议 v11】\n旧账单规则\n此协议优先于旧提示词中“仅输出 events”或“仅识别日程”的限制，其他自定义日程规则继续生效。\n自定义结尾"
        val updated = AiPrompts.normalize(RemotePrompts(version = 11, mmUnifiedPrompt = old))
        assertTrue(updated.mmUnifiedPrompt.contains("自定义开头"))
        assertTrue(updated.mmUnifiedPrompt.contains("自定义结尾"))
        assertFalse(updated.mmUnifiedPrompt.contains("统一图文输出协议 v11"))
        assertTrue(updated.mmUnifiedPrompt.contains("收款方尚未领取的转账不能生成收入"))
        assertTrue(updated.mmUnifiedPrompt.contains("统一图文输出协议 v13"))
        assertEquals(updated.mmUnifiedPrompt, updated.userTextPrompt)
        assertEquals(updated, AiPrompts.normalize(updated))
    }
    @Test fun version10ProtocolAddsNumberTypesAndPreservesSurroundingCustomRules() {
        val old = "自定义开头\n【统一图文输出协议 v10】\n旧账单规则\n此协议优先于旧提示词中“仅输出 events”或“仅识别日程”的限制，其他自定义日程规则继续生效。\n自定义结尾"
        val updated = AiPrompts.normalize(RemotePrompts(version = 10, mmUnifiedPrompt = old))
        assertTrue(updated.mmUnifiedPrompt.contains("自定义开头"))
        assertTrue(updated.mmUnifiedPrompt.contains("自定义结尾"))
        assertFalse(updated.mmUnifiedPrompt.contains("统一图文输出协议 v10"))
        assertTrue(updated.mmUnifiedPrompt.contains("transactionIdType"))
        assertEquals(updated, AiPrompts.normalize(updated))
    }
    @Test fun oldAccountingProtocolIsUpgradedWithoutLosingCustomRules() {
        val old = "我的日程规则\n【统一图文输出协议 v9】\n旧时间规则\n此协议优先于旧提示词中“仅输出 events”或“仅识别日程”的限制，其他自定义日程规则继续生效。"
        val updated = AiPrompts.normalize(RemotePrompts(version = 9, mmUnifiedPrompt = old))
        assertTrue(updated.mmUnifiedPrompt.startsWith("我的日程规则"))
        assertFalse(updated.mmUnifiedPrompt.contains("统一图文输出协议 v9"))
        assertTrue(updated.mmUnifiedPrompt.contains("午饭花了35块"))
        assertEquals(updated, AiPrompts.normalize(updated))
    }
    @Test
    fun legacyLayoutBlockIsRemovedWithoutLosingCustomRules() {
        val layout = """
            【布局标记】（已通过算法预处理）
            - ` | `: 同行分列
            - `[L]`: 左侧气泡
            - `[R]`: 右侧气泡
            - `[C]`: 居中
            保留原始换行。
        """.trimIndent()
        val custom = "自定义规则：保留 [L] 原文。\nTitle：自定义标题\ndescription：【取件】取件码|品牌|位置"
        for (separator in listOf("\n", "\r\n", "\\n")) {
            val header = (layout + "\n" + custom).replace("\n", separator)
            val normalized = AiPrompts.normalize(RemotePrompts(version = 7, promptHeader = header, mmUnifiedPrompt = "custom"))
            assertEquals(custom, normalized.promptHeader.replace("\r\n", "\n"))
            assertTrue(normalized.mmUnifiedPrompt.startsWith("custom"))
            assertTrue(normalized.mmUnifiedPrompt.contains("【统一图文输出协议 v13】"))
            assertEquals(normalized, AiPrompts.normalize(normalized))
        }
    }

    @Test
    fun customHeaderWithoutLegacyBlockIsPreserved() {
        val header = "自定义规则\n【布局标记】自定义含义\n保留原始换行。\ndescription：品牌|位置"
        val normalized = AiPrompts.normalize(RemotePrompts(version = 8, promptHeader = header, mmUnifiedPrompt = "custom"))
        assertEquals(header, normalized.promptHeader)
        val defaults = AiPrompts.normalize(RemotePrompts(version = 8, mmUnifiedPrompt = "custom"))
        assertFalse(defaults.promptHeader.contains("【布局标记】"))
        assertTrue(defaults.promptHeader.contains("Title："))
    }

    @Test
    fun legacyBackupKeepsCredentialsAndUnknownModelNameWithoutInventingVisionSupport() {
        val old = Json.decodeFromString<MySettings>(
            """{"useMultimodalAi":false,"modelKey":"old-key","modelName":"custom-model","modelUrl":"https://old.example/v1"}"""
        )
        val migrated = old.migrateToMultimodalConfig()
        assertTrue(migrated.useMultimodalAi)
        assertEquals("old-key", migrated.mmModelKey)
        assertEquals("custom-model", migrated.mmModelName)
        assertEquals("https://old.example/v1", migrated.mmModelUrl)
        assertEquals(old.modelKey, migrated.modelKey)
        assertEquals(migrated, migrated.migrateToMultimodalConfig())
    }

    @Test
    fun restoredDisabledToggleCannotSelectOldTextConnection() {
        val old = MySettings(
            useMultimodalAi = false,
            modelKey = "old-key", modelName = "text-model", modelUrl = "https://old.example/v1",
            mmModelKey = "vision-key", mmModelName = "vision-model", mmModelUrl = "https://vision.example/v1"
        )
        val active = old.activeAiConfig()
        assertTrue(active.isMultimodal)
        assertEquals("vision-key", active.key)
        assertEquals("vision-model", active.name)
        assertEquals("https://vision.example/v1", active.url)
    }

    @Test
    fun incompleteVisionConnectionNeverBorrowsCredentialsFromAnotherProvider() {
        val old = MySettings(
            modelKey = "secret-for-old-host", modelName = "old-model", modelUrl = "https://old.example/v1",
            mmModelUrl = "https://new.example/v1"
        )
        val active = old.activeAiConfig()
        assertEquals("https://new.example/v1", active.url)
        assertEquals("", active.key)
        assertEquals("", active.name)
        assertFalse(active.isConfigured())
    }

    @Test
    fun unifiedPromptFileNoLongerRequiresRetiredOcrPrompts() {
        val unified = Json.decodeFromString<RemotePrompts>(
            """{"version":8,"mm_unified_prompt":"custom visual prompt"}"""
        )
        assertTrue(unified.isValid())
        assertTrue(RemotePrompts(version = 7, schedulePrompt = "legacy", pickupPrompt = "legacy").isValid())
        assertTrue(RemotePrompts(version = 7, userTextPrompt = "legacy unified").isValid())
        assertFalse(RemotePrompts(version = 8).isValid())
    }
}
