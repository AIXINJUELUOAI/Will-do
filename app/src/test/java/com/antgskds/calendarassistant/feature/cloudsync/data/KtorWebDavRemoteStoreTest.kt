package com.antgskds.calendarassistant.feature.cloudsync.data

import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavConnectionConfig
import com.antgskds.calendarassistant.feature.cloudsync.domain.WebDavCredentials
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KtorWebDavRemoteStoreTest {
    @Test
    fun testsWritableServerWithMove() = runBlocking {
        val methods = mutableListOf<String>()
        val engine = MockEngine { request ->
            methods += request.method.value
            when (request.method.value) {
                "PROPFIND" -> respond("", HttpStatusCode(207, "Multi-Status"))
                "PUT" -> respond("", HttpStatusCode.Created)
                "GET" -> respond(TEST_PAYLOAD, HttpStatusCode.OK)
                "MOVE" -> respond("", HttpStatusCode.Created)
                "DELETE" -> respond("", HttpStatusCode.NoContent)
                "OPTIONS" -> respond("", HttpStatusCode.OK, headersOf("DAV", "1, 2"))
                else -> error("Unexpected method ${request.method.value}")
            }
        }
        val store = KtorWebDavRemoteStore(HttpClient(engine)) { TEST_TOKEN }

        val result = store.testConnection(CONFIG, CREDENTIALS)

        assertTrue(result.message, result.success)
        assertTrue(result.capabilities?.moveSupported == true)
        assertEquals("1, 2", result.capabilities?.davHeader)
        assertTrue("MOVE" in methods)
        assertEquals("Basic dXNlcjpwYXNz", engine.requestHistory.first().headers[HttpHeaders.Authorization])
    }

    @Test
    fun fallsBackWhenMoveIsUnavailable() = runBlocking {
        var putCount = 0
        val engine = MockEngine { request ->
            when (request.method.value) {
                "PROPFIND" -> respond("", HttpStatusCode(207, "Multi-Status"))
                "PUT" -> {
                    putCount++
                    respond("", HttpStatusCode.Created)
                }
                "GET" -> respond(TEST_PAYLOAD, HttpStatusCode.OK)
                "MOVE" -> respond("", HttpStatusCode.NotImplemented)
                "DELETE" -> respond("", HttpStatusCode.NoContent)
                "OPTIONS" -> respond("", HttpStatusCode.OK)
                else -> error("Unexpected method ${request.method.value}")
            }
        }
        val store = KtorWebDavRemoteStore(HttpClient(engine)) { TEST_TOKEN }

        val result = store.testConnection(CONFIG, CREDENTIALS)

        assertTrue(result.message, result.success)
        assertFalse(result.capabilities?.moveSupported ?: true)
        assertEquals(2, putCount)
    }

    private companion object {
        const val TEST_TOKEN = "fixed-token"
        const val TEST_PAYLOAD = "WillDo WebDAV connection test fixed-token"
        val CONFIG = WebDavConnectionConfig(
            baseUrl = "https://example.com/dav",
            username = "user",
            remotePath = "/WillDo",
        )
        val CREDENTIALS = WebDavCredentials("user", "pass")
    }
}
