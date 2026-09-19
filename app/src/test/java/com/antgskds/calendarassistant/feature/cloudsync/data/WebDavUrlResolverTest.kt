package com.antgskds.calendarassistant.feature.cloudsync.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WebDavUrlResolverTest {
    @Test
    fun resolvesRemotePathBelowEndpoint() {
        val resolver = WebDavUrlResolver(
            baseUrl = "https://example.com/dav/",
            remotePath = "/WillDo/同步 数据",
        )

        assertEquals("/WillDo/同步 数据", resolver.normalizedRemotePath)
        assertEquals(
            listOf(
                "https://example.com/dav/WillDo",
                "https://example.com/dav/WillDo/%E5%90%8C%E6%AD%A5%20%E6%95%B0%E6%8D%AE",
            ),
            resolver.remoteDirectoryUrls(),
        )
        assertEquals(
            "https://example.com/dav/WillDo/%E5%90%8C%E6%AD%A5%20%E6%95%B0%E6%8D%AE/batch.json",
            resolver.childUrl("batch.json"),
        )
    }

    @Test
    fun rejectsParentDirectoryTraversal() {
        assertThrows(IllegalArgumentException::class.java) {
            WebDavUrlResolver("https://example.com/dav", "/WillDo/../other")
        }
    }
}
