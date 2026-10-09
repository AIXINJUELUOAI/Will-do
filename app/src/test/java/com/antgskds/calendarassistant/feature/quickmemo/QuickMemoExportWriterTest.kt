package com.antgskds.calendarassistant.feature.quickmemo

import com.antgskds.calendarassistant.feature.quickmemo.application.*
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.linkanalysis.domain.LinkSummaryData
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import org.junit.Assert.*
import org.junit.Test

class QuickMemoExportWriterTest {
    @Test fun exportsUtf8MarkdownWithTranscriptAndDeduplicatedWarnings() {
        val root = Files.createTempDirectory("memo-export").toFile()
        try {
            val data = QuickMemoExportSnapshot(QuickMemoEntity(title = "测试笔记", bodyText = "原文", createdAt = 0),
                LinkSummaryData(summary = "摘要正文。\n\n**已知限制：** 不完整。", warnings = listOf("不完整。"), transcript = "一句。二句。"))
            val result = QuickMemoExportWriter.write(data, root)
            assertEquals("测试笔记.md", result.file.name)
            assertEquals("text/markdown", result.mimeType)
            val text = result.file.readText()
            assertTrue(text.startsWith("# 测试笔记\n"))
            assertTrue(text.contains("一句。\n\n二句。"))
            assertEquals(1, Regex("不完整").findAll(text).count())
            assertFalse(text.contains("已知限制："))
        } finally { root.deleteRecursively() }
    }

    @Test fun zipContainsWorkingRelativeReferencesAndExactAttachmentBytes() {
        val root = Files.createTempDirectory("memo-export-zip").toFile()
        try {
            val image = File(root, "original.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            val audio = File(root, "original.m4a").apply { writeBytes(byteArrayOf(4, 5, 6)) }
            val data = QuickMemoExportSnapshot(QuickMemoEntity(title = "../危险:标题", bodyText = "内容", imagePath = image.path, audioPath = audio.path), null)
            val result = QuickMemoExportWriter.write(data, File(root, "output"))
            assertEquals("application/zip", result.mimeType)
            assertFalse(result.file.name.contains('/'))
            ZipFile(result.file).use { zip ->
                val entries = zip.entries().asSequence().toList()
                assertEquals(3, entries.size)
                assertTrue(entries.none { it.name.startsWith("/") || it.name.contains("../") })
                assertArrayEquals(image.readBytes(), zip.getInputStream(zip.getEntry("attachments/image.jpg")).readBytes())
                assertArrayEquals(audio.readBytes(), zip.getInputStream(zip.getEntry("attachments/audio.m4a")).readBytes())
                val markdown = zip.getInputStream(entries.single { it.name.endsWith(".md") }).reader(Charsets.UTF_8).readText()
                assertTrue(markdown.contains("![图片](attachments/image.jpg)"))
                assertTrue(markdown.contains("[录音](attachments/audio.m4a)"))
            }
        } finally { root.deleteRecursively() }
    }

    @Test fun rejectsMissingAttachmentsAndDoesNotProduceIncompleteDocument() {
        val root = Files.createTempDirectory("memo-export-missing").toFile()
        try {
            val snapshot = QuickMemoExportSnapshot(QuickMemoEntity(imagePath = File(root, "missing.jpg").path), null)
            assertThrows(IllegalArgumentException::class.java) { QuickMemoExportWriter.write(snapshot, root) }
            assertTrue(root.listFiles().orEmpty().isEmpty())
            assertEquals("随口记-CON", QuickMemoExportWriter.fileName(QuickMemoEntity(title = "CON")))
        } finally { root.deleteRecursively() }
    }
}
