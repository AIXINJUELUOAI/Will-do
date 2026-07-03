package com.antgskds.calendarassistant.core.rule

import android.content.Context
import android.util.Log
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.core.ai.AnalysisResult
import com.antgskds.calendarassistant.core.ai.convertDraftToEvent
import com.antgskds.calendarassistant.core.center.ScheduleCenter
import com.antgskds.calendarassistant.core.model.RecognitionDraft
import com.antgskds.calendarassistant.data.model.MySettings
import com.antgskds.calendarassistant.data.node.recognition.RecognitionTextNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class RegexAiReviewCoordinator(
    private val appContext: Context,
    private val scheduleCenter: ScheduleCenter,
    private val appScope: CoroutineScope,
) {
    companion object {
        private const val TAG = "RegexAiReview"
        private const val MATCH_TIME_TOLERANCE_SECONDS = 60L
    }

    data class ReviewContext(
        val rawText: String,
        val regexDraft: RecognitionDraft,
        val settings: MySettings,
    )

    fun reviewAfterIngest(
        traceId: String,
        eventId: Long,
        context: ReviewContext,
    ) {
        appScope.launch {
            runCatching {
                reviewAfterIngestInternal(traceId, eventId, context)
            }.onFailure { error ->
                Log.w(TAG, "AI 后台修正失败: traceId=$traceId, eventId=$eventId", error)
            }
        }
    }

    private suspend fun reviewAfterIngestInternal(
        traceId: String,
        eventId: Long,
        context: ReviewContext,
    ) {
        val before = findEvent(eventId) ?: run {
            Log.d(TAG, "跳过 AI 修正: 事件不存在 traceId=$traceId, eventId=$eventId")
            return
        }
        val expected = convertDraftToEvent(
            draft = context.regexDraft,
            defaultDurationMinutes = context.settings.defaultEventDurationMinutes,
            forceInstantCodeTimeToNow = context.settings.forceInstantCodeTimeToNow,
            eventColorPaletteHex = context.settings.eventColorPaletteHex,
        ).copy(id = eventId, color = before.color)
        if (!isSameEventForReview(before, expected)) {
            Log.d(TAG, "跳过 AI 修正: 用户可能已修改事件 traceId=$traceId, eventId=$eventId")
            return
        }

        val aiResult = RecognitionTextNode.analyzeTextEventsByAi(context.rawText, context.settings, appContext)
        val aiDraft = when (aiResult) {
            is AnalysisResult.Success -> aiResult.data.singleOrNull()
            is AnalysisResult.Empty -> null
            is AnalysisResult.Failure -> null
        } ?: run {
            Log.d(TAG, "跳过 AI 修正: AI 未返回单条结果 traceId=$traceId, eventId=$eventId")
            return
        }

        val aiEvent = convertDraftToEvent(
            draft = aiDraft,
            defaultDurationMinutes = context.settings.defaultEventDurationMinutes,
            forceInstantCodeTimeToNow = context.settings.forceInstantCodeTimeToNow,
            eventColorPaletteHex = context.settings.eventColorPaletteHex,
        ).copy(id = eventId, color = before.color)
        if (isSameEventForReview(before, aiEvent)) {
            Log.d(TAG, "AI 修正无需更新: traceId=$traceId, eventId=$eventId")
            return
        }

        val latest = findEvent(eventId) ?: return
        if (!isSameEventForReview(latest, before)) {
            Log.d(TAG, "跳过 AI 修正: AI 返回前事件已变化 traceId=$traceId, eventId=$eventId")
            return
        }

        scheduleCenter.updateEvent(
            latest.copy(
                title = aiEvent.title,
                startTS = aiEvent.startTS,
                endTS = aiEvent.endTS,
                location = aiEvent.location,
                description = aiEvent.description,
                timeZone = aiEvent.timeZone,
                tag = aiEvent.tag,
            )
        )
        Log.d(TAG, "AI 修正已更新事件: traceId=$traceId, eventId=$eventId")
    }

    private suspend fun findEvent(eventId: Long): Event? = withContext(Dispatchers.IO) {
        scheduleCenter.getLatestActiveEvents().firstOrNull { it.id == eventId }
    }

    private fun isSameEventForReview(left: Event, right: Event): Boolean {
        return left.title.trim() == right.title.trim() &&
            abs(left.startTS - right.startTS) <= MATCH_TIME_TOLERANCE_SECONDS &&
            abs(left.endTS - right.endTS) <= MATCH_TIME_TOLERANCE_SECONDS &&
            left.location.trim() == right.location.trim() &&
            left.description.trim() == right.description.trim() &&
            left.tag.trim() == right.tag.trim()
    }
}
