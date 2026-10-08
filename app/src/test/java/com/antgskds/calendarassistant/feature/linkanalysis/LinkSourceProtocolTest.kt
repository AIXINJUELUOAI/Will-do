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
            assertFalse(disabledReplacement.enabled)
            assertNull(store.find("https://example.com/post"))
            store.remove(next.manifest.id)
            assertTrue(store.sources.value.isEmpty())
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

}
