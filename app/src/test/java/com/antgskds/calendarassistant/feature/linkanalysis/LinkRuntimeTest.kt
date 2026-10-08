package com.antgskds.calendarassistant.feature.linkanalysis
import com.dokar.quickjs.*
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class LinkRuntimeTest {
    @Test fun importedModuleCanAwaitHostAndReturnStructuredOutput() = runBlocking {
        val loader=moduleLoader { load { if(it=="main.js") ModuleContent.Source("export async function extract(input,host){return {requestId:input.requestId,body:await host.request('value')}}") else null } }
        val js=QuickJs.create(Dispatchers.IO,loader)
        try {
            var result=""
            js.asyncFunction<String,String>("hostCall") { delay(1); it+" returned" }
            js.function<String,Unit>("finish") { result=it }
            js.evaluate<Any?>("import {extract} from './main.js';finish(JSON.stringify(await extract({requestId:'test'},{request:hostCall})));",filename="runner.js",asModule=true)
            assertEquals("""{"requestId":"test","body":"value returned"}""",result)
        } finally { js.close() }
        Unit
    }
    @Test fun sourceBusyLoopIsInterruptible() = runBlocking {
        val js=QuickJs.create(Dispatchers.IO)
        js.evaluationTimeoutMillis=50
        try {
            assertThrows(Exception::class.java) { runBlocking { withTimeout(2000) { js.evaluate<Unit>("while(true){}") } } }
        } finally { js.close() }
        Unit
    }
}
