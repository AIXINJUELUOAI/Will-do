package com.antgskds.calendarassistant.platform.xposed

import android.app.Application
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.webkit.ValueCallback
import dalvik.system.DexFile
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

/** 参考 AutoAccounting 的数据库、详情桥接、红包回调及同步消息入口；独立实现，只观察原始数据。 */
class PaymentCaptureHook : IXposedHookLoadPackage {
    private val executor by lazy {
        ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS,
            ArrayBlockingQueue<Runnable>(ConfigCatalog.AUTO_ACCOUNTING_QUEUE_SIZE),
            { task -> Thread(task, "WillDo-payment").apply { isDaemon = true } },
            ThreadPoolExecutor.DiscardPolicy())
    }
    private val delivered = LinkedHashMap<String, Long>()
    private var installed = false
    private val inspectedClasses = java.util.Collections.synchronizedSet(HashSet<Class<*>>())

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val pkg = lpparam.packageName
        if (pkg != "com.tencent.mm" && pkg != "com.eg.android.AlipayGphone") return
        val mainProcess = lpparam.processName == pkg
        val wechatWebProcess = pkg == "com.tencent.mm" && lpparam.processName in setOf("$pkg:tools", "$pkg:toolsmp")
        if (!mainProcess && !wechatWebProcess) return
        XposedHelpers.findAndHookMethod(Application::class.java, "attach", Context::class.java, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (installed) return
                installed = true
                val context = param.args[0] as? Context ?: return
                val loader = context.classLoader
                runCatching {
                    when {
                        wechatWebProcess -> installWechatDetail(context, loader)
                        pkg == "com.tencent.mm" -> installWechat(context, loader)
                        else -> installAlipay(context, loader)
                    }
                }.onFailure { log("$pkg unavailable=${it.javaClass.simpleName}") }
            }
        })
    }

    private fun installWechat(context: Context, loader: ClassLoader) {
        // 旧版 WCDB 与新版 compat 可同时存在；一个入口失败不能挡住其他入口。
        for (name in listOf("com.tencent.wcdb.database.SQLiteDatabase", "com.tencent.wcdb.compat.SQLiteDatabase")) {
            install("wechat database=$name") {
                installWechatDatabase(context, XposedHelpers.findClass(name, loader))
            }
        }
        installWechatDetail(context, loader)
        install("wechat red_packet class observer") {
            // 混淆类名不固定；只检查红包模型包及公开回调签名，新增 split/dex 加载也可接入。
            val inspecting = ThreadLocal<Boolean>()
            XposedBridge.hookAllMethods(ClassLoader::class.java, "loadClass", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val name = param.args.firstOrNull() as? String ?: return
                    if (!name.startsWith("com.tencent.mm.plugin.luckymoney.model.") || inspecting.get() == true) return
                    val clazz = param.result as? Class<*> ?: return
                    inspecting.set(true)
                    try { installRedPacketClass(context, clazz) } finally { inspecting.remove() }
                }
            })
        }
        // 已加载的模型未必再次经过 loadClass；从现有 dex 取候选名，不新开 dex 或添加依赖。
        executor.execute {
            runCatching {
                var current: ClassLoader? = loader
                var count = 0
                while (current != null) {
                    val pathList = runCatching { XposedHelpers.getObjectField(current, "pathList") }.getOrNull()
                    val elements = pathList?.let { XposedHelpers.getObjectField(it, "dexElements") as? Array<*> }.orEmpty()
                    for (element in elements) {
                        val dex = element?.let { XposedHelpers.getObjectField(it, "dexFile") as? DexFile } ?: continue
                        val entries = dex.entries()
                        while (entries.hasMoreElements()) {
                            val name = entries.nextElement()
                            if (!name.startsWith("com.tencent.mm.plugin.luckymoney.model.")) continue
                            if (++count > ConfigCatalog.AUTO_ACCOUNTING_HOOK_MAX_CLASSES) return@execute
                            runCatching { installRedPacketClass(context, Class.forName(name, false, loader)) }
                        }
                    }
                    current = current.parent
                }
                log("wechat red_packet scan completed candidates=$count")
            }.onFailure { log("wechat red_packet scan unavailable=${it.javaClass.simpleName}") }
        }
        log("wechat installed version=${version(context)}")
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
        fun callback(valuesIndex: Int, update: Boolean) = object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                runCatching {
                    if (param.hasThrowable()) return
                    val result = (param.result as? Number)?.toLong() ?: return
                    if (result < 0 || (update && result == 0L)) return
                    val table = param.args[0] as? String ?: return
                    if (table != "message" && table != "AppMessage") return
                    val values = param.args[valuesIndex] as? ContentValues ?: return
                    val type = values.getAsInteger("type") ?: return
                    // 部分字段更新不回查数据库，只有本次写入已包含完整支付信息才处理。
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
                ContentValues::class.java, Int::class.javaPrimitiveType, callback(2, false))
        }
        install("wechat ${database.name} update") {
            XposedHelpers.findAndHookMethod(database, "updateWithOnConflict", String::class.java, ContentValues::class.java,
                String::class.java, Array<String>::class.java, Int::class.javaPrimitiveType, callback(1, true))
        }
    }

    private fun installRedPacketClass(context: Context, clazz: Class<*>) {
        if (inspectedClasses.size >= ConfigCatalog.AUTO_ACCOUNTING_HOOK_MAX_CLASSES || !inspectedClasses.add(clazz)) return
        runCatching {
            val method = clazz.declaredMethods.firstOrNull { it.name == "onGYNetEnd" &&
                it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType, String::class.java, JSONObject::class.java))
            } ?: return
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
        }.onFailure { log("wechat red_packet hook unavailable=${it.javaClass.simpleName}") }
    }

    private fun installAlipay(context: Context, loader: ClassLoader) {
        val message = XposedHelpers.findClass("com.alipay.mobile.rome.longlinkservice.syncmodel.SyncMessage", loader)
        XposedHelpers.findAndHookMethod(message, "getData", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                runCatching {
                    if (param.hasThrowable()) return
                    val data = param.result as? String ?: return
                    if (data.length > ConfigCatalog.AUTO_ACCOUNTING_MAX_PAYLOAD) return
                    if (listOf("支付", "付款", "收款", "到账", "转账", "交易", "退款").none(data::contains)) return
                    forward(context, "com.eg.android.AlipayGphone", data)
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
        runCatching { action(); log("$name installed") }
            .onFailure { log("$name unavailable=${it.javaClass.simpleName}") }
    }
    private fun log(message: String) { XposedBridge.log("WillDoAccounting: $message") }
}
