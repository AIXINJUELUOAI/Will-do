package com.antgskds.calendarassistant.feature.quickmemo

import com.antgskds.calendarassistant.feature.quickmemo.data.QuickMemoRepository
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoDao
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class QuickMemoContentUpdateTest {
    @Test fun titleAndBodyUseOneTargetedWriteWithNormalizedInput() = runBlocking {
        var writes = 0
        val repository = repository { args ->
            writes++
            assertEquals(42L, args[0])
            assertEquals("新标题", args[1])
            assertEquals("第一行\n第二行\n第三行", args[2])
            assertTrue(args[3] as Long > 0)
            1
        }
        repository.updateContent(42, "  新标题  ", "第一行\r\n第二行\r第三行")
        assertEquals(1, writes)
    }

    @Test fun savingDeletedMemoFailsInsteadOfReportingSuccess() = runBlocking {
        val failure = runCatching { repository { 0 }.updateContent(42, "", "正文") }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
        assertEquals("随口记不存在", failure?.message)
    }

    private fun repository(onUpdate: (Array<out Any?>) -> Int): QuickMemoRepository {
        val dao = Proxy.newProxyInstance(QuickMemoDao::class.java.classLoader,
            arrayOf(QuickMemoDao::class.java)) { _, method, args ->
            when {
                method.name.startsWith("observe") -> flowOf(emptyList<Any>())
                method.name == "updateContent" -> onUpdate(requireNotNull(args))
                else -> error("Content edits must not replace a stale whole entity: ${method.name}")
            }
        } as QuickMemoDao
        return QuickMemoRepository(dao)
    }
}
