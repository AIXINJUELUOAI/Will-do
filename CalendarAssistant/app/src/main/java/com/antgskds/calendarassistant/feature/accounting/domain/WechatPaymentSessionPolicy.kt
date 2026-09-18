package com.antgskds.calendarassistant.feature.accounting.domain

import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog

/** 主线程消费事件的值副本；成功文字只授权截图，交易状态和金额仍由图片识别确认。 */
class WechatPaymentSessionPolicy {
    data class Candidate(val sessionId: Long, val windowId: Int, val successAt: Long)

    private var sequence = 0L
    private var sessionId: Long? = null
    private var startedAt = 0L
    private var paymentWindowId: Int? = null
    private var claimed: Candidate? = null

    fun reset() {
        sessionId = null
        paymentWindowId = null
        claimed = null
    }

    fun observeWindow(packageName: String, className: String, windowId: Int, now: Long) {
        if (packageName != AutomaticAccountingPolicy.WECHAT) return
        when {
            className == PAYMENT_PAGE && windowId >= 0 -> {
                // 扫码、小程序等入口不一定经过好友转账页；承载页只建立候选上下文，
                // 同一窗口随后还必须发出成功文字。重复窗口事件不能延长或重开过期会话。
                if (sessionId == null || paymentWindowId != windowId) {
                    reset()
                    sessionId = ++sequence
                    startedAt = now
                    paymentWindowId = windowId
                }
            }
            // 支付中间弹窗会使用混淆类名，不依赖具体的 k2 等名字。
            className.startsWith("com.tencent.mm.ui.widget.dialog.") -> Unit
            // 聊天、历史详情、首页等明确的微信页面切换立即终止上下文。
            className.startsWith("com.tencent.mm.") -> reset()
        }
    }

    fun claimSuccess(packageName: String, windowId: Int, texts: List<String>, now: Long): Candidate? {
        if (packageName != AutomaticAccountingPolicy.WECHAT || !sessionFresh(now) || claimed != null) return null
        if (windowId < 0 || paymentWindowId != windowId) return null
        if (texts.none { it.trim() in SUCCESS_TEXTS }) return null
        return Candidate(sessionId ?: return null, windowId, now).also { claimed = it }
    }

    /** 已消费的成功信号也抑制同一会话的旧 UI 树路径，不能再请求一次模型。 */
    fun hasClaimedSuccess(): Boolean = claimed != null

    fun isValid(candidate: Candidate, foregroundPackage: String, foregroundWindow: Int, now: Long): Boolean =
        foregroundPackage == AutomaticAccountingPolicy.WECHAT && foregroundWindow == candidate.windowId &&
            sessionFresh(now) && sessionId == candidate.sessionId && paymentWindowId == candidate.windowId &&
            claimed == candidate && now - candidate.successAt in 0..ConfigCatalog.AUTO_ACCOUNTING_SUCCESS_EVENT_MS.toLong()

    private fun sessionFresh(now: Long): Boolean = sessionId != null &&
        now - startedAt in 0..ConfigCatalog.AUTO_ACCOUNTING_WECHAT_SESSION_MS.toLong()

    companion object {
        private const val PAYMENT_PAGE = "com.tencent.mm.framework.app.UIPageFragmentActivity"
        private val SUCCESS_TEXTS = setOf("支付成功", "付款成功", "转账成功")
    }
}
