package com.antgskds.calendarassistant.feature.accounting.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.antgskds.calendarassistant.app.ui.theme.material.SectionTitleTextStyle
import com.antgskds.calendarassistant.shared.ui.interaction.rememberAppHaptics
import com.antgskds.calendarassistant.shared.ui.material.component.InlineSummaryAction
import com.antgskds.calendarassistant.shared.ui.material.component.InlineSummaryHeader
import java.time.LocalDate

/** 从导航注入的真实账单生成摘要，写入操作仍由独立入库入口负责。 */
@Composable
fun AccountingSummaryHeader(
    date: LocalDate,
    title: String,
    modifier: Modifier = Modifier,
    leadingActions: List<InlineSummaryAction> = emptyList(),
    showAccounting: Boolean = true,
    monthly: Boolean = false,
    titleFontWeight: FontWeight = FontWeight.Bold,
    summaryFontWeight: FontWeight = FontWeight.Normal,
    markerColor: Color = MaterialTheme.colorScheme.primary,
    hapticEnabled: Boolean = true,
    onOpen: (LocalDate, Boolean) -> Unit,
) {
    val haptics = rememberAppHaptics(hapticEnabled)
    val entries = LocalAccountingEntries.current
    val bills = remember(entries) { AccountingPreviewData.bills(entries) }
    val range = AccountingPreviewData.range(date, if (monthly) PreviewPeriod.MONTH else PreviewPeriod.DAY)
    val expense = AccountingPreviewData.sum(bills, range)
    val wallet = if (showAccounting || expense > 0) listOf(
        InlineSummaryAction(
            text = if (expense > 0) "支出 ${AccountingPreviewData.money(expense)}" else "暂无消费记录",
            icon = rememberVectorPainter(Icons.Outlined.AccountBalanceWallet),
            iconDescription = "记账",
            iconSize = 20.sp,
            onClick = { haptics.click(); onOpen(date, monthly) },
        )
    ) else emptyList()
    InlineSummaryHeader(
        title = title,
        actions = leadingActions + wallet,
        style = SectionTitleTextStyle.copy(fontWeight = summaryFontWeight),
        titleFontWeight = titleFontWeight,
        modifier = modifier,
        markerColor = markerColor,
    )
}
