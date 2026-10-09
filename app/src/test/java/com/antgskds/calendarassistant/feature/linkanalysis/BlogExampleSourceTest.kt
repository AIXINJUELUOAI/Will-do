package com.antgskds.calendarassistant.feature.linkanalysis

import com.antgskds.calendarassistant.feature.linkanalysis.data.LinkSourceStore
import com.antgskds.calendarassistant.feature.linkanalysis.domain.*
import com.dokar.quickjs.*
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Exercises the published JS in the same engine as Android, without live network or AI calls. */
class BlogExampleSourceTest {
    private val example: File
        get() = listOf(File("examples/link-sources/willdo-blog"), File("../examples/link-sources/willdo-blog"))
            .first { File(it, "main.js").isFile }
    private val url = "https://aixinjueluoonline.top/posts/will-do-manual/"

    @Test fun packageOnlyRequestsTheBlogDomain() {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            listOf("manifest.json", "main.js").forEach { name ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(File(example, name).readBytes())
                zip.closeEntry()
            }
        }
        val pack = LinkSourceStore.readPackage(bytes.toByteArray().inputStream())
        assertEquals(setOf("main.js"), pack.files.keys)
        assertEquals(listOf("aixinjueluoonline.top"), pack.manifest.permissions.networkHosts)
        assertFalse(pack.manifest.permissions.browser)
        assertTrue(pack.manifest.permissions.replay.isEmpty())
        assertTrue(pack.manifest.loginEntries.isEmpty())
        assertTrue(LinkAnalysisPolicy.matches(pack.manifest, url))
        assertFalse(LinkAnalysisPolicy.matches(pack.manifest, "https://example.com/posts/test/"))
        assertFalse(LinkAnalysisPolicy.allows(pack.manifest, "https://sub.aixinjueluoonline.top/posts/test/"))
    }

    @Test fun quickJsPreservesArticleTextAndExcludesPageChrome() = runBlocking {
        val result = runSource(
            inputUrl = "$url?from=example#chapter",
            finalUrl = url,
            html = """
                <meta name="author" content="博客作者">
                <nav>导航标记</nav>
                <!-- <div class="markdown-content">假容器</div> -->
                <script>const sample = '<div class="markdown-content">脚本标记</div>';</script>
                <div class="prose markdown-content">
                  <h1>测试标题<a class="anchor"><span>#</span></a></h1>
                  <h2>第一节</h2><div><p>内层正文 &amp; 原样 &lt;标签&gt;</p></div>
                  <p>结尾正文 &#x26; &#65; &#x1F600;</p>
                  <ul><li><p>列表正文</p></li></ul>
                  <table><tr><th>类别</th><th>用途</th></tr><tr><td>事件</td><td>提醒</td></tr></table>
                  <img src="/owned-image.png"><figcaption>图片说明</figcaption>
                  <style>样式标记 {}</style>
                </div>
                <footer>页脚标记</footer>
            """.trimIndent(),
        )
        assertEquals("ok", result.status)
        assertEquals("测试标题", result.title)
        assertEquals("博客作者", result.author)
        assertEquals("$url?from=example#chapter", result.source?.url)
        assertEquals(url, result.source?.canonicalUrl)
        assertEquals("full", result.body.kind)
        assertTrue(result.body.text.contains("内层正文 & 原样 <标签>"))
        assertTrue(result.body.text.contains("结尾正文 & A 😀"))
        assertTrue(result.body.text.contains("• 列表正文"))
        assertTrue(result.body.text.contains("类别 | 用途"))
        assertTrue(result.body.text.contains("事件 | 提醒"))
        assertTrue(result.body.text.contains("图片说明"))
        listOf("导航标记", "脚本标记", "页脚标记", "样式标记", "假容器", "#").forEach {
            assertFalse("Page chrome leaked: $it", result.body.text.contains(it))
        }
        assertTrue(result.media.isEmpty())
        assertEquals(1, result.warnings.size)
    }

    @Test fun unsupportedLinksNeverCallHttp() = runBlocking {
        listOf(
            "https://aixinjueluoonline.top/",
            "https://example.com/posts/test/",
            "https://aixinjueluoonline.top.evil.example/posts/test/",
            "https://user@aixinjueluoonline.top/posts/test/",
            "https://aixinjueluoonline.top/posts/test/extra/",
        ).forEach { target ->
            val result = runSource(inputUrl = target, allowHttp = false)
            assertEquals("error", result.status)
            assertEquals("NO_TARGET", result.error?.code)
        }
    }

    @Test fun failuresDoNotReturnUnrelatedPageText() = runBlocking {
        assertEquals("HTTP_FAILED", runSource(status = 404).error?.code)
        assertEquals("ACCESS_REQUIRED", runSource(status = 403).error?.code)
        assertEquals("TARGET_MISMATCH", runSource(finalUrl = "https://aixinjueluoonline.top/").error?.code)
        assertEquals("TARGET_MISMATCH", runSource(finalUrl = "https://aixinjueluoonline.top/posts/other/").error?.code)
        assertEquals("NO_ARTICLE", runSource(html = "<h1>仅导航和标题</h1>").error?.code)
        assertEquals("NO_ARTICLE", runSource(html = """<div class="markdown-content"><h1>未闭合</h1>""").error?.code)
        assertEquals("NO_CONTENT", runSource(html = """<div class="markdown-content"><p>没有文章标题</p></div>""").error?.code)
        val failed = runSource(failureCode = "NETWORK_TIMEOUT")
        assertEquals("NETWORK_TIMEOUT", failed.error?.code)
        assertFalse(LinkSourceProtocol.json.encodeToString(failed).contains("private-error-marker"))
        assertTrue(failed.body.text.isEmpty())
    }

    @Test fun tutorialReturnCasesMatchAppProtocol() {
        val fixture = File(example.parentFile, "protocol-cases.json").readText()
        val cases = LinkSourceProtocol.json.parseToJsonElement(fixture).jsonObject["cases"]!!.jsonArray
        assertEquals(6, cases.size)
        cases.forEach { case ->
            val input = LinkSourceProtocol.json.decodeFromString<LinkSourceInput>(case.jsonObject["input"].toString())
            val result = LinkSourceProtocol.json.decodeFromString<LinkExtractionResult>(case.jsonObject["result"].toString())
            LinkSourceProtocol.validate(result, input.requestId)
            assertTrue(result.status == "error" || result.source?.url == input.url)
        }
    }

    private suspend fun runSource(
        inputUrl: String = url,
        finalUrl: String = inputUrl,
        status: Int = 200,
        html: String = "",
        allowHttp: Boolean = true,
        failureCode: String = "",
    ): LinkExtractionResult {
        val source = File(example, "main.js").readText()
        val loader = moduleLoader { load { if (it == "main.js") ModuleContent.Source(source) else null } }
        val js = QuickJs.create(Dispatchers.IO, loader)
        try {
            js.evaluationTimeoutMillis = 2000
            val input = LinkSourceInput(requestId = "blog-example-test", url = inputUrl)
            var output = ""
            js.asyncFunction<String, String>("hostHttp") { options ->
                check(allowHttp) { "Unsupported target reached host HTTP" }
                val request = LinkSourceProtocol.json.parseToJsonElement(options).jsonObject
                assertEquals(inputUrl, request["url"]?.jsonPrimitive?.content)
                assertEquals("GET", request["method"]?.jsonPrimitive?.content)
                buildJsonObject {
                    put("status", status); put("url", finalUrl); put("body", html)
                }.toString()
            }
            js.function<String, Unit>("finish") { output = it }
            js.evaluate<Any?>(
                """
                import { extract } from './main.js';
                const host = {http: {request: async args => {
                    const code = ${JsonPrimitive(failureCode)};
                    if (code) {
                        const error = new Error('private-error-marker');
                        error.sourceCode = code;
                        throw error;
                    }
                    return JSON.parse(await hostHttp(JSON.stringify(args)));
                }}};
                finish(JSON.stringify(await extract(${LinkSourceProtocol.json.encodeToString(input)}, host)));
                """.trimIndent(),
                filename = "blog-runner.js", asModule = true,
            )
            return LinkSourceProtocol.json.decodeFromString<LinkExtractionResult>(output).also {
                LinkSourceProtocol.validate(it, input.requestId)
                assertTrue(it.status == "error" || it.source?.url == inputUrl)
            }
        } finally {
            js.close()
        }
    }
}
