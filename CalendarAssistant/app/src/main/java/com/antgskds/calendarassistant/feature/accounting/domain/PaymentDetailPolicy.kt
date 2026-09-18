package com.antgskds.calendarassistant.feature.accounting.domain

import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog

/** 主线程维护页面身份；事件/树文字只决定是否截图，不从触发词直接生成账单。 */
class PaymentDetailPolicy {
    data class Candidate(val packageName: String, val windowId: Int, val visit: Long,
        val detectedAt: Long, val evidence: String, val contentFingerprint: String)

    private var sequence = 0L
    private var packageName = ""
    private var windowId = -1
    private var pageClass = ""
    private var claimed: Candidate? = null
    private var treeBlocked = false
    private var detailContext = false
    private var contentLoading = false

    fun reset() {
        sequence++
        packageName = ""
        windowId = -1
        pageClass = ""
        claimed = null
        treeBlocked = false
        detailContext = false
        contentLoading = false
    }

    fun observeForeground(pkg: String, window: Int) {
        if (pkg == packageName && window == windowId) return
        // 聊天弹窗换窗口时仍保留聊天限制，直到明确收到新应用页面的身份。
        val inheritedChatClass = pageClass.takeIf { pkg == packageName && blockedPage() }.orEmpty()
        reset()
        packageName = pkg
        windowId = window
        pageClass = inheritedChatClass
    }

    fun observeWindow(pkg: String, className: String, window: Int,
        foregroundPackage: String = pkg, foregroundWindow: Int = window) {
        if (!AutomaticAccountingPolicy.supports(pkg) || window < 0) return
        // 微信可能在小程序详情前台时补发后方 LauncherUI 的事件，不能据此覆盖当前页面。
        if (pkg != foregroundPackage || window != foregroundWindow) return
        // 窗口事件也会来自控件、输入法和弹窗；不能用它们覆盖此前明确的聊天页身份。
        if (className.startsWith("android.") || className.startsWith("androidx.") ||
            className.contains(".widget.") || !className.contains('.')) return
        if (pkg != packageName || window != windowId || className != pageClass) {
            reset()
            packageName = pkg
            windowId = window
            pageClass = className
            detailContext = knownDetailPage()
        }
    }

    fun claimEvent(pkg: String, window: Int, texts: List<String>, now: Long): Candidate? {
        // 空树事件兜底必须已知应用页面身份，避免服务中途启动时把聊天词句当详情。
        // 旧树可能还是上一页的列表；新事件携带详情信号时允许重新判断，截图前再校验当前树。
        if (pkg != packageName || window != windowId || pageClass.isBlank() || blockedPage()) return null
        if (hasDetailMarker(texts) || knownDetailPage()) detailContext = true
        val readiness = inspectReadiness(texts, false, knownDetailPage())
        if (readiness.loading) contentLoading = true
        if (!readiness.ready) return null
        contentLoading = false
        val evidence = when {
            pkg == AutomaticAccountingPolicy.WECHAT && pageClass == WECHAT_TRANSFER_DETAIL -> "known_page"
            hasDetailMarker(texts) -> "event_text"
            else -> return null
        }
        return claim(now, evidence, texts)
    }

    fun claimTree(pkg: String, window: Int, texts: List<String>, editable: Boolean, now: Long,
        evidence: String = "tree_text"): Candidate? {
        if (!AutomaticAccountingPolicy.supports(pkg) || window < 0) return null
        // 聊天中的弹窗可能分配新窗口 ID，不能因此丢掉已知的聊天页身份。
        if (pkg == packageName && blockedPage()) return null
        if (pkg != packageName || window != windowId) {
            reset()
            packageName = pkg
            windowId = window
        }
        val blocked = blockedTexts(texts, editable)
        // 只有完整前台树能确认返回列表；事件源可能只是“全部账单”导航按钮的局部节点。
        if (evidence == "tree_text") {
            if (blocked && !treeBlocked) { sequence++; claimed = null; detailContext = false; contentLoading = false }
            treeBlocked = blocked
        }
        if (blockedPage() || blocked) return null
        if (hasDetailMarker(texts) || knownDetailPage()) detailContext = true
        val readiness = inspectReadiness(texts, editable, knownDetailPage())
        if (readiness.loading) contentLoading = true
        if (!readiness.ready) return null
        contentLoading = false
        return claim(now, evidence, texts)
    }

    private fun claim(now: Long, evidence: String, texts: List<String>): Candidate? {
        if (claimed != null) return null
        return Candidate(packageName, windowId, sequence, now, evidence,
            AutomaticAccountingPolicy.fingerprint(texts.joinToString("\n"))).also { claimed = it }
    }

    fun hasClaimed(pkg: String) = pkg == packageName && claimed != null
    fun hasDetailContext(pkg: String) = pkg == packageName && detailContext

    fun isBlocked(pkg: String, window: Int, texts: List<String>, editable: Boolean): Boolean =
        window < 0 || blockedTexts(texts, editable) || (pkg == packageName && blockedPage())

    fun isValid(candidate: Candidate, pkg: String, window: Int, texts: List<String>, editable: Boolean, now: Long): Boolean =
        candidate == claimed && candidate.visit == sequence && pkg == packageName && window == windowId &&
            !isBlocked(pkg, window, texts, editable) &&
            !contentLoading && !isLoading(texts) &&
            now - candidate.detectedAt in 0..ConfigCatalog.AUTO_ACCOUNTING_DETAIL_SIGNAL_MS.toLong()

    private fun knownDetailPage() = packageName == AutomaticAccountingPolicy.WECHAT && pageClass == WECHAT_TRANSFER_DETAIL

    private fun blockedPage(): Boolean = pageClass.contains("chat", ignoreCase = true) ||
        pageClass == "com.tencent.mm.ui.LauncherUI"

    companion object {
        private const val WECHAT_TRANSFER_DETAIL = "com.tencent.mm.plugin.remittance.ui.RemittanceDetailUI"
        data class Readiness(val detail: Boolean, val amount: Boolean, val time: Boolean, val loading: Boolean, val blocked: Boolean) {
            val ready get() = detail && amount && time && !loading && !blocked
        }

        /** 单个快照必须具备关键内容，不把不同交易或不同阶段的零散字段拼成一笔。 */
        fun inspectReadiness(texts: List<String>, editable: Boolean, knownDetailPage: Boolean = false): Readiness {
            val text = texts.joinToString("\n")
            val detail = hasDetailMarker(texts) || knownDetailPage
            // 微信详情主金额没有货币符号；允许独立的带小数金额，不将长交易号当金额。
            val amount = texts.any { STANDALONE_AMOUNT.matches(it.trim()) } || CURRENCY_AMOUNT.containsMatchIn(text)
            // 支付宝已完成的收钱码账单只展示“创建时间”；不能据此放宽普通待支付订单。
            val receiptCreationTime = text.contains("账单详情") && text.contains("收钱码收款") &&
                texts.any { it.trim() == "交易成功" } && text.contains("创建时间")
            // 用户选择把收款合计按一笔收入记录；汇总首页只需统计日期，不要求逐笔交易时间。
            val summaryDate = hasReceiptSummary(texts) &&
                (text.contains("今日收款") || STATISTICS_DATE.containsMatchIn(text))
            val time = summaryDate || ((TIME_LABELS.any(text::contains) || receiptCreationTime) && TRANSACTION_TIME.containsMatchIn(text))
            return Readiness(detail, amount, time, isLoading(texts), blockedTexts(texts, editable))
        }

        private val STANDALONE_AMOUNT = Regex("[+\\-−]?[¥￥]?\\s*[0-9]+\\.[0-9]{1,2}(?:\\s*元)?")
        private val CURRENCY_AMOUNT = Regex("(?:[¥￥]\\s*[0-9]+(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?\\s*元)")
        private val TIME_LABELS = listOf("支付时间", "交易时间", "转账时间", "付款时间", "收款时间", "到账时间")
        private val TRANSACTION_TIME = Regex("(?:[0-9]{4}[年/\\-][0-9]{1,2}[月/\\-][0-9]{1,2}日?|今天|昨天)[\\sT]+[0-9]{1,2}:[0-9]{2}(?::[0-9]{2})?")
        private val STATISTICS_DATE = Regex("(?:[0-9]{4}[年/\\-][0-9]{1,2}[月/\\-][0-9]{1,2}日?|今天|今日|昨天)")
        private fun isLoading(texts: List<String>) = texts.any { text ->
            listOf("加载中", "正在加载", "请稍候").any(text::contains)
        }
        fun hasDetailMarker(texts: List<String>): Boolean = hasReceiptSummary(texts) || texts.any { text ->
            // 微信二维码收款详情使用“转账单号”，事件源中不一定带“账单详情”标题。
            listOf("账单详情", "交易详情", "交易单号", "交易订单号", "转账单号", "商户单号", "商户订单号").any(text::contains)
        }

        /** 微信小程序的 source 可能只有收款记录描述，没有页面标题；不能只靠“收款金额”放行。 */
        fun hasReceiptSummary(texts: List<String>): Boolean {
            val text = texts.joinToString("\n")
            return (text.contains("收款记录") && text.contains("今日收款") && text.contains("共计")) ||
                (text.contains("收款概览") && text.contains("收款金额") && text.contains("收款笔数"))
        }
        fun blockedTexts(texts: List<String>, editable: Boolean): Boolean {
            // 聊天和输入状态始终拦截；详情页中的列表导航按钮不能覆盖明确的详情证据。
            if (editable || texts.any { it.trim() == "发送" || it.contains("按住说话") }) return true
            return !hasDetailMarker(texts) && texts.any { text ->
                listOf("全部账单", "收支统计", "交易记录").any(text::contains)
            }
        }
    }
}
