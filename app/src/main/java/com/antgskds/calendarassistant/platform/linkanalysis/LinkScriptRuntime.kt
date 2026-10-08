package com.antgskds.calendarassistant.platform.linkanalysis
import android.content.Context
import com.antgskds.calendarassistant.feature.linkanalysis.domain.*
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog as Limits
import com.dokar.quickjs.*
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Paths
import com.antgskds.calendarassistant.shared.util.AppLogger as Log

class LinkScriptRuntime(private val context: Context) {
    suspend fun extract(pack: LinkSourcePackage, input: LinkSourceInput): LinkExtractionResult = withTimeout(Limits.LINK_SCRIPT_TIMEOUT_MS.toLong()) {
        val http = LinkHttpClient(pack.manifest,input.requestId)
        val browser = LinkBrowserSession(context, pack.manifest, http, input.requestId)
        val loader = moduleLoader {
            normalize { base, name ->
                require(!name.contains(':') && !name.startsWith("/") && !name.contains('\\'))
                val resolved = Paths.get(base).parent?.resolve(name)?.normalize()?.toString()?.replace('\\','/') ?: name.removePrefix("./")
                require(LinkSourceProtocol.portablePath(resolved)); resolved
            }
            load { name -> pack.files[name]?.let(ModuleContent::Source) }
        }
        val js = QuickJs.create(Dispatchers.IO, loader)
        var output: String? = null
        try {
            js.memoryLimit = Limits.LINK_JS_MEMORY_BYTES; js.evaluationTimeoutMillis = Limits.LINK_SCRIPT_TIMEOUT_MS.toLong()
            Log.i("LinkAnalysis","trace=" + input.requestId + " stage=SCRIPT_START")

            js.asyncFunction<String,String>("__host") { payload ->
                val call = LinkSourceProtocol.json.parseToJsonElement(payload).jsonObject
                val args = call["args"]?.jsonObject ?: buildJsonObject {}
                val method = call["method"]?.jsonPrimitive?.content
                try {
                val result = when (method) {
                    "http" -> http.request(args)
                    "open" -> browser.open(args)
                    "evaluate" -> browser.evaluate(args["expression"]!!.jsonPrimitive.content)
                    "wait" -> browser.waitForData(args["expression"]!!.jsonPrimitive.content, args["timeoutMs"]?.jsonPrimitive?.long ?: Limits.LINK_SCRIPT_TIMEOUT_MS.toLong())
                    "close" -> { browser.close(); JsonNull }
                    "report" -> JsonNull
                    else -> error("未知宿主方法")
                }
                result.toString()
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (error: Exception) {
                    val failure = LinkAnalysisFailure.describe(error,"EXTRACTING")
                    Log.w("LinkAnalysis","trace=" + input.requestId + " stage=HOST_FAILED code=" + failure.code.name)
                    buildJsonObject {
                        putJsonObject("__hostFailure") {
                            put("code",failure.code.name); put("message",failure.code.userMessage)
                        }
                    }.toString()
                }
            }
            js.function<String,Unit>("__finish") { result ->
                require(result.toByteArray().size <= Limits.LINK_RESULT_MAX_BYTES); output = result
            }
            val inputJson = LinkSourceProtocol.json.encodeToString(input)
            val entryJson = JsonPrimitive("./" + pack.manifest.entry).toString()
            js.evaluate<Any?>("""
                import { extract } from $entryJson;
                const call=async(method,args={})=>{
                  const value=JSON.parse(await __host(JSON.stringify({method,args})));
                  if(value?.__hostFailure) {
                    const error=new Error(value.__hostFailure.message);error.sourceCode=value.__hostFailure.code;throw error;
                  }
                  return value;
                };
                const host=Object.freeze({
                  http:Object.freeze({request:(args)=>call('http',args)}),
                  browser:Object.freeze({
                    open:(args)=>call('open',typeof args==='string'?{url:args}:args),
                    evaluate:(expression)=>call('evaluate',{expression}),
                    waitForData:(expression,timeoutMs)=>call('wait',{expression,timeoutMs}),
                    close:()=>call('close')
                  }),report:(message)=>call('report',{message})
                });
                __finish(JSON.stringify(await extract($inputJson,host)));
            """.trimIndent(), filename = "__runner.js", asModule = true)
            Log.i("LinkAnalysis","trace=" + input.requestId + " stage=SCRIPT_DONE result_bytes=" + output.orEmpty().toByteArray().size)
            LinkSourceProtocol.json.decodeFromString<LinkExtractionResult>(output ?: error("源没有返回结果")).also {
                try {
                    LinkSourceProtocol.validate(it, input.requestId)
                    require(it.source?.url == input.url || it.status == "error")
                } catch (error: IllegalArgumentException) {
                    throw LinkAnalysisFailure(LinkFailureCode.SOURCE_INVALID, cause = error)
                }
            }
        } finally { js.close(); browser.close() }
    }
}
