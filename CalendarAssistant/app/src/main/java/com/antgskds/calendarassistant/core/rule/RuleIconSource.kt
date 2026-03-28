package com.antgskds.calendarassistant.core.rule

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 规则图标源定义。
 * iconSourceJson 格式：{ "capsuleIcon": "ic_stat_train" }
 * 值为 drawable 资源名，空或缺失表示使用默认图标。
 */
@Serializable
data class RuleIconSource(
    val capsuleIcon: String = ""
) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parse(jsonString: String): RuleIconSource {
            if (jsonString.isBlank()) return RuleIconSource()
            return try {
                json.decodeFromString<RuleIconSource>(jsonString)
            } catch (e: Exception) {
                RuleIconSource()
            }
        }

        fun serialize(source: RuleIconSource): String {
            return json.encodeToString(source)
        }
    }
}
