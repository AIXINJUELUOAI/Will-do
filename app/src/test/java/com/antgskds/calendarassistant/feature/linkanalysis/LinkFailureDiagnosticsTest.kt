package com.antgskds.calendarassistant.feature.linkanalysis

import com.antgskds.calendarassistant.feature.linkanalysis.domain.*
import com.antgskds.calendarassistant.platform.linkanalysis.LinkHttpClient
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import java.io.ByteArrayInputStream
import java.net.UnknownHostException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LinkFailureDiagnosticsTest {
    @Test fun fullHtmlMayExceedOldLimitButNewLimitNeverReturnsTruncation() = runBlocking {
        val manifest=LinkSourceManifest(1,"test.source","测试","1",matches=listOf(LinkSourceMatch("example.com")),
            permissions=LinkSourcePermissions(listOf("example.com")))
        val client=LinkHttpClient(manifest)
        val large=ByteArray(4*1024*1024) { 65 }
        assertArrayEquals(large,client.boundedRead(ByteArrayInputStream(large),ConfigCatalog.LINK_HTTP_MAX_BYTES))
        val boundary=ByteArray(ConfigCatalog.LINK_HTTP_MAX_BYTES) { 66 }
        assertArrayEquals(boundary,client.boundedRead(ByteArrayInputStream(boundary),ConfigCatalog.LINK_HTTP_MAX_BYTES))
        val thrown=assertThrows(LinkAnalysisFailure::class.java) {
            runBlocking { client.boundedRead(ByteArrayInputStream(boundary+byteArrayOf(67)),ConfigCatalog.LINK_HTTP_MAX_BYTES) }
        }
        assertEquals(LinkFailureCode.HTTP_TOO_LARGE,thrown.code)
        Unit
    }
    @Test fun causeAndSourceFailureNeverExposeInputMessages() {
        val sensitive="https://example.com?s=secret Cookie=credential private article"
        val failure=LinkAnalysisFailure.describe(IllegalStateException(sensitive,UnknownHostException(sensitive)),"EXTRACTING")
        assertEquals(LinkFailureCode.NETWORK_DNS,failure.code)
        assertFalse(failure.message!!.contains("secret"))
        assertEquals(LinkFailureCode.EXTRACTION_FAILED,LinkFailureCode.fromSource(sensitive))
        assertTrue(LinkFailureCode.fromSource("LINK_UNAVAILABLE").userMessage.contains("重新分享"))
        assertEquals(LinkFailureCode.AI_HTTP,LinkAnalysisFailure.describe(LinkAnalysisFailure(LinkFailureCode.AI_HTTP,401),"SUMMARIZING").code)
    }
}
