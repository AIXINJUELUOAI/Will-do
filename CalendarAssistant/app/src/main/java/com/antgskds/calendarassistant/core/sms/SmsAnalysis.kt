/*
 * 短信取件码解析器
 *
 * 正则规则参考自 parcel 项目 (https://github.com/shareven/parcel)
 * 原项目基于 MIT 许可证开源，版权所有 (c) 2025 shareven
 *
 * MIT License:
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
 */

package com.antgskds.calendarassistant.core.sms

import com.antgskds.calendarassistant.data.model.CalendarEventData
import com.antgskds.calendarassistant.data.model.EventTags
import com.antgskds.calendarassistant.data.model.EventType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern

/**
 * 短信取件码解析器
 *
 * 正则匹配后直接返回 CalendarEventData，与 AI 识屏输出格式完全一致。
 * 下游（SmsReceiver、LaboratoryPage 测试）直接复用现有的入库管线。
 */
object SmsAnalysis {

    // ==================== 正则规则 ====================

    private val codePattern: Pattern = Pattern.compile(
        """(?i)(取件码为|提货号为|取货码为|提货码为|取件码（|提货号（|取货码（|提货码（|取件码『|提货号『|取货码『|提货码『|取件码【|提货号【|取货码【|提货码【|取件码\(|提货号\(|取货码\(|提货码\(|取件码\[|提货号\[|取货码\[|提货码\[|取件码|提货号|取货码|提货码|凭|快递|京东|天猫|中通|顺丰|韵达|德邦|菜鸟|拼多多|EMS|闪送|美团|饿了么|盒马|叮咚买菜|UU跑腿|签收码|签收编号|操作码|提货编码|收货编码|签收编码|取件編號|提貨號碼|運單碼|快遞碼|快件碼|包裹碼|貨品碼)\s*[A-Za-z0-9\s-]{2,}(?:[，,、][A-Za-z0-9\s-]{2,})*"""
    )

    private val lockerPattern: Pattern = Pattern.compile(
        """(?i)([0-9]+)号(?:柜|快递柜|丰巢柜|蜂巢柜|熊猫柜|兔喜快递柜)"""
    )

    private val addressPattern: Pattern = Pattern.compile(
        """(?i)(地址|收货地址|送货地址|位于|放至|已到达|到达|已到|送达|已放入|已存放至|已存放|放入)[\s\S]*?([\w\s-]+?(?:门牌|驿站|快递点|门面|柜|,|，|。|$))"""
    )

    // ==================== 平台识别 ====================

    private val foodKeywords = setOf(
        "美团", "饿了么", "盒马", "叮咚买菜", "肯德基", "KFC",
        "麦当劳", "星巴克", "瑞幸", "蜜雪冰城", "喜茶", "奈雪"
    )

    private val companyKeywords = mapOf(
        "顺丰" to "顺丰速运", "sf" to "顺丰速运",
        "中通" to "中通快递", "zt" to "中通快递",
        "圆通" to "圆通速递", "yt" to "圆通速递",
        "韵达" to "韵达快递", "yd" to "韵达快递",
        "申通" to "申通快递", "st" to "申通快递",
        "极兔" to "极兔速递", "jt" to "极兔速递",
        "邮政" to "中国邮政", "ems" to "EMS",
        "京东" to "京东快递", "jd" to "京东快递",
        "德邦" to "德邦快递", "dp" to "德邦快递",
        "菜鸟" to "菜鸟驿站",
        "丰巢" to "丰巢快递柜",
        "天猫" to "天猫超市",
        "拼多多" to "拼多多",
        "闪送" to "闪送",
        "UU跑腿" to "UU跑腿"
    )

    val defaultIgnoreKeywords = setOf(
        "验证码", "还款", "消费", "余额", "转账", "信用卡",
        "贷款", "还款日", "账单", "积分", "理财产品"
    )

    // ==================== 公开方法 ====================

    /**
     * 解析短信，返回与 AI 识屏格式一致的 CalendarEventData
     * @return CalendarEventData 或 null（无法识别时）
     */
    fun parse(
        sender: String,
        body: String,
        ignoreKeywords: Set<String> = emptySet()
    ): CalendarEventData? {
        // 检查忽略关键词
        val allIgnore = defaultIgnoreKeywords + ignoreKeywords
        for (keyword in allIgnore) {
            if (body.contains(keyword, ignoreCase = true)) return null
        }

        // 提取取件码
        val code = extractPickupCode(body) ?: return null
        val location = extractLocation(body)
        val companyName = resolveCompany(body)
        val platform = resolvePlatform(body)
        val isFood = isFoodPickup(body)
        val tag = if (isFood) EventTags.FOOD else EventTags.PICKUP

        // 构建 title：与 AI 识屏格式对齐 "📦 平台 取件码"
        val emojiPrefix = if (isFood) "🍔" else "📦"
        val platformPart = platform.ifBlank { companyName }.ifBlank { if (isFood) "取餐" else "取件" }
        val title = "$emojiPrefix $platformPart $code"

        // 构建 description：微格式 【取件】code|company|location
        val header = if (isFood) "【取餐】" else "【取件】"
        val locationPart = location.ifBlank { platform }
        val description = "$header$code|$companyName|$locationPart"

        // 时间：使用当前系统时间，与 AI 取件识别一致
        val now = LocalDateTime.now()
        val dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        return CalendarEventData(
            title = title,
            startTime = now.format(dtf),
            endTime = now.plusHours(1).format(dtf),
            location = locationPart,
            description = description,
            type = EventType.EVENT,
            tag = tag
        )
    }

    // ==================== 内部方法 ====================

    private fun extractPickupCode(body: String): String? {
        val matcher = codePattern.matcher(body)
        if (matcher.find()) {
            val match = matcher.group(0) ?: return null
            val codes = match.split(Regex("[，,、]"))
            return codes.joinToString(", ") { code ->
                code.trim()
                    .replace(Regex("(?i)(取件码为|提货号为|取货码为|提货码为|取件码|提货号|取货码|提货码|凭|快递|签收码|签收编号|操作码|提货编码|收货编码|签收编码|取件編號|提貨號碼|運單碼|快遞碼|快件碼|包裹碼|貨品碼|[（）\\[\\]『』【】])"), "")
                    .trim()
                    .replace(Regex("[^A-Za-z0-9\\-, ]"), "")
                    .trim()
            }.ifBlank { null }
        }
        return null
    }

    private fun extractLocation(body: String): String {
        val lockerMatcher = lockerPattern.matcher(body)
        if (lockerMatcher.find()) {
            val lockerAddr = lockerMatcher.group() ?: ""
            if (lockerAddr.isNotBlank()) return lockerAddr.replace(Regex("[,，。]"), "")
        }
        val addressMatcher = addressPattern.matcher(body)
        var longestAddress = ""
        while (addressMatcher.find()) {
            val current = addressMatcher.group(2)?.toString() ?: ""
            if (current.length > longestAddress.length) {
                longestAddress = current
            }
        }
        return longestAddress
            .replace(Regex("[,，。]"), "")
            .replace("取件", "")
            .trim()
    }

    private fun resolveCompany(body: String): String {
        for ((keyword, name) in companyKeywords) {
            if (body.contains(keyword, ignoreCase = true)) return name
        }
        return ""
    }

    private fun resolvePlatform(body: String): String {
        return when {
            body.contains("菜鸟", ignoreCase = true) -> "菜鸟驿站"
            body.contains("丰巢", ignoreCase = true) -> "丰巢快递柜"
            body.contains("京东", ignoreCase = true) -> "京东"
            body.contains("美团", ignoreCase = true) -> "美团"
            body.contains("饿了么", ignoreCase = true) -> "饿了么"
            else -> ""
        }
    }

    private fun isFoodPickup(body: String): Boolean {
        return foodKeywords.any { body.contains(it, ignoreCase = true) }
    }
}
