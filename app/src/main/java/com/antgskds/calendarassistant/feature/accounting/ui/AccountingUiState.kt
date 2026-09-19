package com.antgskds.calendarassistant.feature.accounting.ui

import androidx.compose.runtime.staticCompositionLocalOf
import com.antgskds.calendarassistant.feature.accounting.data.AccountingEntry

/** 由导航装配账单查询结果，首页摘要不直接获取 App 或 DAO。 */
val LocalAccountingEntries = staticCompositionLocalOf<List<AccountingEntry>> { emptyList() }

/** Activity 装配，共用记账页与备份页的账单读写状态。 */
val LocalAccountingViewModel = staticCompositionLocalOf<AccountingViewModel> { error("账单状态未装配") }
