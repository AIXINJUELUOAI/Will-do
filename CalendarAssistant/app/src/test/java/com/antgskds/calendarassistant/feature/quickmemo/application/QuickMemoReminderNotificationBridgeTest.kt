package com.antgskds.calendarassistant.feature.quickmemo.application

import com.antgskds.calendarassistant.feature.notification.api.NotificationApi
import com.antgskds.calendarassistant.feature.notification.model.*
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class QuickMemoReminderNotificationBridgeTest {
    @Test fun registersBeforePublishingAndKeepsMemoTargetAndRepeatAlerts() = runBlocking {
        val api = RecordingApi()
        val result = QuickMemoReminderNotificationBridge(api).publish(QuickMemoEntity(id = 42, bodyText = "喝水"), 7)
        assertTrue(result is NotificationResult.Success)
        assertEquals(listOf("create", "trigger"), api.calls)
        val request = requireNotNull(api.request)
        assertEquals(NotificationKind.QUICK_MEMO_REMINDER, request.kind)
        assertEquals(NotificationRoute.AUTO, request.route)
        assertEquals("喝水", request.display.expandedText)
        assertEquals(NotificationTapTargetType.QUICK_MEMO_DETAIL, request.tapTarget?.type)
        assertEquals("42", request.tapTarget?.payload?.get("quickMemoId"))
        assertFalse(request.behavior.onlyAlertOnce)
        assertNull(request.behavior.triggerAtEpochMillis)
        assertEquals(request.key, (api.trigger as NotificationTrigger.ByKey).key)
    }

    @Test fun registrationFailureDoesNotTriggerAndPublishFailureIsReturned() = runBlocking {
        val failure = NotificationResult.Failure(reason = NotificationFailureReason.STORAGE_FAILED)
        val api = RecordingApi(createFailure = failure)
        assertSame(failure, QuickMemoReminderNotificationBridge(api).publish(QuickMemoEntity(id = 1), 2))
        assertEquals(listOf("create"), api.calls)

        val denied = NotificationResult.Failure(reason = NotificationFailureReason.PERMISSION_DENIED)
        val deniedApi = RecordingApi(publishFailure = denied)
        assertSame(denied, QuickMemoReminderNotificationBridge(deniedApi).publish(QuickMemoEntity(id = 1), 2))
    }

    private class RecordingApi(
        private val createFailure: NotificationResult.Failure? = null,
        private val publishFailure: NotificationResult.Failure? = null,
    ) : NotificationApi {
        val calls = mutableListOf<String>()
        var request: NotificationRequest? = null
        var trigger: NotificationTrigger? = null
        override suspend fun create(request: NotificationRequest): NotificationResult {
            calls += "create"
            this.request = request
            return createFailure ?: NotificationResult.Success(request.key, NotificationState.READY)
        }
        override suspend fun trigger(trigger: NotificationTrigger): NotificationResult {
            calls += "trigger"
            this.trigger = trigger
            return publishFailure ?: NotificationResult.Success(requireNotNull(request).key, NotificationState.POSTED)
        }
        override suspend fun update(request: NotificationRequest): NotificationResult = error("Unexpected update")
        override suspend fun cancel(key: NotificationKey): NotificationResult = error("Unexpected cancel")
        override suspend fun get(key: NotificationKey): NotificationSnapshot? = error("Unexpected get")
        override suspend fun list(query: NotificationQuery): List<NotificationSnapshot> = error("Unexpected list")
    }
}
