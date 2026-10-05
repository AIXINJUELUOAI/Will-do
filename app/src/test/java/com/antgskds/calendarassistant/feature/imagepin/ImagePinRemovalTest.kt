package com.antgskds.calendarassistant.feature.imagepin

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ImagePinRemovalTest {
    @get:Rule val temporary = TemporaryFolder()

    private fun images(): Map<Long, File> = linkedMapOf(
        10L to temporary.newFile("pin_10.image").apply { writeText("first image") },
        11L to temporary.newFile("pin_11.image").apply { writeText("second image") },
    )

    @Test fun removingOneCopyPersistsOnlyTheSurvivorBeforeDeletingAndLeavesOriginalUntouched() {
        val images = images()
        val original = temporary.newFile("gallery-original.jpg").apply { writeText("original image") }
        var index = listOf(10L, 11L)
        val remaining = removeImagePinCopy(11L, 11L, images, images.getValue(11L).absolutePath) {
            assertTrue(images.values.all(File::isFile))
            index = it
            true
        }
        assertEquals(listOf(10L), remaining)
        assertEquals(listOf(10L), index)
        assertEquals("first image", images.getValue(10L).readText())
        assertFalse(images.getValue(11L).exists())
        assertEquals("original image", original.readText())
        // 批次 id 保持 11；删除最后一张仍应成功，持久化空索引供控制器结束胶囊。
        val last = removeImagePinCopy(11L, 11L, images.filterKeys { it in index }, images.getValue(10L).absolutePath) {
            index = it
            true
        }
        assertEquals(emptyList<Long>(), last)
        assertTrue(index.isEmpty())
        assertFalse(images.getValue(10L).exists())
    }

    @Test fun failedIndexSaveKeepsBothCopiesAndTheGalleryOriginal() {
        val images = images()
        val original = temporary.newFile("gallery-original.jpg").apply { writeText("original image") }
        assertThrows(IllegalStateException::class.java) {
            removeImagePinCopy(11L, 11L, images, images.getValue(10L).absolutePath) { false }
        }
        assertEquals("first image", images.getValue(10L).readText())
        assertEquals("second image", images.getValue(11L).readText())
        assertEquals("original image", original.readText())
    }

    @Test fun staleViewerOrUnlistedPathCannotModifyTheCurrentBatch() {
        val images = images()
        val original = temporary.newFile("gallery-original.jpg")
        val shouldNotPersist: (List<Long>) -> Boolean = { error("must not persist") }
        assertNull(removeImagePinCopy(12L, 11L, images, images.getValue(10L).absolutePath, shouldNotPersist))
        assertNull(removeImagePinCopy(11L, 11L, images, original.absolutePath, shouldNotPersist))
        assertNull(removeImagePinCopy(0L, 0L, images, images.getValue(10L).absolutePath, shouldNotPersist))
        assertTrue(images.values.all(File::isFile))
        assertTrue(original.isFile)
    }

    @Test fun repeatedRemovalIsIgnoredAndLegacySingleImageCanBeRemoved() {
        val images = images()
        val legacy = images.filterKeys { it in restoreImagePinIds(null, 10L) }
        val path = images.getValue(10L).absolutePath
        val remaining = removeImagePinCopy(10L, 10L, legacy, path) { true }
        assertEquals(emptyList<Long>(), remaining)
        assertNull(removeImagePinCopy(10L, 10L, legacy.filterKeys { it in remaining!! }, path) { error("must not persist") })
        assertTrue(images.getValue(11L).isFile)
    }
}
