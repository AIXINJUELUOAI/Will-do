/*
 * Adapted from airline233/WakeUpDecoder, main.py and wakeup_share_sim.py.
 * Upstream revision: 577161c9ea8c34dddbcecd6ea9a702687c4ac8c4
 * https://github.com/airline233/WakeUpDecoder
 * Licensed under the Apache License, Version 2.0; see assets/licenses/WakeUpDecoder-*.
 * Modified for Will do on 2026-10-06: Kotlin/Ktor transport, explicit response
 * validation and safe errors; only fetches content for the existing import preview.
 */
package com.antgskds.calendarassistant.feature.backup.courseimport.external.wakeup

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.withCharset
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

internal class WakeUpShareClient(private val httpClient: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchShareData(
        code: String,
        rand10: String = WakeUpShareProtocol.generateRand10()
    ): String {
        val authentication = post("/pluto/app/antispam", WakeUpShareProtocol.buildAntispamBody(rand10))
        val signB = encryptedData(authentication)
            ?: (authentication["result"] as? JsonObject)?.get("data").stringValue()
            ?: error("WakeUp 认证失败，请尝试从文件导入课表")
        val token = try {
            WakeUpShareProtocol.tokenFromSignB(signB, rand10)
        } catch (_: IllegalArgumentException) {
            error("WakeUp 认证响应无效，请尝试从文件导入课表")
        } catch (_: IllegalStateException) {
            error("WakeUp 认证响应无效，请尝试从文件导入课表")
        }
        val request = WakeUpShareProtocol.buildShareRequest(code, token)
        val response = post("/share_schedule/getv2", request.body)
        val encrypted = encryptedData(response)
            ?: error("WakeUp 返回的课表数据为空，请尝试从文件导入课表")
        val decoded = try {
            parseObject(WakeUpShareProtocol.decryptShareData(encrypted, request.rc4Key))
        } catch (_: IllegalArgumentException) {
            error("WakeUp 课表解密失败，请尝试从文件导入课表")
        }
        checkStatus(decoded)
        return decoded["shareData"].stringValue()
            ?: error("WakeUp 分享数据格式不完整，请尝试从文件导入课表")
    }

    private suspend fun post(path: String, body: String): JsonObject {
        val response = httpClient.post("https://api.wakeup.fun$path") {
            contentType(ContentType.Application.FormUrlEncoded.withCharset(Charsets.UTF_8))
            header("na__zyb_source__", "wakeup")
            header("zyb-cuid", WakeUpShareProtocol.cuid)
            header("zyb-adid", WakeUpShareProtocol.adid)
            setBody(body)
        }
        if (!response.status.isSuccess()) {
            error("WakeUp 请求失败：HTTP ${response.status.value}")
        }
        return parseObject(response.bodyAsText()).also(::checkStatus)
    }

    private fun parseObject(body: String): JsonObject = try {
        json.parseToJsonElement(body) as? JsonObject
            ?: error("WakeUp 返回的数据格式无效，请尝试从文件导入课表")
    } catch (_: SerializationException) {
        error("WakeUp 返回的数据格式无效，请尝试从文件导入课表")
    }

    private fun checkStatus(root: JsonObject) {
        val errNo = (root["errNo"] as? JsonPrimitive)?.intOrNull
        val status = (root["status"] as? JsonPrimitive)?.intOrNull
        val failed = if (errNo != null) errNo != 0 else status != null && status != 1
        if (!failed) return
        val message = listOf("errMsg", "message", "msg", "errorMsg")
            .firstNotNullOfOrNull { root[it].stringValue() }.orEmpty()
        if (message.contains("版本") && message.contains("升级")) {
            error("WakeUp 接口已更新，请尝试从文件导入课表")
        }
        error(message.ifBlank { "WakeUp 请求未成功，请检查分享口令或尝试从文件导入课表" })
    }

    private fun encryptedData(root: JsonObject): String? =
        root["data"].stringValue() ?: (root["data"] as? JsonObject)?.get("data").stringValue()

    private fun kotlinx.serialization.json.JsonElement?.stringValue(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
}
