package com.antgskds.calendarassistant.feature.quickmemo

import com.antgskds.calendarassistant.feature.backup.data.model.*
import com.antgskds.calendarassistant.feature.cloudsync.data.SyncV2Codec
import com.antgskds.calendarassistant.feature.cloudsync.domain.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.junit.Assert.*
import org.junit.Test

class QuickMemoPortableDataTest {
    @Test fun oldBackupDefaultsToUntitledUngroupedWithoutLosingBody() {
        val backup = Json.decodeFromString<AppBackupData>("""{"quickMemos":[{"bodyText":"旧正文"}]}""")
        val memo = backup.quickMemos.single()
        assertEquals("旧正文", memo.bodyText)
        assertEquals("", memo.title)
        assertNull(memo.sourceUrl)
        assertNull(memo.folderId)
        assertTrue(backup.quickMemoFolders.isEmpty())
    }

    @Test fun backupKeepsLinkFieldsAndEmptyFolders() {
        val original = AppBackupData(
            quickMemos = listOf(AppBackupQuickMemoDto(bodyText = "文案\nhttps://example.test/a",
                title = "链接收藏", sourceUrl = "https://example.test/a", linkKey = "https://example.test/a", folderId = "folder")),
            quickMemoFolders = listOf(AppBackupQuickMemoFolderDto("folder", "文章"), AppBackupQuickMemoFolderDto("empty", "空文件夹")),
        )
        assertEquals(original, Json.decodeFromString<AppBackupData>(Json.encodeToString(original)))
    }

    @Test fun syncCodecKeepsIndependentFolderPayloadAndOptionalFields() {
        val codec = SyncV2Codec()
        val folder = SyncV2QuickMemoFolderPayload("空文件夹", 10, 20)
        assertEquals(folder, codec.decodePayload(codec.encodePayload(folder), SyncV2QuickMemoFolderPayload::class.java))
        val legacy = codec.decodePayload("""{"type":"TEXT","bodyText":"旧正文","audioDurationMs":0,"transcriptionStatus":"NONE","analysisStatus":"NONE","createdAt":1,"updatedAt":1,"sortRank":0,"todoState":"NONE"}""",
            SyncV2QuickMemoPayload::class.java)
        assertEquals("旧正文", legacy.bodyText)
        assertTrue(legacy.title.isNullOrEmpty())
        assertNull(legacy.folderId)
        val memo = legacy.copy(title = "收藏", sourceUrl = "https://example.test/a", linkKey = "key", folderId = "folder")
        assertEquals(memo, codec.decodePayload(codec.encodePayload(memo), SyncV2QuickMemoPayload::class.java))
    }
}
