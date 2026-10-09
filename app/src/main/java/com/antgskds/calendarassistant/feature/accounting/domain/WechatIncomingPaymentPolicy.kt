package com.antgskds.calendarassistant.feature.accounting.domain

import com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import java.time.Instant
import java.time.ZoneId

/** 微信空树收款兜底；页面/事件只授权截图，金额、完成状态和交易时间由统一多模态核实。 */
class WechatIncomingPaymentPolicy {
    enum class Kind { TRANSFER, RED_PACKET }
    data class Evidence(val kind: Kind, val expectedAmount: String? = null, val receivedAtMillis: Long? = null) {
        fun promptContext(): String = when (kind) {
            Kind.TRANSFER -> """
                本次应用上下文：附件来自微信转账详情，应用在同一窗口收到本人“你已收款，资金已存入零钱”的成功事件。
                只提取本人已收到的这笔转账，输出一条 INCOME、COMPLETED、CNY 账单，events 为空。
                金额和交易对方必须以截图为准；余额、零钱通收益、背景聊天或其他交易不生成账单。
                occurredAt 优先使用截图中的收款时间；不能把转账发出时间当收款时间，也不能用当前时间补缺。
                待你收款、确认收款按钮、加载状态或不能明确看到收款完成时返回空 bills。
            """.trimIndent()
            Kind.RED_PACKET -> """
                本次应用上下文：应用刚观察到微信打开红包页到领取详情页的同次领取流程，附件是领取结果截图。
                仅明确看到本人领取金额及“已存入零钱”等到账依据时输出一条 INCOME、COMPLETED、CNY 账单，events 为空。
                只取本人实际领取金额，不能取红包总额、他人领取金额、余额，也不能把待打开、过期或已被他人领完的红包入账。
                名称没有交易对方时用“微信红包”，channel 为“微信支付”。occurredAt 只填写截图明确的实际领取时间，没有时间则留空，由应用填入本次领取结果出现的时刻。
                空白、加载、聊天页、发送方红包记录或无法确认本人到账时返回空 bills。
            """.trimIndent()
        }

        fun complete(bills: List<AccountingDraft>, issues: List<String>): AccountingDraft? {
            val bill = bills.singleOrNull() ?: return null
            if (issues.isNotEmpty() || bill.direction != "INCOME" || bill.paymentStatus != "COMPLETED" || bill.currency != "CNY") return null
            val amount = bill.amount.toBigDecimalOrNull() ?: return null
            val expected = expectedAmount?.toBigDecimalOrNull()
            if (amount.signum() != 1 || amount.stripTrailingZeros().scale() > 2 ||
                (expectedAmount != null && (expected == null || amount.compareTo(expected) != 0))) return null
            val completed = when (kind) {
                Kind.TRANSFER -> bill.copy(merchant = bill.merchant.ifBlank { "微信收款" }, channel = "微信支付")
                Kind.RED_PACKET -> {
                    val receivedAt = receivedAtMillis ?: return null
                    val zone = ZoneId.systemDefault()
                    bill.copy(merchant = bill.merchant.ifBlank { "微信红包" }, channel = "微信支付",
                        occurredAt = bill.occurredAt.ifBlank { Instant.ofEpochMilli(receivedAt).atZone(zone).toLocalDateTime().toString() },
                        zoneId = bill.zoneId.ifBlank { zone.id }, createdAt = receivedAt)
                }
            }
            return completed.takeIf { AccountingRecognitionMapper.automaticInput(it, useCurrentTimeForMissing = false) != null }
        }
    }
    data class Candidate(val visit: Long, val windowId: Int, val detectedAt: Long, val evidence: Evidence)
    private data class PendingTransfer(val windowId: Int, val amount: String, val at: Long)
    private var sequence = 0L
    private var pageClass = ""
    private var pageWindow = -1
    private var receivingAt: Long? = null
    private var receivingWindow = -1
    private var redPacketEvidence: Evidence? = null
    private var redPacketResultAt = 0L
    private var pendingTransfer: PendingTransfer? = null
    private var claimed: Candidate? = null

    fun reset() {
        sequence++
        pageClass = ""
        pageWindow = -1
        receivingAt = null
        receivingWindow = -1
        redPacketEvidence = null
        pendingTransfer = null
        claimed = null
    }

    /** 窗口类名有时晚于焦点成功事件；只在同一当前窗口、短时内关联，不跨聊天页保存证据。 */
    fun observe(packageName: String, className: String, window: Int, texts: List<String>, editable: Boolean,
        isWindowEvent: Boolean, foregroundPackage: String?, foregroundWindow: Int?, now: Long, wallTime: Long): Candidate? {
        if (foregroundPackage != null && foregroundPackage != AutomaticAccountingPolicy.WECHAT) reset()
        if (packageName != AutomaticAccountingPolicy.WECHAT || foregroundPackage != packageName || window < 0 || window != foregroundWindow) return null
        if (isWindowEvent) {
            when {
                className == RECEIVE_PAGE -> {
                    if (pageClass != className || pageWindow != window) {
                        reset()
                        receivingAt = now
                        receivingWindow = window
                        pageClass = className
                        pageWindow = window
                    }
                }
                className in RED_PACKET_DETAILS -> {
                    if (pageClass !in RED_PACKET_DETAILS || pageWindow != window) {
                        val fresh = receivingAt?.let { now - it in 0..ConfigCatalog.AUTO_ACCOUNTING_WECHAT_SESSION_MS.toLong() } == true &&
                            pageClass == RECEIVE_PAGE && receivingWindow != window
                        reset()
                        pageClass = className
                        pageWindow = window
                        if (fresh) {
                            redPacketEvidence = Evidence(Kind.RED_PACKET, receivedAtMillis = wallTime)
                            redPacketResultAt = now
                        }
                    }
                    pageClass = className
                }
                className == TRANSFER_DETAIL -> {
                    if (pageClass != className || pageWindow != window) {
                        val pending = pendingTransfer?.takeIf { it.windowId == window }
                        reset()
                        pageClass = className
                        pageWindow = window
                        pendingTransfer = pending
                    }
                }
                className.startsWith("com.tencent.mm.") && !className.startsWith("com.tencent.mm.ui.widget.dialog.") -> {
                    reset()
                    pageClass = className
                    pageWindow = window
                }
            }
        }
        if (editable || PaymentDetailPolicy.blockedTexts(texts, false)) {
            claimed = null
            pendingTransfer = null
            redPacketEvidence = null
            return null
        }
        if (pageClass == RECEIVE_PAGE && texts.any { word ->
                listOf("查看领取详情", "红包已领取", "已被领完", "已过期").any(word::contains) }) receivingAt = null
        if (pageClass in RED_PACKET_DETAILS && texts.any { word ->
                listOf("手慢了", "已被领完", "已过期").any(word::contains) }) {
            claimed = null
            redPacketEvidence = null
            return null
        }
        if (texts.any { word -> listOf("加载中", "正在加载", "请稍候").any(word::contains) }) return null
        val success = texts.any { it.trim() == "已收款" || it.trim() == "已收钱" ||
            (it.contains("你已收款") && it.contains("资金已存入零钱")) }
        if (success && (pageClass.isBlank() || pageClass == TRANSFER_DETAIL || pageWindow != window)) {
            receiptAmount(texts)?.let { amount ->
                if (claimed?.evidence?.expectedAmount != null && claimed?.evidence?.expectedAmount != amount) {
                    sequence++
                    claimed = null
                }
                pendingTransfer = PendingTransfer(window, amount, now)
            }
        }
        if (pageWindow != window || claimed != null) return null
        val pending = pendingTransfer?.takeIf { it.windowId == window && now - it.at in 0..ConfigCatalog.AUTO_ACCOUNTING_SUCCESS_EVENT_MS.toLong() }
        val evidence = when {
            pageClass == TRANSFER_DETAIL && pending != null -> Evidence(Kind.TRANSFER, expectedAmount = pending.amount)
            pageClass in RED_PACKET_DETAILS && now - redPacketResultAt in 0..ConfigCatalog.AUTO_ACCOUNTING_SUCCESS_EVENT_MS.toLong() -> redPacketEvidence
            else -> null
        } ?: return null
        return Candidate(sequence, window, pending?.at ?: redPacketResultAt, evidence).also { claimed = it }
    }

    fun hasClaimed(packageName: String, window: Int) = packageName == AutomaticAccountingPolicy.WECHAT && claimed?.windowId == window
    fun release(candidate: Candidate) { if (claimed == candidate) { claimed = null; sequence++ } }

    fun isValid(candidate: Candidate, packageName: String, window: Int, texts: List<String>, editable: Boolean, now: Long): Boolean {
        if (candidate != claimed || candidate.visit != sequence || packageName != AutomaticAccountingPolicy.WECHAT ||
            window != candidate.windowId || window != pageWindow ||
            now - candidate.detectedAt !in 0..ConfigCatalog.AUTO_ACCOUNTING_SUCCESS_EVENT_MS.toLong() ||
            editable || PaymentDetailPolicy.blockedTexts(texts, false) ||
            texts.any { word -> listOf("加载中", "正在加载", "请稍候").any(word::contains) }) return false
        if (candidate.evidence.kind == Kind.TRANSFER) {
            if (pageClass != TRANSFER_DETAIL || texts.any { it.contains("待你收款") || it.trim() == "确认收款" }) return false
            val visibleAmount = receiptAmount(texts)
            if (visibleAmount != null && visibleAmount != candidate.evidence.expectedAmount) return false
        } else if (pageClass !in RED_PACKET_DETAILS || redPacketEvidence != candidate.evidence) return false
        return true
    }

    companion object {
        private const val RECEIVE_PAGE = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNewReceiveUI"
        private val RED_PACKET_DETAILS = setOf("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNewDetailUI",
            "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyBeforeDetailUI")
        private const val TRANSFER_DETAIL = "com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI"
        private val CURRENCY_AMOUNT = Regex("(?:[¥￥]\\s*([0-9]+(?:\\.[0-9]{1,2})?)|([0-9]+(?:\\.[0-9]{1,2})?)\\s*元)")
        private val DECIMAL_AMOUNT = Regex("[+]?([0-9]+\\.[0-9]{1,2})")
        private fun receiptAmount(texts: List<String>): String? {
            val amounts = texts.flatMap { text ->
                val currency = CURRENCY_AMOUNT.findAll(text).map { match -> match.groupValues[1].ifBlank { match.groupValues[2] } }.toList()
                currency.ifEmpty { DECIMAL_AMOUNT.matchEntire(text.trim())?.let { listOf(it.groupValues[1]) }.orEmpty() }
            }.mapNotNull { it.toBigDecimalOrNull()?.takeIf { value -> value.signum() == 1 }?.stripTrailingZeros()?.toPlainString() }.distinct()
            return amounts.singleOrNull()
        }
    }
}
