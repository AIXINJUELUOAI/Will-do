package com.antgskds.calendarassistant.core.quickmemo

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

object QuickMemoType {
    const val TEXT = "TEXT"
    const val VOICE = "VOICE"
}

object QuickMemoTranscriptionStatus {
    const val NONE = "NONE"
    const val PENDING = "PENDING"
    const val PROCESSING = "PROCESSING"
    const val SUCCESS = "SUCCESS"
    const val FAILED = "FAILED"
}

object QuickMemoAnalysisStatus {
    const val NONE = "NONE"
    const val PENDING = "PENDING"
    const val PROCESSING = "PROCESSING"
    const val SUCCESS = "SUCCESS"
    const val FAILED = "FAILED"
}

object QuickMemoTodoState {
    const val NONE = "NONE"
    const val ACTIVE = "ACTIVE"
    const val COMPLETED = "COMPLETED"
}

object QuickMemoSuggestionType {
    const val SCHEDULE = "SCHEDULE"
}

object QuickMemoSuggestionStatus {
    const val PENDING = "PENDING"
    const val CREATED = "CREATED"
    const val DISMISSED = "DISMISSED"
    const val FAILED = "FAILED"
}

@Entity(
    tableName = "quick_memos",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["updated_at"]),
        Index(value = ["todo_state"]),
        Index(value = ["type"])
    ]
)
data class QuickMemoEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
    @ColumnInfo(name = "type")
    val type: String = QuickMemoType.TEXT,
    @ColumnInfo(name = "body_text")
    val bodyText: String = "",
    @ColumnInfo(name = "audio_path")
    val audioPath: String? = null,
    @ColumnInfo(name = "audio_duration_ms")
    val audioDurationMs: Long = 0L,
    @ColumnInfo(name = "transcription_status")
    val transcriptionStatus: String = QuickMemoTranscriptionStatus.NONE,
    @ColumnInfo(name = "analysis_status")
    val analysisStatus: String = QuickMemoAnalysisStatus.NONE,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "todo_state")
    val todoState: String = QuickMemoTodoState.NONE,
    @ColumnInfo(name = "todo_pending_until")
    val todoPendingUntil: Long? = null,
    @ColumnInfo(name = "todo_completed_at")
    val todoCompletedAt: Long? = null
) {
    val isVoice: Boolean get() = type == QuickMemoType.VOICE
    val isTodo: Boolean get() = todoState == QuickMemoTodoState.ACTIVE || todoState == QuickMemoTodoState.COMPLETED
    val isTodoCompleted: Boolean get() = todoState == QuickMemoTodoState.COMPLETED
}

@Entity(
    tableName = "quick_memo_suggestions",
    foreignKeys = [
        ForeignKey(
            entity = QuickMemoEntity::class,
            parentColumns = ["id"],
            childColumns = ["quick_memo_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["quick_memo_id"]),
        Index(value = ["status"]),
        Index(value = ["type"]),
        Index(value = ["event_id"])
    ]
)
data class QuickMemoSuggestionEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long? = null,
    @ColumnInfo(name = "quick_memo_id")
    val quickMemoId: Long,
    @ColumnInfo(name = "type")
    val type: String = QuickMemoSuggestionType.SCHEDULE,
    @ColumnInfo(name = "status")
    val status: String = QuickMemoSuggestionStatus.PENDING,
    @ColumnInfo(name = "candidate_json")
    val candidateJson: String = "",
    @ColumnInfo(name = "event_id")
    val eventId: Long? = null,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)
