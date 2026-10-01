package com.antgskds.calendarassistant.feature.accounting.domain

import com.antgskds.calendarassistant.feature.settings.data.model.MySettings
import com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft
import java.security.MessageDigest

/** 自动记账开关、来源与页面规则；页面任务去重由 PaymentDetailPolicy 统一维护。 */
class AutomaticAccountingPolicy {
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
