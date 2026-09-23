/*
 * Hook lifecycle and payment interception adapted from AutoAccounting 4e707980.
 * Copyright (C) 2023-2025 ankio (ankio@ankio.net).
 * See docs/third-party/autoaccounting/README.md and LICENSE for sources and integration changes.
 */
package com.antgskds.calendarassistant.platform.xposed

import android.app.Instrumentation
import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** 沿用上游主进程 onCreate 后安装、精确红包适配及固定支付入口；只观察并转发，不改宿主参数。 */
class PaymentCaptureHook : IXposedHookLoadPackage {
    private val executor by lazy {
        ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            ArrayBlockingQueue<Runnable>(ConfigCatalog.AUTO_ACCOUNTING_QUEUE_SIZE),
            { task -> Thread(task, "WillDo-payment").apply { isDaemon = true } },
            ThreadPoolExecutor.DiscardPolicy())
    }
    private val delivered = LinkedHashMap<String, Long>()
    private var installed = false

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName
        if (pkg != "com.tencent.mm" && pkg != "com.eg.android.AlipayGphone") return
        // Upstream core/App.kt: only the manifest's main process, after Application.onCreate.
        if (lpparam.processName != pkg) return
        install(pkg + " lifecycle") {
            XposedHelpers.findAndHookMethod(
                Instrumentation::class.java, "callApplicationOnCreate", Application::class.java,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        if (installed || param.hasThrowable()) return
                        val application = param.args[0] as? Application ?: return
                        if (application.packageName != pkg) return
                        installed = true
                        install(pkg + " payment hooks version=" + version(application)) {
                            if (pkg == "com.tencent.mm") installWechat(application, application.classLoader)
                            else installAlipay(application, application.classLoader)
                        }
                    }
                },
            )
        }
    }

    private fun installWechat(context: Context, loader: ClassLoader) {
        // Upstream DatabaseHooker: the fixed WCDB insert entry. No extra compat/update hooks.
        install("wechat database") {
            installWechatDatabase(context, XposedHelpers.findClass("com.tencent.wcdb.database.SQLiteDatabase", loader))
        }
        installWechatDetail(context, loader)
        // Upstream AdaptationUtils + LuckMoneyModel: cached complete signature, never hook ClassLoader.
        install("wechat red_packet adaptation") {
            val adaptation = WechatHookAdaptation(context, loader) { message, error ->
                log(message)
                if (error != null) XposedBridge.log(error)
            }
            val model = adaptation.cachedClass()
            if (model != null) {
                try { installRedPacketClass(context, model) }
                catch (error: Throwable) {
                    adaptation.invalidate()
                    log("wechat red_packet install failed")
                    XposedBridge.log(error)
                }
            } else {
                executor.execute { adaptation.adaptForNextLaunch() }
            }
        }
    }

    private fun installWechatDetail(context: Context, loader: ClassLoader) = install("wechat detail") {
        XposedHelpers.findAndHookMethod("com.tencent.xweb.WebView", loader, "evaluateJavascript",
            String::class.java, ValueCallback::class.java, object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    runCatching {
                        if (param.hasThrowable()) return
                        val script = param.args[0] as? String ?: return
                        val prefix = "javascript:WeixinJSBridge._handleMessageFromWeixin("
                        if (script.length > ConfigCatalog.AUTO_ACCOUNTING_MAX_PAYLOAD || !script.startsWith(prefix)) return
                        val body = script.removePrefix(prefix).trimEnd().removeSuffix(";").removeSuffix(")")
                        val args = JSONObject(body).optJSONObject("__json_message")?.optJSONObject("__params") ?: return
                        if (args.optString("err_msg") != "nativeWXPayCgiTunnel:ok") return
                        val response = JSONObject(args.optString("respbuf"))
                        if (!response.has("header") || !response.has("preview")) return
                        // 不转发带签名的服务跳转链接，只保留本次账单结构。
                        val data = JSONObject().put("ret_code", response.opt("ret_code"))
                            .put("header", response.opt("header")).put("preview", response.opt("preview"))
                        forward(context, "com.tencent.mm", JSONObject().put("kind", "wechat_detail").put("data", data).toString())
                    }.onFailure { log("wechat detail callback=${it.javaClass.simpleName}") }
                }
            })
    }

    private fun installWechatDatabase(context: Context, database: Class<*>) {
        fun callback() = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                runCatching {
                    if (param.hasThrowable()) return
                    val result = (param.result as? Number)?.toLong() ?: return
                    if (result < 0) return
                    val table = param.args[0] as? String ?: return
                    if (table != "message" && table != "AppMessage") return
                    val values = param.args[2] as? ContentValues ?: return
                    val type = values.getAsInteger("type") ?: return
                    // 只读取本次插入包含的支付信息，不回查聊天库，不修改宿主 ContentValues。
                    val kind = when {
                        table == "message" && type == 318767153 -> "wechat_payment"
                        table == "message" && type == 419430449 -> "wechat_transfer"
                        table == "AppMessage" && type == 5 -> "wechat_payment"
                        else -> return
                    }
                    val xml = values.getAsString(if (table == "AppMessage") "xml" else "content") ?: return
                    if (xml.length > ConfigCatalog.AUTO_ACCOUNTING_MAX_PAYLOAD) return
                    if (table == "AppMessage" && (!xml.contains("<template_header>") || !xml.contains("<pub_time>") ||
                            listOf("微信支付凭证", "收款到账通知", "退款到账通知").none(xml::contains))) return
                    val payload = JSONObject().put("kind", kind).put("createdAt", values.getAsLong("createTime"))
                        .put("isSend", values.getAsInteger("isSend")).put("xml", xml).toString()
                    forward(context, "com.tencent.mm", payload)
                }.onFailure { log("wechat database callback=${it.javaClass.simpleName}") }
            }
        }
        install("wechat ${database.name} insert") {
            XposedHelpers.findAndHookMethod(database, "insertWithOnConflict", String::class.java, String::class.java,
                ContentValues::class.java, Int::class.javaPrimitiveType, callback())
        }
    }

    private fun installRedPacketClass(context: Context, clazz: Class<*>) {
        // Upstream RedPackageHooker: hook only the class selected by LuckMoneyModel.
        val method = clazz.getDeclaredMethod("onGYNetEnd",
            Int::class.javaPrimitiveType!!, String::class.java, JSONObject::class.java)
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                runCatching {
                    if (param.args[0] != 0) return
                    val data = param.args[2] as? JSONObject ?: return
                    if (!data.has("receiveId") || !data.has("receiveStatus") || !data.has("record")) return
                    // 复制原响应，既不补写宿主字段，也不采用上一次支付的缓存金额。
                    val copy = JSONObject()
                    for (key in listOf("retcode", "isSender", "receiveStatus", "changeWording", "receiveId", "amount", "record")) {
                        copy.put(key, data.opt(key))
                    }
                    forward(context, "com.tencent.mm", JSONObject().put("kind", "wechat_red_packet").put("data", copy).toString())
                }.onFailure { log("wechat red_packet callback=${it.javaClass.simpleName}") }
            }
        })
        log("wechat red_packet hook=${clazz.name}")

    }

    private fun installAlipay(context: Context, loader: ClassLoader) {
        val message = XposedHelpers.findClass("com.alipay.mobile.rome.longlinkservice.syncmodel.SyncMessage", loader)
        XposedHelpers.findAndHookMethod(message, "getData", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                runCatching {
                    if (param.hasThrowable()) return
                    val data = param.result as? String ?: return
                    if (data.length > ConfigCatalog.AUTO_ACCOUNTING_MAX_PAYLOAD) return
                    // Upstream MessageBoxHooker: decode the sync array and dispatch each message.
                    // Do not test raw Chinese keywords: JSON may contain escaped Unicode.
                    val messages = Gson().fromJson(data, JsonArray::class.java)
                    messages.forEach { item ->
                        forward(context, "com.eg.android.AlipayGphone", JsonArray().apply { add(item) }.toString())
                    }
                }.onFailure { log("alipay callback=${it.javaClass.simpleName}") }
            }
        })
        // 参考项目兼容点：部分路径只读 toString，不调用 getData。递归保护，不改原返回值。
        val reading = ThreadLocal<Boolean>()
        install("alipay toString bridge") { XposedHelpers.findAndHookMethod(message, "toString", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (reading.get() == true || param.hasThrowable()) return
                reading.set(true)
                try { XposedHelpers.callMethod(param.thisObject, "getData") }
                catch (_: Throwable) { log("alipay getData bridge unavailable") }
                finally { reading.remove() }
            }
        }) }
        log("alipay installed version=${version(context)}")
    }

    private fun forward(context: Context, pkg: String, payload: String) {
        if (payload.length > ConfigCatalog.AUTO_ACCOUNTING_MAX_PAYLOAD) return
        executor.execute {
            try {
                val uri = Uri.parse("content://com.antgskds.calendarassistant.payment.capture")
                val resolver = context.contentResolver
                if (resolver.call(uri, "status", pkg, null)?.getBoolean("enabled") != true) return@execute
                val now = android.os.SystemClock.elapsedRealtime()
                delivered.entries.removeAll { now - it.value >= ConfigCatalog.AUTO_ACCOUNTING_REPEAT_MS }
                val hash = MessageDigest.getInstance("SHA-256").digest(payload.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it.toInt() and 255) }
                if (delivered.containsKey(hash)) return@execute
                val result = resolver.call(uri, "capture", pkg, Bundle().apply { putString("payload", payload) })
                val status = result?.getString("status") ?: "unavailable"
                if (status == "processed" || status == "unmatched") {
                    while (delivered.size >= ConfigCatalog.AUTO_ACCOUNTING_MAX_NODES) delivered.remove(delivered.keys.first())
                    delivered[hash] = now
                }
                log("$pkg capture=$status")
            } catch (e: Throwable) { log("$pkg delivery=${e.javaClass.simpleName}") }
        }
    }

    private fun version(context: Context): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("unknown")
    private fun install(name: String, action: () -> Unit) {
        runCatching { action(); log(name + " installed") }
            .onFailure {
                log(name + " unavailable=" + it.javaClass.simpleName)
                XposedBridge.log(it) // Installation errors only; callback payment bodies stay out of logs.
            }
    }
    private fun log(message: String) { XposedBridge.log("WillDoAccounting: $message") }
}
