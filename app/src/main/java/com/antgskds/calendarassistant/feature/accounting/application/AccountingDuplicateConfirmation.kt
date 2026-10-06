package com.antgskds.calendarassistant.feature.accounting.application

import com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingConfirmationResult
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingEntryInput
import com.antgskds.calendarassistant.feature.accounting.domain.AccountingRecognitionMapper

/** 点击时重读草稿；实际确认仍由统一入库入口在事务中校验和精确排重。 */
object AccountingDuplicateConfirmation {
    suspend fun confirm(
        draftIds: List<String>,
        readDrafts: suspend () -> List<AccountingDraft>,
        confirmDraft: suspend (String, AccountingEntryInput) -> AccountingConfirmationResult,
    ): Int {
        val targets = draftIds.toSet()
        if (targets.isEmpty()) return 0
        var saved = 0
        for (draft in readDrafts().filter { it.id in targets }) {
            val input = AccountingRecognitionMapper.possibleDuplicateInput(draft) ?: continue
            val result = confirmDraft(draft.id, input)
            if (!result.duplicate && result.entry != null) saved++
        }
        return saved
    }
}
