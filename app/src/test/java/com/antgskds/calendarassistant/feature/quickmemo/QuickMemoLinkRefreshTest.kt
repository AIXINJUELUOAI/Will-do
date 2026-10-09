package com.antgskds.calendarassistant.feature.quickmemo

import com.antgskds.calendarassistant.feature.quickmemo.data.local.*
import com.antgskds.calendarassistant.feature.quickmemo.domain.*
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class QuickMemoLinkRefreshTest {
    @Test fun refreshUsesOriginalRecordAndPreservesEdits() = runBlocking {
        val oldUrl = "https://www.coolapk.com/feed/123?s=old"
        val newUrl = "https://www.coolapk.com/feed/123?s=new"
        var saved = QuickMemoEntity(id=7, type=QuickMemoType.LINK, title="我的标题",
            bodyText="我的文案\n$oldUrl", sourceUrl=oldUrl, linkKey="coolapk:feed:123",
            folderId="folder", createdAt=10, updatedAt=20, sortRank=30)
        var writes = 0
        val dao = Proxy.newProxyInstance(QuickMemoDao::class.java.classLoader, arrayOf(QuickMemoDao::class.java)) { _, method, args ->
            when (method.name) {
                "findLink" -> saved
                "refreshLinkSource" -> {
                    saved=saved.copy(sourceUrl=args!![1] as String, bodyText=args[2] as String, updatedAt=args[3] as Long)
                    writes++; Unit
                }
                "insertLinkIfAbsent" -> {
                    val memo=args!![0] as QuickMemoEntity
                    val link=QuickMemoLink(requireNotNull(memo.sourceUrl),"","",requireNotNull(memo.linkKey))
                    if (QuickMemoLinkRefreshPolicy.needsRefresh(saved.sourceUrl,link)) {
                        saved=saved.copy(sourceUrl=link.url,
                            bodyText=QuickMemoLinkRefreshPolicy.refreshedBody(saved.bodyText,saved.sourceUrl,link.url),
                            updatedAt=memo.updatedAt)
                        writes++
                    }
                    requireNotNull(saved.id)
                }
                else -> error("Unexpected write: " + method.name)
            }
        } as QuickMemoDao
        val original=saved
        assertEquals(7L,dao.insertLinkIfAbsent(QuickMemoEntity(type=QuickMemoType.LINK,
            sourceUrl=newUrl, linkKey=saved.linkKey, bodyText="分享者新文案", title="新标题",updatedAt=40)))
        assertEquals(original.copy(sourceUrl=newUrl,bodyText="我的文案\n$newUrl",updatedAt=40),saved)
        dao.insertLinkIfAbsent(QuickMemoEntity(sourceUrl=newUrl,linkKey=saved.linkKey,updatedAt=50))
        dao.insertLinkIfAbsent(QuickMemoEntity(sourceUrl="https://www.coolapk.com/feed/123",linkKey=saved.linkKey,updatedAt=60))
        assertEquals(1,writes)
        assertEquals(newUrl,saved.sourceUrl)
        Unit
    }

    @Test fun newTokenIsOfferedButTokenlessCopyDoesNotReplaceSavedLink() {
        val saved="https://www.coolapk.com/feed/123?s=old"
        val fresh=QuickMemoLinkParser.parse("https://www.coolapk.com/feed/123?s=new")!!
        assertEquals("coolapk:feed:123",fresh.dedupKey)
        assertTrue(QuickMemoLinkRefreshPolicy.needsRefresh(saved,fresh))
        assertFalse(QuickMemoLinkRefreshPolicy.needsRefresh(fresh.url,fresh))
        assertFalse(QuickMemoLinkRefreshPolicy.needsRefresh(saved,QuickMemoLinkParser.parse("https://www.coolapk.com/feed/123")!!))
        assertEquals("我自己改过的正文",QuickMemoLinkRefreshPolicy.refreshedBody("我自己改过的正文",saved,fresh.url))
    }
}
