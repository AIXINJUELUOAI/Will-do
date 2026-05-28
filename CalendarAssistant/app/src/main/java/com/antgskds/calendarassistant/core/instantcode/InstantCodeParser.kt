package com.antgskds.calendarassistant.core.instantcode

import com.antgskds.calendarassistant.core.model.RecognitionDraft
import java.time.LocalDateTime
import java.util.regex.Pattern

object InstantCodeParser {
    fun parseSms(
        sender: String,
        body: String,
        ignoreKeywords: Set<String> = emptySet()
    ): RecognitionDraft? {
        val candidate = parseSmsCandidate(body, ignoreKeywords) ?: return null
        return buildDraft(candidate)
    }

    fun parseClipboard(
        text: String,
        mode: InstantCodeParseMode
    ): InstantCodeCandidate? {
        if (text.isBlank() || shouldIgnore(text, emptySet())) return null
        hasNonCodeLabelNearCode(text)?.let { return null }

        findCandidate(text, InstantCodePatterns.directCodePatterns)?.let { return it }
        findCandidate(text, InstantCodePatterns.credentialCodePatterns)?.let { return it }
        if (mode == InstantCodeParseMode.CLIPBOARD_CONFIRM) {
            findCandidate(text, InstantCodePatterns.relaxedCodePatterns)?.let { return it }
        }
        return null
    }

    fun toDraft(candidate: InstantCodeCandidate): RecognitionDraft = buildDraft(candidate)

    private fun parseSmsCandidate(body: String, ignoreKeywords: Set<String>): InstantCodeCandidate? {
        if (shouldIgnore(body, ignoreKeywords)) return null

        val code = extractSmsPickupCode(body) ?: return null
        val location = extractLocation(body)
        val companyName = resolveCompany(body)
        val platform = resolvePlatform(body)
        val type = if (isFoodPickup(body)) InstantCodeType.FOOD else InstantCodeType.PICKUP
        val locationPart = location.ifBlank { platform }
        return InstantCodeCandidate(
            type = type,
            code = code,
            company = companyName,
            location = locationPart,
            sourceText = body
        )
    }

    private fun findCandidate(text: String, patterns: List<Pair<InstantCodeType, Pattern>>): InstantCodeCandidate? {
        for ((type, pattern) in patterns) {
            val matcher = pattern.matcher(text)
            if (!matcher.find()) continue
            val code = matcher.group(matcher.groupCount())?.let(::normalizeCandidateCode) ?: continue
            if (!isValidCode(code)) continue
            return InstantCodeCandidate(
                type = type,
                code = code,
                company = resolveCompany(text),
                location = extractLocation(text),
                sourceText = text
            )
        }
        return null
    }

    private fun shouldIgnore(text: String, extraIgnoreKeywords: Set<String>): Boolean {
        val allIgnore = InstantCodePatterns.ignoreKeywords + extraIgnoreKeywords
        return allIgnore.any { text.contains(it, ignoreCase = true) }
    }

    private fun extractSmsPickupCode(body: String): String? {
        val matcher = InstantCodePatterns.smsCodePattern.matcher(body)
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

        val hasPickupContext = Regex("取件|提货|取货|快递|包裹|驿站|货架").containsMatchIn(body)
        if (hasPickupContext) {
            val strict = InstantCodePatterns.strictPickupCodePattern.matcher(body)
            if (strict.find()) {
                val code = strict.group(2)?.trim()?.trimStart('*')?.let(::normalizeCandidateCode)
                if (!code.isNullOrBlank() && isValidCode(code)) return code
            }

            val quick = InstantCodePatterns.quickCodePattern.matcher(body)
            if (quick.find()) {
                val code = quick.group(2)?.trim()?.trimStart('*')?.let(::normalizeCandidateCode)
                if (!code.isNullOrBlank() && isValidCode(code)) return code
            }
        }

        return null
    }

    private fun extractLocation(body: String): String {
        val lockerMatcher = InstantCodePatterns.lockerPattern.matcher(body)
        if (lockerMatcher.find()) {
            val lockerAddr = lockerMatcher.group() ?: ""
            if (lockerAddr.isNotBlank()) return lockerAddr.replace(Regex("[,，。]"), "")
        }
        val addressMatcher = InstantCodePatterns.addressPattern.matcher(body)
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
        for ((keyword, name) in InstantCodePatterns.companyKeywords) {
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
        return InstantCodePatterns.foodKeywords.any { body.contains(it, ignoreCase = true) }
    }

    private fun normalizeCandidateCode(raw: String): String {
        return raw
            .trim()
            .trimStart('*')
            .replace(Regex("\\s+"), "")
            .trim(':', '：', ',', '，', '。', ';', '；', ' ')
    }

    private fun isValidCode(code: String): Boolean {
        val alnum = code.filter { it.isLetterOrDigit() }
        if (alnum.length !in 3..16) return false
        if (alnum.all { it.isDigit() } && alnum.length > 10) return false
        return code.any { it.isLetterOrDigit() }
    }

    private fun hasNonCodeLabelNearCode(text: String): String? {
        for (label in InstantCodePatterns.nonCodeLabels) {
            val pattern = Regex("$label\\s*(?:为|是|:|：|=)?\\s*[A-Za-z0-9-]{3,24}", RegexOption.IGNORE_CASE)
            if (pattern.containsMatchIn(text)) return label
        }
        return null
    }

    private fun buildDraft(candidate: InstantCodeCandidate): RecognitionDraft {
        val platformPart = candidate.location.ifBlank { candidate.company }.ifBlank { candidate.type.titleName }
        val title = "${candidate.type.emoji} $platformPart ${candidate.code}"
        val description = "【${candidate.type.header}】${candidate.code}|${candidate.company}|${candidate.location}"
        val now = LocalDateTime.now()
        val zone = java.time.ZoneId.systemDefault()
        val startSec = now.atZone(zone).toEpochSecond()
        val endSec = now.plusHours(1).atZone(zone).toEpochSecond()
        return RecognitionDraft(
            title = title,
            startTS = startSec,
            endTS = endSec,
            location = candidate.location,
            description = description,
            tag = candidate.type.tag
        )
    }
}
