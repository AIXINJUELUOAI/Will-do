package com.antgskds.calendarassistant.feature.accounting

import com.antgskds.calendarassistant.feature.accounting.application.AccountingDuplicateConfirmation
import com.antgskds.calendarassistant.feature.accounting.data.AccountingDraft
import com.antgskds.calendarassistant.feature.accounting.domain.*
import com.antgskds.calendarassistant.feature.notification.model.NotificationAction
import com.antgskds.calendarassistant.feature.notification.model.NotificationDisplaySnapshot
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.template.AccountingRecognitionDisplay
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.vendor.xiaomi.XiaomiLiveNotificationTemplate
import com.antgskds.calendarassistant.shared.management.resource.notification.display.live.vendor.xiaomi.XiaomiLiveTemplateKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AccountingDuplicateConfirmationTest {
    private fun draft(id: String) = AccountingDraft(id = id, amount = "35.00", direction = "EXPENSE", currency = "CNY",
        merchant = "午饭", occurredAt = "2026-10-06 12:00:00", zoneId = "Asia/Shanghai",
        paymentStatus = "COMPLETED", note = AccountingRecognitionMapper.POSSIBLE_DUPLICATE_NOTE)

    @Test fun ordinaryAndCapsuleActionsCarryOnlyThePersistedSuspectedDrafts() {
        val result = AccountingRecognitionResult(duplicates = 1, suspectedDuplicates = 2, pending = 1,
            suspectedDraftIds = listOf("first", "second"))
        val normal = requireNotNull(AccountingDuplicateAction.create(result.suspectedDraftIds))
        val direct = AccountingRecognitionDisplay.create(result)
        val routed = AccountingRecognitionDisplay.notification(NotificationDisplaySnapshot(direct.shortText, direct.primaryText), listOf(normal))
        for (live in listOf(direct, routed)) {
            val action = live.effectiveActions.single()
            assertEquals("仍然计入", action.label)
            assertEquals(normal, NotificationAction(action.receiverAction, action.label, action.stringExtras))
            assertEquals(listOf("first", "second"), AccountingDuplicateAction.draftIds(action.stringExtras[AccountingDuplicateAction.EXTRA_DRAFT_IDS]))
            val island = XiaomiLiveNotificationTemplate.create(live, true, true, false, null, 1L, 2L)
            assertEquals(XiaomiLiveTemplateKind.TEXT_ICON_ACTION, island.templateKind)
        }
        assertTrue(AccountingRecognitionDisplay.create(AccountingRecognitionResult(duplicates = 1, pending = 1)).effectiveActions.isEmpty())
        assertTrue(AccountingRecognitionDisplay.create(AccountingRecognitionResult(suspectedDuplicates = 1)).effectiveActions.isEmpty())
    }

    @Test fun actionPayloadRejectsMalformedInputAndRemovesRepeatedIds() {
        val action = requireNotNull(AccountingDuplicateAction.create(listOf("first", "", "first", "second")))
        assertEquals(listOf("first", "second"), AccountingDuplicateAction.draftIds(action.payload[AccountingDuplicateAction.EXTRA_DRAFT_IDS]))
        assertNull(AccountingDuplicateAction.create(emptyList()))
        assertTrue(AccountingDuplicateAction.draftIds(null).isEmpty())
        assertTrue(AccountingDuplicateAction.draftIds("{invalid}").isEmpty())
    }

    @Test fun confirmationReReadsOnlyRequestedDraftsAndRepeatedClicksDoNotWriteAgain() = runBlocking {
        val current = mutableListOf(draft("other"), draft("first"), draft("second"))
        val writes = mutableListOf<String>()
        suspend fun confirm(ids: List<String>) = AccountingDuplicateConfirmation.confirm(ids, { current.toList() }) { id, input ->
            val draft = current.single { it.id == id }
            assertTrue(input.allowPossibleDuplicate)
            assertFalse(input.note.contains(AccountingRecognitionMapper.POSSIBLE_DUPLICATE_NOTE))
            assertEquals("2026-10-06", input.date.toString())
            writes += id
            current.remove(draft)
            AccountingConfirmationResult(AccountingRecognitionMapper.build(draft, input, draft.createdAt), false)
        }
        assertEquals(1, confirm(listOf("first", "first", "already-removed")))
        assertEquals(0, confirm(listOf("first")))
        assertEquals(listOf("first"), writes)
        assertEquals(listOf("other", "second"), current.map { it.id })
    }

    @Test fun incompleteUnmarkedAndExactDuplicatesAreNotCountedAsNewEntries() = runBlocking {
        val drafts = listOf(draft("valid"), draft("missing-time").copy(occurredAt = ""),
            draft("unmarked").copy(note = ""), draft("unpaid").copy(paymentStatus = "UNPAID"))
        val writes = mutableListOf<String>()
        val count = AccountingDuplicateConfirmation.confirm(drafts.map { it.id }, { drafts }) { id, input ->
            writes += id
            AccountingConfirmationResult(AccountingRecognitionMapper.build(drafts.single { it.id == id }, input, 1L), duplicate = true)
        }
        assertEquals(listOf("valid"), writes)
        assertEquals(0, count)
    }

    @Test fun storageFailureIsReportedInsteadOfClaimingSuccess() {
        assertThrows(IllegalStateException::class.java) {
            runBlocking {
                AccountingDuplicateConfirmation.confirm(listOf("first"), { listOf(draft("first")) }) { _, _ ->
                    error("storage failed")
                }
            }
        }
    }
}
