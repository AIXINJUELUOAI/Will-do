package com.antgskds.calendarassistant.feature.linkanalysis
import com.antgskds.calendarassistant.feature.linkanalysis.domain.*
import com.antgskds.calendarassistant.feature.linkanalysis.data.LinkSourceStore
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.*

class LinkSourceProtocolTest {
    private val manifest = LinkSourceManifest(1,"user.example","测试源","1",matches=listOf(LinkSourceMatch("example.com")),
        permissions=LinkSourcePermissions(listOf("example.com","*.example.com")))
    private fun zip(files: Map<String,String>): ByteArray {
        val bytes=ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip -> files.forEach { (name,body) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray()); zip.closeEntry()
        } }
        return bytes.toByteArray()
    }
    private fun pack(extra: Map<String,String> = emptyMap(), entry: String = "export async function extract() {}") =
        zip(mapOf("manifest.json" to LinkSourceProtocol.json.encodeToString(manifest),"main.js" to entry)+extra)
    @Test fun domainPermissionsDoNotMatchLookalikesOrCredentials() {
        assertTrue(LinkAnalysisPolicy.matches(manifest,"https://example.com/post?a=1"))
        assertFalse(LinkAnalysisPolicy.matches(manifest,"https://example.com.attacker.net/post"))
        assertFalse(LinkAnalysisPolicy.matches(manifest,"https://example.com@attacker.net/post"))
        assertFalse(LinkAnalysisPolicy.allows(manifest,"https://notexample.com"))
        assertTrue(LinkAnalysisPolicy.allows(manifest,"https://cdn.example.com/post"))
        assertFalse(LinkAnalysisPolicy.allows(manifest,"file:///data/private"))
        assertFalse(LinkAnalysisPolicy.allows(manifest,"https://example.com:22"))
    }
    @Test fun zipTraversalAndNonScriptPayloadAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { LinkSourceStore.readPackage(ByteArrayInputStream(pack(mapOf("../escape.js" to "")))) }
        assertThrows(IllegalArgumentException::class.java) { LinkSourceStore.readPackage(ByteArrayInputStream(pack(mapOf("main.dex" to "")))) }
    }
    @Test fun expandedZipLimitRejectsHighlyCompressedSource() {
        assertThrows(IllegalArgumentException::class.java) {
            LinkSourceStore.readPackage(ByteArrayInputStream(pack(entry="x".repeat(ConfigCatalog.LINK_SOURCE_MAX_BYTES+1))))
        }
    }
    @Test fun invalidReplacementPreservesInstalledSourceAndDigestChangesWithCode() = runBlocking {
        val dir=Files.createTempDirectory("link-source-test").toFile()
        try {
            val store=LinkSourceStore(dir)
            val first=store.import(ByteArrayInputStream(pack()))
            assertNotNull(store.load(first.manifest.id,first.digest))
            assertThrows(Exception::class.java) { runBlocking { store.import(ByteArrayInputStream(zip(mapOf("main.js" to "broken")))) } }
            assertNotNull(store.load(first.manifest.id,first.digest))
            val next=store.import(ByteArrayInputStream(pack(entry="export async function extract(){return 1}")))
            assertNotEquals(first.digest,next.digest)
            assertNull(store.load(first.manifest.id,first.digest))
            store.setEnabled(next.manifest.id,false)
            val disabledReplacement=store.import(ByteArrayInputStream(pack(entry="export async function extract(){return 2}")))
            assertTrue(disabledReplacement.enabled)
            assertNotNull(store.find("https://example.com/post"))
            store.remove(next.manifest.id)
            assertTrue(store.sources.value.isEmpty())
        } finally { dir.deleteRecursively() }
    }
    @Test fun importingDifferentSourceIdReplacesSingleSlotAndInvalidImportPreservesIt() = runBlocking {
        val dir = Files.createTempDirectory("link-single-source").toFile()
        try {
            val store = LinkSourceStore(dir)
            val first = store.import(ByteArrayInputStream(pack()))
            val nextManifest = manifest.copy(id = "user.next")
            val next = store.import(ByteArrayInputStream(zip(mapOf("manifest.json" to LinkSourceProtocol.json.encodeToString(nextManifest), "main.js" to "export async function extract(){return 1}"))))
            assertEquals(listOf(next), store.sources.value)
            assertNull(store.load(first.manifest.id, first.digest))
            assertThrows(Exception::class.java) { runBlocking { store.import(ByteArrayInputStream(zip(mapOf("main.js" to "broken")))) } }
            assertEquals(listOf(next), store.sources.value)
            assertNotNull(store.load(next.manifest.id, next.digest))
        } finally { dir.deleteRecursively() }
    }

    @Test fun resultMustBelongToCurrentRequestAndPreserveMediaOrder() {
        val result=LinkExtractionResult(requestId="new",status="ok",source=LinkResultSource("https://example.com"),
            contentType="image_post",media=listOf(LinkResultMedia("image","content_image",listOf("https://example.com/a"),order=1)))
        LinkSourceProtocol.validate(result,"new")
        assertThrows(IllegalArgumentException::class.java) { LinkSourceProtocol.validate(result,"old") }
        assertThrows(IllegalArgumentException::class.java) { LinkSourceProtocol.validate(result.copy(media=result.media+result.media),"new") }
    }
    @Test fun shortOriginalAudioFallsBackToVideoAndBackgroundMusicIsExcluded() {
        val speech=LinkResultMedia("audio","speech_audio",listOf("https://example.com/a"),durationMs=15_000,order=0)
        val video=LinkResultMedia("video","video",listOf("https://example.com/v"),durationMs=554_000,order=1)
        assertEquals(video,LinkAnalysisPolicy.preferredAudio(listOf(speech,video)))
        assertEquals(speech.copy(durationMs=554_680),LinkAnalysisPolicy.preferredAudio(listOf(speech.copy(durationMs=554_680),video)))
        assertNull(LinkAnalysisPolicy.preferredAudio(listOf(speech.copy(role="background_music"))))
        assertFalse(LinkAnalysisPolicy.coversVideo(15_000,554_000))
    }

    @Test fun legacySourceWithoutLoginEntriesStillImports() {
        val legacy = """{"protocolVersion":1,"id":"user.example","name":"旧源","version":"1","matches":[{"host":"example.com"}],"permissions":{"networkHosts":["example.com"]}}"""
        val source = LinkSourceStore.readPackage(ByteArrayInputStream(zip(mapOf(
            "manifest.json" to legacy, "main.js" to "export async function extract() {}"))))
        assertTrue(source.manifest.loginEntries.isEmpty())
        assertTrue(LinkAnalysisPolicy.loginEntries(source.manifest).isEmpty())
        assertTrue(LinkAnalysisPolicy.matches(source.manifest, "https://example.com/post"))
    }

    private fun loginManifest(entries: List<LinkSourceLoginEntry>) = manifest.copy(
        permissions = manifest.permissions.copy(browser = true), loginEntries = entries)

    @Test fun sourceDeclaredLoginSurvivesStorageAndInvalidReplacement() = runBlocking {
        val dir = Files.createTempDirectory("link-login-source").toFile()
        try {
            val store = LinkSourceStore(dir)
            val entry = LinkSourceLoginEntry("示例站点", "https://example.com/login", "ExampleBrowser/1.0")
            val withLogin = loginManifest(listOf(entry))
            fun loginPack(value: LinkSourceManifest) = zip(mapOf(
                "manifest.json" to LinkSourceProtocol.json.encodeToString(value),
                "main.js" to "export async function extract() {}"))
            val installed = store.import(ByteArrayInputStream(loginPack(withLogin)))
            val reopened = LinkSourceStore(dir)
            assertEquals(listOf(entry), LinkAnalysisPolicy.loginEntries(reopened.sources.value.single().manifest))
            assertEquals(listOf(entry), reopened.load(installed.manifest.id, installed.digest)?.manifest?.loginEntries)
            assertThrows(IllegalArgumentException::class.java) { runBlocking {
                store.import(ByteArrayInputStream(loginPack(withLogin.copy(
                    loginEntries = listOf(entry.copy(url = "https://other.example.net/login"))))))
            } }
            assertEquals(installed, store.sources.value.single())
            assertNotNull(store.load(installed.manifest.id, installed.digest))
        } finally { dir.deleteRecursively() }
    }

    @Test fun loginRejectsUnpermittedHostsSchemesCredentialsAndPorts() {
        val entry = LinkSourceLoginEntry("示例", "https://example.com/login")
        val withLogin = loginManifest(listOf(entry))
        LinkSourceProtocol.validate(withLogin)
        val invalid = listOf("http://example.com/login", "https://example.com.attacker.net/login",
            "https://example.com@attacker.net/login", "https://user@example.com/login",
            "https://example.com:8443/login", "file:///data/private", "javascript:alert(1)")
        invalid.forEach { url ->
            assertFalse(LinkAnalysisPolicy.allowsLoginUrl(withLogin, url))
            assertThrows(IllegalArgumentException::class.java) {
                LinkSourceProtocol.validate(withLogin.copy(loginEntries = listOf(entry.copy(url = url))))
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            LinkSourceProtocol.validate(withLogin.copy(permissions = withLogin.permissions.copy(browser = false)))
        }
    }

    @Test fun loginNameAndUserAgentHaveBoundedSafeDisplayValues() {
        val entry = LinkSourceLoginEntry("示例", "https://example.com/login")
        val withLogin = loginManifest(listOf(entry))
        val invalid = listOf(entry.copy(name = " "), entry.copy(name = "示例\n站点"),
            entry.copy(name = "x".repeat(ConfigCatalog.LINK_LOGIN_NAME_MAX_CHARS + 1)),
            entry.copy(userAgent = " "), entry.copy(userAgent = "Example\r\nInjected: value"),
            entry.copy(userAgent = "x".repeat(ConfigCatalog.LINK_BROWSER_UA_MAX_CHARS + 1)))
        invalid.forEach { value -> assertThrows(IllegalArgumentException::class.java) {
            LinkSourceProtocol.validate(withLogin.copy(loginEntries = listOf(value)))
        } }
    }

    @Test fun loginEntryCountIsBoundedAndDuplicateUrlsAreRejected() {
        val entries = (1..ConfigCatalog.LINK_LOGIN_MAX_ENTRIES).map {
            LinkSourceLoginEntry("示例 $it", "https://example.com/login/$it")
        }
        LinkSourceProtocol.validate(loginManifest(entries))
        assertThrows(IllegalArgumentException::class.java) {
            LinkSourceProtocol.validate(loginManifest(entries + LinkSourceLoginEntry("超限", "https://example.com/login/extra")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            LinkSourceProtocol.validate(loginManifest(listOf(entries.first(), entries.first().copy(name = "重复"))))
        }
    }

    @Test fun loginUsesCurrentSourceDeclarationsWithoutPlatformFallback() {
        val first = LinkSourceLoginEntry("站点一", "https://example.com/login")
        val second = LinkSourceLoginEntry("站点二", "https://auth.example.com/login")
        assertEquals(listOf(first), LinkAnalysisPolicy.loginEntries(loginManifest(listOf(first))))
        assertEquals(listOf(second), LinkAnalysisPolicy.loginEntries(loginManifest(listOf(second))))
        assertTrue(LinkAnalysisPolicy.loginEntries(manifest).isEmpty())
        val bad = first.copy(url = "https://other.example.net/login")
        assertEquals(listOf(first), LinkAnalysisPolicy.loginEntries(loginManifest(listOf(first, bad))))
    }

    @Test fun loginWildcardPermissionDoesNotIncludeRootOrLookalikeDomains() {
        val withLogin = manifest.copy(matches = listOf(LinkSourceMatch("www.example.com")),
            permissions = LinkSourcePermissions(listOf("*.example.com"), browser = true),
            loginEntries = listOf(LinkSourceLoginEntry("账号", "https://auth.example.com/login")))
        LinkSourceProtocol.validate(withLogin)
        assertTrue(LinkAnalysisPolicy.allowsLoginUrl(withLogin, "https://auth.example.com/verify"))
        assertFalse(LinkAnalysisPolicy.allowsLoginUrl(withLogin, "https://example.com/login"))
        assertFalse(LinkAnalysisPolicy.allowsLoginUrl(withLogin, "https://auth.example.com.attacker.net/login"))
    }

}
