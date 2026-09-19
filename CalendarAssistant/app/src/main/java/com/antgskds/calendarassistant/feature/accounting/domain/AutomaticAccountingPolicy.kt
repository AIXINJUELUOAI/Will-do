package com.antgskds.calendarassistant.feature.accounting.domain

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft
import com.antgskds.calendarassistant.shared.management.catalog.ConfigCatalog
import java.security.MessageDigest

/** 只决定是否允许触发；使用完整交易文本指纹，保留金额，避免吞掉下一笔付款。 */
class AutomaticAccountingPolicy {
    private val attempts = LinkedHashMap<String, Long>()
    private var lastAttempt: Long? = null

    enum class Reservation { ACCEPTED, REPEATED_PAGE, MIN_INTERVAL }

    fun reserve(packageName: String, text: String, now: Long): Boolean =
        reserveWithReason(packageName, text, now) == Reservation.ACCEPTED

    @Synchronized
    fun reserveWithReason(packageName: String, text: String, now: Long): Reservation {
        attempts.entries.removeAll { now - it.value >= ConfigCatalog.AUTO_ACCOUNTING_REPEAT_MS }
        val key = fingerprint("$packageName|${text.trim()}")
        if (attempts.containsKey(key)) return Reservation.REPEATED_PAGE
        if (lastAttempt?.let { now - it < ConfigCatalog.AUTO_ACCOUNTING_MIN_INTERVAL_MS } == true) return Reservation.MIN_INTERVAL
        while (attempts.size >= ConfigCatalog.AUTO_ACCOUNTING_MAX_NODES) attempts.remove(attempts.keys.first())
        attempts[key] = now
        lastAttempt = now
        return Reservation.ACCEPTED
    }

    /** 仅保留规则判断，不含页面原文，供日常诊断日志使用。 */
    data class ScreenCheck(val supported: Boolean, val editable: Boolean, val excludedMarker: String?, val success: Boolean, val amount: Boolean) {
        val eligible get() = supported && !editable && excludedMarker == null && success && amount
    }

    companion object {
        const val WECHAT = "com.tencent.mm"
        const val ALIPAY = "com.eg.android.AlipayGphone"
        fun enabled(settings: MySettings) = settings.automaticAccountingEnabled
        fun supports(packageName: String) = packageName == WECHAT || packageName == ALIPAY
        // 购物 App 内嵌支付仍属于购物包；只扩大用户主动开启的诊断，不启用这些包的自动识别。
        val diagnosticPackages = setOf(WECHAT, ALIPAY, "com.xunmeng.pinduoduo", "com.taobao.taobao", "com.jingdong.app.mall")
        fun supportsDiagnostics(packageName: String) = packageName in diagnosticPackages
        /** 详情触发信号可以宽松，但历史账单入库不容许多笔、部分解析失败或缺失真实时间。 */
        fun acceptsDetailResult(bills: List<AccountingDraft>, issues: List<String>): Boolean {
            val bill = bills.singleOrNull() ?: return false
            return issues.isEmpty() && AccountingRecognitionMapper.automaticInput(bill, useCurrentTimeForMissing = false) != null
        }
        fun fingerprint(text: String): String = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }

        fun matchesScreen(packageName: String, texts: List<String>, editable: Boolean): Boolean =
            inspectScreen(packageName, texts, editable).eligible

        fun inspectScreen(packageName: String, texts: List<String>, editable: Boolean): ScreenCheck {
            val text = texts.joinToString("\n")
            // 此处只匹配支付完成现场；详情页由 PaymentDetailPolicy 独立触发，禁止回填当前时间。
            val excluded = listOf("全部账单", "收支统计", "账单详情", "交易详情", "交易记录", "支付失败", "付款失败", "等待付款", "待付款", "确认付款", "输入密码").firstOrNull(text::contains)
            val success = texts.any { it.trim() in setOf("支付成功", "付款成功", "交易成功", "收款成功", "转账成功", "已收款", "已收钱", "收钱到账") }
            val amount = Regex("(?:[¥￥]\\s*[0-9]+(?:\\.[0-9]{1,2})?|[0-9]+(?:\\.[0-9]{1,2})?\\s*元)").containsMatchIn(text)
            return ScreenCheck(supports(packageName), editable, excluded, success, amount)
        }
    }
}
