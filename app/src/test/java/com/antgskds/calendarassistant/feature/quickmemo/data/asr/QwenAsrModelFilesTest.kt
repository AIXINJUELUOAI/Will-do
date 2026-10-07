package com.antgskds.calendarassistant.feature.quickmemo.data.asr

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class QwenAsrModelFilesTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun bundle(vararg entries: Pair<String, ByteArray>): ByteArray {
        val buffer = ByteArrayOutputStream()
        ZipOutputStream(buffer).use { zip ->
            entries.forEach { (name, data) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry()
            }
        }
        return buffer.toByteArray()
    }

    private fun validFiles(): List<Pair<String, ByteArray>> = QwenAsrModelFiles.required.map { name ->
        name to when {
            name.endsWith(".onnx") -> ByteArray(1024 * 1024) // Structural import fixture, not an inference model.
            name.endsWith("merges.txt") -> ("#version: 0.2\n" + "a b\n".repeat(40)).toByteArray()
            name.endsWith("vocab.json") -> (0..1000).joinToString(",", "{", "}") { "\"token$it\":$it" }.toByteArray()
            else -> ("{\"added_tokens_decoder\":{}}" + " ".repeat(128)).toByteArray()
        }
    }

    @Test fun fullBundleSupportsWrapperFolderAndHasVersionSensitiveCacheKey() {
        val directory = temporary.newFolder()
        val entries = validFiles().map { (name, bytes) -> "qwen/$name" to bytes }
        QwenAsrModelFiles.extractBundle(ByteArrayInputStream(bundle(*entries.toTypedArray())), directory)
        assertTrue(QwenAsrModelFiles.missing(directory).isEmpty())
        val version = QwenAsrModelFiles.version(directory)
        File(directory, "tokenizer/vocab.json").appendText(" ")
        assertNotEquals(version, QwenAsrModelFiles.version(directory))
        File(directory, "encoder.int8.onnx").delete()
        assertEquals(listOf("encoder.int8.onnx"), QwenAsrModelFiles.missing(directory))
    }

    @Test fun rejectsTraversalDuplicateIncompleteBundlesAndBadTokenizer() {
        val directory = temporary.newFolder()
        assertThrows(IllegalArgumentException::class.java) { QwenAsrModelFiles.targetName("../vocab.json") }
        assertThrows(IllegalArgumentException::class.java) {
            QwenAsrModelFiles.extractBundle(ByteArrayInputStream(bundle("../encoder.int8.onnx" to ByteArray(1))), directory)
        }
        val one = validFiles().first()
        assertThrows(IllegalArgumentException::class.java) {
            QwenAsrModelFiles.extractBundle(ByteArrayInputStream(bundle(one, "wrapper/${one.first}" to one.second)), directory)
        }
        assertThrows(IllegalArgumentException::class.java) {
            QwenAsrModelFiles.extractBundle(ByteArrayInputStream(bundle(one)), directory)
        }
        val vocab = temporary.newFile()
        vocab.writeText("{\"wrong\":true}" + " ".repeat(128))
        assertThrows(IllegalArgumentException::class.java) { QwenAsrModelFiles.validate("tokenizer/vocab.json", vocab) }
    }

    @Test fun completeReplacementCleansLegacyDirectoryButKeepsRecordingAndNewModel() {
        val files = temporary.newFolder()
        val legacy = File(files, QuickMemoAsrModelStore.MODEL_DIR).apply { mkdirs() }
        File(legacy, "model.int8.onnx").writeText("old model")
        val recording = File(files, "quick_memos/audio/recording.m4a").apply {
            parentFile.mkdirs(); writeText("original recording")
        }
        val current = File(files, QwenAsrModelFiles.DIRECTORY)
        QwenAsrModelFiles.extractBundle(ByteArrayInputStream(bundle(*validFiles().toTypedArray())), current)
        assertTrue(QwenAsrModelFiles.cleanupLegacyModel(files))
        assertFalse(legacy.exists())
        assertEquals("original recording", recording.readText())
        assertTrue(QwenAsrModelFiles.missing(current).isEmpty())
        assertTrue(QwenAsrModelFiles.cleanupLegacyModel(files)) // Idempotent on the next transcription.
    }

    @Test fun incompleteOrInvalidReplacementKeepsLegacyFiles() {
        val files = temporary.newFolder()
        val legacy = File(files, QuickMemoAsrModelStore.MODEL_DIR).apply { mkdirs() }
        val oldModel = File(legacy, "model.int8.onnx").apply { writeText("old model") }
        assertFalse(QwenAsrModelFiles.cleanupLegacyModel(files))
        assertEquals("old model", oldModel.readText())
        val current = File(files, QwenAsrModelFiles.DIRECTORY)
        QwenAsrModelFiles.extractBundle(ByteArrayInputStream(bundle(*validFiles().toTypedArray())), current)
        File(current, "tokenizer/vocab.json").writeText("{\"bad\":true}" + " ".repeat(128))
        assertThrows(IllegalArgumentException::class.java) { QwenAsrModelFiles.cleanupLegacyModel(files) }
        assertEquals("old model", oldModel.readText())
    }

    @Test fun importEnforcesSizeLimitBeforeAcceptingFile() {
        assertThrows(IllegalArgumentException::class.java) {
            QwenAsrModelFiles.copyLimited(ByteArrayInputStream(ByteArray(32)), temporary.newFile(), 16)
        }
    }
}
