/*
 * Adapted from AutoAccounting 4e707980 AdaptationUtils.kt / HookerClazz.kt.
 * Copyright (C) 2025 ankio (ankio@ankio.net).
 * See docs/third-party/autoaccounting/README.md and LICENSE.
 * Integration: host-private SharedPreferences and Will do logging replace upstream services.
 * No host restart or Tinker modification; a successful scan is used on the next normal launch.
 */
package com.antgskds.calendarassistant.platform.xposed

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.antgskds.calendarassistant.platform.xposed.upstream.LuckMoneyModel
import com.antgskds.calendarassistant.platform.xposed.upstream.dex.Dex
import com.antgskds.calendarassistant.platform.xposed.upstream.dex.result.ClazzResult

internal class WechatHookAdaptation(
    private val context: Context,
    private val loader: ClassLoader,
    private val log: (String, Throwable?) -> Unit,
) {
    private val prefs by lazy { context.getSharedPreferences("willdo_payment_adaptation", Context.MODE_PRIVATE) }
    private val rules = listOf(LuckMoneyModel.rule)
    private val gson = Gson()
    private val mapType = object : TypeToken<HashMap<String, ClazzResult>>() {}.type

    // The upstream cache is bound to the host version and complete method rules.
    private val rulesHash = rules.joinToString(",") { rule ->
        rule.name + ":" + rule.methods.joinToString("|") { it.toString() }
    }.hashCode().toString()

    private fun version(): Long = context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    fun cachedClass(): Class<*>? = try {
        if (prefs.getLong("adaptation_version", 0) != version() ||
            prefs.getString("adaptation_rules_hash", "") != rulesHash) {
            log("wechat adaptation cache_miss", null)
            null
        } else {
            val map: HashMap<String, ClazzResult> = gson.fromJson(prefs.getString("clazz", ""), mapType)
            require(map.keys == rules.map { it.name }.toSet()) { "Incomplete adaptation cache" }
            val name = requireNotNull(map[LuckMoneyModel.rule.name]).clazzName
            require(Regex(LuckMoneyModel.rule.nameRule).matches(name)) { "Invalid cached model" }
            val clazz = loader.loadClass(name)
            // A stale hot-patch class must fail locally, never fall back to broad live hooks.
            val constructor = LuckMoneyModel.rule.methods.first { it.name == "constructor" }
            clazz.getDeclaredConstructor(*constructor.parameters.map {
                if (it.type == "int") Int::class.javaPrimitiveType!! else loader.loadClass(it.type)
            }.toTypedArray())
            val callback = clazz.getDeclaredMethod("onGYNetEnd",
                Int::class.javaPrimitiveType!!, String::class.java, org.json.JSONObject::class.java)
            require(callback.returnType == Void.TYPE) { "Changed red-packet callback" }
            log("wechat adaptation cache_hit", null)
            clazz
        }
    } catch (error: Throwable) {
        invalidate()
        log("wechat adaptation cache_invalid", error)
        null
    }

    fun invalidate() {
        // Owned cache only; never delete or change WeChat data or patches.
        prefs.edit().remove("adaptation_version").remove("adaptation_rules_hash").remove("clazz").apply()
    }

    /** Run on the delivery executor, after Application.onCreate. No hooks are installed while scanning. */
    fun adaptForNextLaunch() {
        try {
            val hostVersion = version()
            log("wechat adaptation scan_start", null)
            Dex.DEBUG = false
            val classes = Dex.findClazz(context.applicationInfo.sourceDir, loader, rules)
            if (classes.keys != rules.map { it.name }.toSet()) {
                invalidate()
                log("wechat adaptation unmatched; red_packet skipped", null)
                return
            }
            check(prefs.edit()
                .putLong("adaptation_version", hostVersion)
                .putString("adaptation_rules_hash", rulesHash)
                .putString("clazz", gson.toJson(classes))
                .commit()) { "Could not persist adaptation" }
            log("wechat adaptation ready_next_launch", null)
        } catch (error: Throwable) {
            // Adaptation failure affects this optional hook, not the host or other payment sources.
            log("wechat adaptation failed; red_packet skipped", error)
        }
    }
}
