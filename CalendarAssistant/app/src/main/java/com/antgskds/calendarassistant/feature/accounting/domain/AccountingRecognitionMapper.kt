package com.antgskds.calendarassistant.feature.accounting.domain

import com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft
import com.antgskds.calendarassistant.feature.accounting.data.AccountingEntry
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import java.security.MessageDigest
import java.text.Normalizer
import java.time.ZoneId
import java.util.Locale

/** 复用文件导入的交易身份；不以相似内容直接删除可能真实存在的另一笔交易。 */
object AccountingRecognitionMapper {
    const val POSSIBLE_DUPLICATE_NOTE = "疑似重复，暂不入库"

    fun isPossibleDuplicate(draft: AccountingDraft): Boolean =
        POSSIBLE_DUPLICATE_NOTE in draft.note.split(" · ")

    /** 快捷确认只放行疑似重复，不补齐缺失时间或跳过交易校验。 */
    fun possibleDuplicateInput(draft: AccountingDraft): AccountingEntryInput? {
        if (!isPossibleDuplicate(draft)) return null
        return automaticInput(draft, useCurrentTimeForMissing = false)?.copy(
            allowPossibleDuplicate = true,
            note = draft.note.split(" · ").filter { it != POSSIBLE_DUPLICATE_NOTE }.joinToString(" · "),
        )
    }

    /** 只有无时间的文字输入使用本次识别时间；非空但错误的时间不得悄悄改为现在。 */
    fun automaticInput(draft: AccountingDraft, useCurrentTimeForMissing: Boolean): AccountingEntryInput? = runCatching {
        require(draft.paymentStatus in setOf("COMPLETED", "REFUNDED"))
        require(draft.currency == "CNY") // 外币仍留待核对，不能混入人民币统计。
        val zone = draft.zoneId.takeIf(String::isNotBlank)?.let(ZoneId::of) ?: ZoneId.systemDefault()
        val time = if (draft.occurredAt.isBlank()) {
            require(useCurrentTimeForMissing)
            java.time.Instant.ofEpochMilli(draft.createdAt).atZone(zone).toLocalDateTime()
        } else java.time.LocalDateTime.parse(draft.occurredAt.replace(' ', 'T'))
        AccountingEntryInput(amount = draft.amount, direction = draft.direction, merchant = draft.merchant,
            category = draft.category, note = draft.note, date = time.toLocalDate(), time = time.toLocalTime(),
            currency = draft.currency, channel = draft.channel, transactionId = draft.transactionId, paymentConfirmed = true,
            transactionIdType = draft.transactionIdType)
    }.getOrNull()

    fun transactionKey(channel: String, transactionId: String, direction: String, transactionIdType: String = "PAYMENT", merchant: String = ""): String? {
        val normalizedChannel = normalizeChannel(channel)
        val transaction = usableTransactionId(transactionId) ?: return null
        if (normalizedChannel.isBlank() || transactionIdType !in setOf("PAYMENT", "MERCHANT_ORDER")) return null
        // 支付交易号保留文件导入的旧身份；商户订单号还需限定商户，避免不同商家编号碰撞。
        val merchantScope = normalizeMerchant(merchant)
        if (transactionIdType == "MERCHANT_ORDER" && merchantScope.isBlank()) return null
        val identity = if (transactionIdType == "PAYMENT") "$normalizedChannel|$transaction|$direction"
            else "merchant-order|$normalizedChannel|$merchantScope|$transaction|$direction"
        return MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    fun build(draft: AccountingDraft, input: AccountingEntryInput, now: Long): AccountingEntry {
        require(input.id == null) { "识别草稿不能覆盖已有账单" }
        require(input.paymentConfirmed) { "请核对交易已完成；未支付订单不能入账" }
        require(draft.paymentStatus != "REFUNDED" || input.direction == "INCOME") { "到账退款应作为入账，请核对是否为独立退款记录" }
        val currency = input.currency.trim().uppercase()
        require(currency.matches(Regex("[A-Z]{3}"))) { "请填写三位币种代码，如 CNY" }
        val zone = draft.zoneId.takeIf(String::isNotBlank)?.let(ZoneId::of) ?: ZoneId.systemDefault()
        val entry = AccountingEntryEditor.build(input, null, now)
        val channel = normalizeChannel(input.channel)
        val transaction = input.transactionId.trim()
        val transactionType = input.transactionIdType.takeIf { it in setOf("PAYMENT", "MERCHANT_ORDER") } ?: "UNKNOWN"
        return entry.copy(id = draft.id, currency = currency, source = "RECOGNITION", channel = channel,
            transactionId = transaction, ruleId = "recognition-confirmed",
            status = if (currency == "CNY") "CONFIRMED" else "PENDING",
            occurredAt = input.date.atTime(input.time).atZone(zone).toInstant().toEpochMilli(), zoneId = zone.id,
            transactionIdType = transactionType, sourceImagePath = draft.sourceImagePath,
            dedupKey = transactionKey(channel, transaction, input.direction, transactionType, input.merchant))
    }

    private fun normalizeChannel(channel: String): String = when (val value = channel.trim()) {
        "微信", "WeChat", "WECHAT" -> "微信支付"
        "Alipay", "ALIPAY" -> "支付宝"
        else -> value
    }

    /** 同金额同一分钟独立拦截；仅时间接近才要求名称相似，可比交易号明确不同仍放行。 */
    fun possibleDuplicate(a: AccountingEntry, b: AccountingEntry): Boolean {
        if (a.amountMinor != b.amountMinor || a.currency != b.currency || a.direction != b.direction) return false
        if (b.occurredAt !in duplicateTimeRange(a.occurredAt)) return false
        val precision = ConfigCatalog.ACCOUNTING_DUPLICATE_TIME_PRECISION_MS.toLong()
        val sameDisplayedTime = Math.floorDiv(a.occurredAt, precision) == Math.floorDiv(b.occurredAt, precision)
        if (!sameDisplayedTime && !similarMerchant(a.merchant, b.merchant)) return false
        val aId = usableTransactionId(a.transactionId)
        val bId = usableTransactionId(b.transactionId)
        val channel = normalizeChannel(a.channel)
        val comparable = channel.isNotBlank() && channel == normalizeChannel(b.channel) &&
            a.transactionIdType in setOf("PAYMENT", "MERCHANT_ORDER") && a.transactionIdType == b.transactionIdType
        // 类型不明、脱敏号码、空渠道均不能作为“确定为不同交易”的依据。
        return !(comparable && aId != null && bId != null && aId != bId)
    }

    fun duplicateTimeRange(time: Long): LongRange {
        val window = ConfigCatalog.ACCOUNTING_DUPLICATE_WINDOW_MS.toLong()
        val start = if (time < Long.MIN_VALUE + window) Long.MIN_VALUE else time - window
        val end = if (time > Long.MAX_VALUE - window) Long.MAX_VALUE else time + window
        return start..end
    }

    private fun usableTransactionId(value: String): String? = value.trim().takeIf {
        it.isNotBlank() && it !in setOf("/", "-", "未知", "无") &&
            !it.contains('*') && !it.contains('＊') && !it.contains('…') && !it.contains("...")
    }

    private fun normalizeMerchant(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).filter(Char::isLetterOrDigit)

    private fun similarMerchant(first: String, second: String): Boolean {
        val a = normalizeMerchant(first)
        val b = normalizeMerchant(second)
        val unknown = setOf("", "未知", "商户", "商家", "交易对方", "未提供交易对方")
        if (a in unknown || b in unknown) return false
        if (a == b) return true
        val shorter = minOf(a.length, b.length)
        val longer = maxOf(a.length, b.length)
        if (shorter >= ConfigCatalog.ACCOUNTING_NAME_CONTAINMENT_MIN_LENGTH && (a.contains(b) || b.contains(a))) return true
        if (shorter < ConfigCatalog.ACCOUNTING_NAME_FUZZY_MIN_LENGTH || longer > ConfigCatalog.ACCOUNTING_NAME_FUZZY_MAX_LENGTH) return false
        val allowed = longer * (100 - ConfigCatalog.ACCOUNTING_NAME_SIMILARITY_PERCENT) / 100
        if (longer - shorter > allowed) return false
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) {
                current[j + 1] = minOf(current[j] + 1, previous[j + 1] + 1,
                    previous[j] + if (a[i] == b[j]) 0 else 1)
            }
            previous = current
        }
        return previous[b.length] <= allowed
    }
}

data class AccountingConfirmationResult(val entry: AccountingEntry?, val duplicate: Boolean)
