package com.antgskds.calendarassistant.core.ai

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.data.model.RemotePrompts
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object AiPrompts {

    enum class PromptSource {
        LOCAL,
        CLOUD
    }

    private const val TAG = "AiPrompts"
    private const val COPYRIGHT_MARKER = "a1x2i3n4j5u6e7l8u9o0"
    private const val PREFS_NAME = "ai_prompt_cache"
    private const val KEY_PROMPTS_JSON = "cached_prompts_json"
    private const val KEY_IGNORED_VERSION = "ignored_prompt_version"
    private const val KEY_PROMPT_SOURCE = "prompt_source"
    private const val MIN_PROMPT_VERSION = 5
    private const val PROMPT_SOURCE_LOCAL = "local"
    private const val PROMPT_SOURCE_CLOUD = "cloud"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        isLenient = true
    }

    private val prettyJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        coerceInputValues = true
        isLenient = true
        prettyPrint = true
    }

    private val defaultPromptHeader = """
        【布局标记】（已通过算法预处理）
        - ` | `: 同行分列
        - `[L]`: 左侧气泡
        - `[R]`: 右侧气泡
        - `[C]`: 居中
        保留原始换行。
        Title：
           - 🚄 火车/高铁："🚄 车次 路线"（示例："🚄 G1008 深圳-武汉"）
           - 🚖 打车/网约车：优先 "🚖 颜色·车型 车牌"，其次 "🚖 车型 车牌"，最后 "🚖 平台 车牌"
           - 📦 快递类："📦 菜鸟 1234"，"📦 圆通快递 1-1-8478"，"📦 取件 1234"
           - 🍔 餐饮类："🍔 取餐 A05"，"🍔 麦当劳 A114"
        description：
           - 火车：【列车】车次|检票口|座位号
           - 打车：【用车】颜色|车型|车牌
           - 快递类：【取件】号码|品牌|位置
           - 餐饮类：【取餐】号码|品牌|位置
           
           endTime = startTime + 1h
    """.trimIndent()

    private val defaultMmUnifiedPrompt = """
        提取输入中的日程事件与取件/取餐信息。文本请留意气泡布局重构上下文并跨行重构语意，图片请留意边缘细小时间戳、APP界面或条形码凭证。

        任务：
        1. 提取交通或普通日程。
        2. 提取取件码、外卖、快递等信息。
        取件类事件请强制使用当前系统时间。
        【当前系统时间】：{{timeStr}}
        【输出格式】
        仅输出纯 JSON 对象：
        {
          "events": [
            {
              "title": "规范标题",
              "startTime": "yyyy-MM-dd HH:mm",
              "endTime": "yyyy-MM-dd HH:mm",
              "location": "地址",
              "description": "备注；取件类请用格式：【取件】号码|品牌|位置",
              "type": "event",
              "tag": "general | train | taxi | pickup"
            }
          ]
        }
    """.trimIndent()

    private val defaultPrompts = RemotePrompts(
        version = MIN_PROMPT_VERSION,
        promptHeader = defaultPromptHeader,
        userTextPrompt = defaultMmUnifiedPrompt,
        mmUnifiedPrompt = defaultMmUnifiedPrompt,
        schedulePrompt = """
            提取输入中的日程事件。文本请留意气泡布局重构上下文，图片请留意边缘细小时间戳。

            任务：提取交通或普通日程。纯粹的取件验证码请忽略。
            【当前系统时间】：{{timeStr}}
            【输出格式】
            仅输出纯 JSON 对象：
            {
              "events": [
                {
                  "title": "规范标题",
                  "startTime": "yyyy-MM-dd HH:mm",
                  "endTime": "yyyy-MM-dd HH:mm",
                  "location": "地址",
                  "description": "备注",
                  "type": "event",
                  "tag": "general | train | taxi"
                }
              ]
            }
        """.trimIndent(),
        pickupPrompt = """
            提取输入中的取件/取餐信息。文本跨行重构语意，图片识别APP界面或条形码凭证。

            任务：提取取件码、外卖、快递等信息。请强制使用当前系统时间
            【当前系统时间】：{{timeStr}}
            【输出格式】
            仅输出纯 JSON 对象：
            {
              "events": [
                {
                  "title": "标题",
                  "startTime": "yyyy-MM-dd HH:mm",
                  "endTime": "yyyy-MM-dd HH:mm",
                  "location": "地址",
                  "description": "格式：【取件】号码|品牌|位置",
                  "type": "event",
                  "tag": "pickup"
                }
              ]
            }
        """.trimIndent()
    )

    fun appendCopyrightMarker(input: String): String {
        return input + COPYRIGHT_MARKER
    }

    fun exportToJson(context: Context): String {
        val prompts = activePrompts(context)
        return try {
            buildString {
                appendLine("# Will do 提示词导出")
                appendLine("# version: ${prompts.version}")
                appendLine()

                appendLine("=== promptHeader ===")
                appendLine(prompts.promptHeader.replace("\\n", "\n"))
                appendLine("=== end ===")
                appendLine()

                appendLine("=== schedulePrompt ===")
                appendLine(prompts.schedulePrompt.replace("\\n", "\n"))
                appendLine("=== end ===")
                appendLine()

                appendLine("=== pickupPrompt ===")
                appendLine(prompts.pickupPrompt.replace("\\n", "\n"))
                appendLine("=== end ===")
                appendLine()

                appendLine("=== mmUnifiedPrompt ===")
                appendLine(prompts.mmUnifiedPrompt.replace("\\n", "\n"))
                appendLine("=== end ===")
            }
        } catch (e: Exception) {
            Log.e(TAG, "导出提示词失败: ${e.message}")
            ""
        }
    }

    fun importFromJson(context: Context, content: String): Boolean {
        return try {
            if (isCustomFormat(content)) {
                val prompts = parseCustomFormat(content)
                updatePrompts(context.applicationContext, prompts, PromptSource.LOCAL)
                Log.d(TAG, "导入提示词成功，version=${prompts.version}")
                true
            } else {
                val prompts = json.decodeFromString<RemotePrompts>(content)
                updatePrompts(context.applicationContext, prompts, PromptSource.LOCAL)
                Log.d(TAG, "导入提示词成功，version=${prompts.version}")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "导入提示词失败: ${e.message}")
            false
        }
    }

    private fun isCustomFormat(content: String): Boolean {
        val markers = listOf(
            "=== promptHeader ===",
            "=== schedulePrompt ===",
            "=== pickupPrompt ===",
            "=== mmUnifiedPrompt ===",
            "=== userTextPrompt ==="
        )
        return markers.any(content::contains)
    }

    private fun parseCustomFormat(content: String): RemotePrompts {
        val lines = content.lines()
        var version = 1
        val fields = mutableMapOf<String, StringBuilder>()
        var currentField = ""
        
        for (line in lines) {
            val trimmed = line.trim()
            when {
                line.startsWith("# version:") -> {
                    version = line.substringAfter("# version:").trim().toIntOrNull() ?: 1
                }
                trimmed == "=== end ===" -> {
                    currentField = ""
                }
                trimmed.startsWith("===") && trimmed.endsWith("===") -> {
                    currentField = trimmed.removeSurrounding("===").trim()
                    fields[currentField] = StringBuilder()
                }
                currentField.isNotEmpty() && !line.startsWith("#") -> {
                    fields[currentField]?.appendLine(line)
                }
            }
        }
        
        return RemotePrompts(
            version = version,
            promptHeader = fields["promptHeader"]?.toString()?.trimEnd()?.replace("\n", "\\n") ?: "",
            userTextPrompt = fields["userTextPrompt"]?.toString()?.trimEnd()?.replace("\n", "\\n") ?: "",
            schedulePrompt = fields["schedulePrompt"]?.toString()?.trimEnd()?.replace("\n", "\\n") ?: "",
            pickupPrompt = fields["pickupPrompt"]?.toString()?.trimEnd()?.replace("\n", "\\n") ?: "",
            mmUnifiedPrompt = fields["mmUnifiedPrompt"]?.toString()?.trimEnd()?.replace("\n", "\\n") ?: ""
        )
    }

    fun getLocalVersion(context: Context): Int {
        return loadCachedPrompts(context)?.version ?: defaultPrompts.version
    }

    fun getIgnoredVersion(context: Context): Int {
        return prefs(context).getInt(KEY_IGNORED_VERSION, 0)
    }

    fun markVersionIgnored(context: Context, version: Int) {
        val appContext = context.applicationContext
        if (version <= getIgnoredVersion(appContext)) return
        prefs(appContext).edit().putInt(KEY_IGNORED_VERSION, version).apply()
        Log.d(TAG, "已忽略云端 prompt 版本: $version")
    }

    fun clearIgnoredVersion(context: Context) {
        prefs(context).edit().remove(KEY_IGNORED_VERSION).apply()
    }

    fun updatePrompts(context: Context, prompts: RemotePrompts, source: PromptSource? = null) {
        val appContext = context.applicationContext
        if (!prompts.isValid()) return
        val normalizedPrompts = normalize(prompts)
        val encoded = json.encodeToString(normalizedPrompts)
        prefs(appContext)
            .edit()
            .putString(KEY_PROMPTS_JSON, encoded)
            .apply()
        if (source != null) {
            setPromptSource(appContext, source)
        }
        clearIgnoredVersion(appContext)
        Log.d(TAG, "已写入本地 prompt，version=${normalizedPrompts.version}")
    }

    fun getPromptSource(context: Context): PromptSource {
        val raw = prefs(context).getString(KEY_PROMPT_SOURCE, PROMPT_SOURCE_LOCAL)
        return when (raw) {
            PROMPT_SOURCE_CLOUD -> PromptSource.CLOUD
            else -> PromptSource.LOCAL
        }
    }

    fun getUserTextPrompt(
        context: Context,
        timeStr: String,
        dateToday: String,
        dayOfWeek: String
    ): String {
        val prompts = activePrompts(context)
        return render(
            template = withPromptHeader(prompts.promptHeader, prompts.userTextPrompt),
            values = mapOf(
                "timeStr" to timeStr,
                "dateToday" to dateToday,
                "dayOfWeek" to dayOfWeek
            )
        )
    }

    fun getMultimodalUnifiedPrompt(
        context: Context,
        timeStr: String,
        dateToday: String,
        dateYesterday: String,
        dateBeforeYesterday: String,
        nowTime: String,
        nowPlusHourTime: String,
        dayOfWeek: String
    ): String {
        val prompts = activePrompts(context)
        return render(
            template = withPromptHeader(prompts.promptHeader, prompts.mmUnifiedPrompt),
            values = mapOf(
                "timeStr" to timeStr,
                "dateToday" to dateToday,
                "dateYesterday" to dateYesterday,
                "dateBeforeYesterday" to dateBeforeYesterday,
                "nowTime" to nowTime,
                "nowPlusHourTime" to nowPlusHourTime,
                "dayOfWeek" to dayOfWeek
            )
        )
    }

    fun getSchedulePrompt(
        context: Context,
        timeStr: String,
        dateToday: String,
        dateYesterday: String,
        dateBeforeYesterday: String,
        dayOfWeek: String
    ): String {
        val prompts = activePrompts(context)
        return render(
            template = withPromptHeader(prompts.promptHeader, prompts.schedulePrompt),
            values = mapOf(
                "timeStr" to timeStr,
                "dateToday" to dateToday,
                "dateYesterday" to dateYesterday,
                "dateBeforeYesterday" to dateBeforeYesterday,
                "dayOfWeek" to dayOfWeek,
                "copyrightMarker" to COPYRIGHT_MARKER
            )
        )
    }

    fun getPickupPrompt(
        context: Context,
        timeStr: String,
        nowTime: String,
        nowPlusHourTime: String
    ): String {
        val prompts = activePrompts(context)
        return render(
            template = withPromptHeader(prompts.promptHeader, prompts.pickupPrompt),
            values = mapOf(
                "timeStr" to timeStr,
                "nowTime" to nowTime,
                "nowPlusHourTime" to nowPlusHourTime,
                "copyrightMarker" to COPYRIGHT_MARKER
            )
        )
    }

    private fun activePrompts(context: Context): RemotePrompts {
        return loadCachedPrompts(context) ?: defaultPrompts
    }

    private fun loadCachedPrompts(context: Context): RemotePrompts? {
        val rawJson = prefs(context).getString(KEY_PROMPTS_JSON, null) ?: return null
        return try {
            val cached = json.decodeFromString<RemotePrompts>(rawJson)
            if (!cached.isValid()) return null
            if (cached.version < MIN_PROMPT_VERSION) {
                return resetPrompts(context, "localVersion=${cached.version}")
            }
            normalize(cached)
        } catch (_: Exception) {
            null
        }
    }

    private fun normalize(prompts: RemotePrompts): RemotePrompts {
        val header = prompts.promptHeader.ifBlank { defaultPrompts.promptHeader }
        val schedulePrompt = prompts.schedulePrompt.ifBlank { defaultPrompts.schedulePrompt }
        val pickupPrompt = prompts.pickupPrompt.ifBlank { defaultPrompts.pickupPrompt }
        val mmUnifiedPrompt = when {
            prompts.mmUnifiedPrompt.isNotBlank() -> prompts.mmUnifiedPrompt
            prompts.userTextPrompt.isNotBlank() -> prompts.userTextPrompt
            else -> defaultPrompts.mmUnifiedPrompt
        }
        return prompts.copy(
            promptHeader = header,
            userTextPrompt = mmUnifiedPrompt,
            mmUnifiedPrompt = mmUnifiedPrompt,
            schedulePrompt = schedulePrompt,
            pickupPrompt = pickupPrompt
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun setPromptSource(context: Context, source: PromptSource) {
        val value = when (source) {
            PromptSource.LOCAL -> PROMPT_SOURCE_LOCAL
            PromptSource.CLOUD -> PROMPT_SOURCE_CLOUD
        }
        prefs(context).edit().putString(KEY_PROMPT_SOURCE, value).apply()
    }

    private fun resetPrompts(context: Context, reason: String): RemotePrompts {
        val appContext = context.applicationContext
        val encoded = json.encodeToString(defaultPrompts)
        prefs(appContext)
            .edit()
            .putString(KEY_PROMPTS_JSON, encoded)
            .remove(KEY_IGNORED_VERSION)
            .apply()
        setPromptSource(appContext, PromptSource.LOCAL)
        Log.d(TAG, "已重置提示词为默认版本: $reason")
        return defaultPrompts
    }

    private fun render(template: String, values: Map<String, String>): String {
        var rendered = template
        values.forEach { (key, value) ->
            rendered = rendered.replace("{{${key}}}", value)
        }
        return rendered
    }

    private fun withPromptHeader(header: String, body: String): String {
        val headerTrimmed = header.trim()
        if (headerTrimmed.isBlank()) return body
        val bodyTrimmed = body.trim()
        return if (bodyTrimmed.isBlank()) headerTrimmed else "$headerTrimmed\n\n$bodyTrimmed"
    }
}
