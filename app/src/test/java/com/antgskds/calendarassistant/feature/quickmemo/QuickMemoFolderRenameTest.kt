package com.antgskds.calendarassistant.feature.quickmemo

import com.antgskds.calendarassistant.feature.quickmemo.data.QuickMemoRepository
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoDao
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoFolderEntity
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class QuickMemoFolderRenameTest {
    @Test
    fun renamePreservesIdentityAndRejectsBlankDuplicateAndMissingFolders() = runBlocking {
        val original = QuickMemoFolderEntity("saved-id", "文章", createdAt = 10, updatedAt = 20)
        val other = QuickMemoFolderEntity("other-id", "VIDEO", createdAt = 30, updatedAt = 40)
        val folders = linkedMapOf(original.id to original, other.id to other)
        var writes = 0
        val dao = Proxy.newProxyInstance(QuickMemoDao::class.java.classLoader, arrayOf(QuickMemoDao::class.java)) { _, method, args ->
            when {
                method.name.startsWith("observe") -> flowOf(emptyList<Any>())
                method.name == "getAllFolders" -> folders.values.toList()
                method.name == "renameFolder" -> {
                    val id = args!![0] as String
                    val current = folders[id]
                    if (current == null) 0 else {
                        folders[id] = current.copy(name = args[1] as String, updatedAt = args[2] as Long)
                        writes++
                        1
                    }
                }
                else -> error("Rename must not write memo data or replace a folder: ${method.name}")
            }
        } as QuickMemoDao
        val repository = QuickMemoRepository(dao)
        repository.renameFolder(original.id, "  阅读  ")
        val renamed = folders.getValue(original.id)
        assertEquals("阅读", renamed.name)
        assertEquals(original.id, renamed.id)
        assertEquals(original.createdAt, renamed.createdAt)
        assertTrue(renamed.updatedAt > original.updatedAt)
        assertEquals(other, folders[other.id])
        val snapshot = folders.toMap()
        listOf(original.id to "  ", original.id to " video ", "missing" to "新名称").forEach { (id, name) ->
            assertTrue(runCatching { repository.renameFolder(id, name) }.isFailure)
            assertEquals(snapshot, folders)
        }
        assertEquals(1, writes)
    }
}
