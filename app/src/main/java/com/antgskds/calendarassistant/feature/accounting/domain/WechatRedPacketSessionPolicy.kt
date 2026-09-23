package com.antgskds.calendarassistant.feature.accounting.domain

import com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import java.time.Instant
import java.time.ZoneId

/** 仅关联现场发送顺序，不从编辑框金额或普通聊天通知生成账单。主线程调用。 */
class WechatRedPacketSessionPolicy {
    enum class EventKind { WINDOW, CLICK, TOAST, OTHER }
    sealed interface Action {
        data class Capture(val sessionId: Long, val windowId: Int) : Action
        data class Sent(val sessionId: Long, val evidence: SentEvidence) : Action
    }
    data class SentEvidence(val sentAtMillis: Long) {
        /** 这是应用已核实的发送结果，只修正本次缓存确认图的语义，不放宽普通图片。 */
        fun promptContext(): String = """
            本次应用上下文：附件是微信发红包流程中预先缓存的支付确认截图，截图之后，应用已在同一发送会话收到微信“已发送”成功提示。
            仅此请求允许使用这张发送前的确认图提取已成功发出的红包。必须清楚看到“微信红包”的支付确认标题和对应付款总金额；编辑页、加载页、聊天页或其他交易图不能作为凭证，返回空 bills。
            只输出本次红包一条 EXPENSE、COMPLETED 账单，不能提取余额、单个红包均额或背景金额。金额和币种必须来自确认图，无法看清则返回空 bills。
            名称没有交易对方时用“微信红包”，channel 为“微信支付”；occurredAt 留空，由应用填写已发送时刻。events 返回空数组。
        """.trimIndent()

        fun complete(bills: List<AccountingDraft>): AccountingDraft? {
            val bill = bills.singleOrNull() ?: return null
            if (bill.paymentStatus != "COMPLETED" || bill.direction != "EXPENSE" || bill.currency != "CNY") return null
            if (bill.amount.toBigDecimalOrNull()?.signum() != 1) return null
            val zone = ZoneId.systemDefault()
            return bill.copy(merchant = bill.merchant.ifBlank { "微信红包" }, channel = "微信支付",
                occurredAt = Instant.ofEpochMilli(sentAtMillis).atZone(zone).toLocalDateTime().toString(),
                zoneId = zone.id, createdAt = sentAtMillis)
        }
    }

    private enum class Phase { PREPARE, SUBMITTED, PAYMENT }
    private var sequence = 0L
    private var phase: Phase? = null
    private var startedAt = 0L
    private var windowId = -1
    private var cached = false
    var sessionId: Long? = null
        private set

    fun reset() { sessionId = null; phase = null; cached = false; windowId = -1 }
    fun fresh(now: Long): Boolean = sessionId != null &&
        now - startedAt in 0..ConfigCatalog.AUTO_ACCOUNTING_WECHAT_SESSION_MS.toLong()

    fun captureValid(capture: Action.Capture, foregroundPackage: String?, foregroundWindow: Int?, now: Long): Boolean =
        fresh(now) && phase == Phase.PAYMENT && sessionId == capture.sessionId &&
            windowId == capture.windowId && foregroundPackage == AutomaticAccountingPolicy.WECHAT && foregroundWindow == windowId

    fun markCaptured(capture: Action.Capture, foregroundPackage: String?, foregroundWindow: Int?, now: Long): Boolean =
        captureValid(capture, foregroundPackage, foregroundWindow, now).also { if (it) cached = true }

    fun observe(packageName: String, className: String, eventWindow: Int, texts: List<String>,
        kind: EventKind, foregroundPackage: String?, foregroundWindow: Int?, now: Long, wallTime: Long): Action? {
        if (sessionId != null && !fresh(now)) reset()
        if (foregroundPackage != null && foregroundPackage != AutomaticAccountingPolicy.WECHAT) reset()
        if (packageName != AutomaticAccountingPolicy.WECHAT || foregroundPackage != packageName) return null
        val words = texts.map(String::trim)
        // Toast 没有有效 windowId，不能沿用 UI 节点事件的窗口等值判断。
        if (kind == EventKind.TOAST) {
            if (words.any { it in setOf("发送失败", "支付失败", "已取消") }) { reset(); return null }
            if (words.none { it == "已发送" }) return null
            val sent = if (fresh(now) && phase == Phase.PAYMENT && cached)
                Action.Sent(sessionId!!, SentEvidence(wallTime)) else null
            reset() // 即使截图尚未完成也消费结束，不能晚到后拿聊天截图补账。
            return sent
        }
        // 点击回调到达时，微信可能已经把前台切到加载弹窗。提交事件仍必须来自已登记的准备窗口，
        // 只把它作为后续确认页的前置证据，绝不据此截图/记账。
        if (kind == EventKind.CLICK && phase == Phase.PREPARE && eventWindow == windowId && "塞钱进红包" in words) {
            phase = Phase.SUBMITTED
            return null
        }
        if (eventWindow < 0 || foregroundWindow != eventWindow) return null
        if (kind == EventKind.CLICK && words.any { it in setOf("取消", "关闭", "返回") }) { reset(); return null }
        if (kind == EventKind.WINDOW) {
            when {
                className == PREPARE_PAGE -> {
                    if (phase != Phase.PREPARE || windowId != eventWindow) {
                        reset(); sessionId = ++sequence; startedAt = now; phase = Phase.PREPARE; windowId = eventWindow
                    }
                }
                className == PAYMENT_PAGE -> {
                    if (phase == Phase.SUBMITTED) {
                        phase = Phase.PAYMENT; windowId = eventWindow
                        return Action.Capture(sessionId!!, windowId)
                    }
                    if (phase == Phase.PAYMENT && windowId != eventWindow) reset()
                }
                className.startsWith("com.tencent.mm.ui.widget.dialog.") -> Unit
                className.startsWith("com.tencent.mm.") -> reset()
            }
        }
        return null
    }

    companion object {
        private const val PREPARE_PAGE = "com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyNewPrepareUI"
        private const val PAYMENT_PAGE = "com.tencent.mm.framework.app.UIPageFragmentActivity"
    }
}
