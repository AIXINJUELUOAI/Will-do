package com.antgskds.calendarassistant.calendar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.antgskds.calendarassistant.calendar.data.dao.EventAttachmentsDao
import com.antgskds.calendarassistant.calendar.data.dao.EventTypesDao
import com.antgskds.calendarassistant.calendar.data.dao.EventsDao
import com.antgskds.calendarassistant.calendar.helpers.REGULAR_EVENT_TYPE_ID
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.EventAttachment
import com.antgskds.calendarassistant.calendar.models.EventType
import com.antgskds.calendarassistant.core.note.NoteEntity
import com.antgskds.calendarassistant.core.note.NotesDao
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoDao
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoEntity
import com.antgskds.calendarassistant.core.quickmemo.QuickMemoSuggestionEntity
import java.util.concurrent.Executors

@Database(
    entities = [
        Event::class,
        EventType::class,
        EventAttachment::class,
        NoteEntity::class,
        QuickMemoEntity::class,
        QuickMemoSuggestionEntity::class
    ],
    version = 11,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class EventsDatabase : RoomDatabase() {

    abstract fun eventsDao(): EventsDao
    abstract fun eventTypesDao(): EventTypesDao
    abstract fun eventAttachmentsDao(): EventAttachmentsDao
    abstract fun notesDao(): NotesDao
    abstract fun quickMemoDao(): QuickMemoDao

    companion object {
        @Volatile
        private var db: EventsDatabase? = null

        fun getInstance(context: Context): EventsDatabase {
            return db ?: synchronized(this) {
                db ?: Room.databaseBuilder(
                    context.applicationContext,
                    EventsDatabase::class.java,
                    "events.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11).addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        insertRegularEventType(context)
                    }
                }).build().also { db = it }
            }
        }

        private fun insertRegularEventType(context: Context) {
            Executors.newSingleThreadExecutor().execute {
                val database = getInstance(context)
                val defaultType = EventType(
                    id = REGULAR_EVENT_TYPE_ID,
                    title = "Regular",
                    color = 0xFF3F51B5.toInt()
                )
                database.eventTypesDao().insertOrUpdate(defaultType)
            }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN state INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE events ADD COLUMN tag TEXT NOT NULL DEFAULT 'general'")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN archived_at INTEGER DEFAULT NULL")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS event_attachments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        event_id INTEGER NOT NULL,
                        local_path TEXT NOT NULL,
                        display_name TEXT NOT NULL DEFAULT '',
                        mime_type TEXT NOT NULL DEFAULT '',
                        size_bytes INTEGER NOT NULL DEFAULT 0,
                        source TEXT NOT NULL DEFAULT 'manual',
                        created_at INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY(event_id) REFERENCES events(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_event_attachments_event_id ON event_attachments(event_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_event_attachments_local_path ON event_attachments(local_path)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS event_attachments_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        event_id INTEGER,
                        event_key TEXT NOT NULL DEFAULT '',
                        local_path TEXT NOT NULL,
                        display_name TEXT NOT NULL DEFAULT '',
                        mime_type TEXT NOT NULL DEFAULT '',
                        size_bytes INTEGER NOT NULL DEFAULT 0,
                        source TEXT NOT NULL DEFAULT 'manual',
                        created_at INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO event_attachments_new (
                        id, event_id, event_key, local_path, display_name, mime_type, size_bytes, source, created_at
                    )
                    SELECT id, event_id, '', local_path, display_name, mime_type, size_bytes, source, created_at
                    FROM event_attachments
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE event_attachments")
                db.execSQL("ALTER TABLE event_attachments_new RENAME TO event_attachments")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_event_attachments_event_id ON event_attachments(event_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_event_attachments_event_key ON event_attachments(event_key)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_event_attachments_local_path ON event_attachments(local_path)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS notes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        title TEXT NOT NULL DEFAULT '',
                        plain_text TEXT NOT NULL DEFAULT '',
                        document_json TEXT NOT NULL DEFAULT '',
                        created_at INTEGER NOT NULL DEFAULT 0,
                        updated_at INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_updated_at ON notes(updated_at)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN pinned_at INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_notes_pinned_at ON notes(pinned_at)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS quick_memos (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        type TEXT NOT NULL,
                        body_text TEXT NOT NULL,
                        audio_path TEXT,
                        audio_duration_ms INTEGER NOT NULL,
                        transcription_status TEXT NOT NULL,
                        analysis_status TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        todo_state TEXT NOT NULL,
                        todo_pending_until INTEGER,
                        todo_completed_at INTEGER
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_quick_memos_created_at ON quick_memos(created_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_quick_memos_updated_at ON quick_memos(updated_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_quick_memos_todo_state ON quick_memos(todo_state)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_quick_memos_type ON quick_memos(type)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS quick_memo_suggestions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        quick_memo_id INTEGER NOT NULL,
                        type TEXT NOT NULL,
                        status TEXT NOT NULL,
                        candidate_json TEXT NOT NULL,
                        event_id INTEGER,
                        created_at INTEGER NOT NULL,
                        updated_at INTEGER NOT NULL,
                        FOREIGN KEY(quick_memo_id) REFERENCES quick_memos(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_quick_memo_suggestions_quick_memo_id ON quick_memo_suggestions(quick_memo_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_quick_memo_suggestions_status ON quick_memo_suggestions(status)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_quick_memo_suggestions_type ON quick_memo_suggestions(type)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_quick_memo_suggestions_event_id ON quick_memo_suggestions(event_id)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE quick_memos ADD COLUMN sort_rank INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    UPDATE quick_memos
                    SET sort_rank = (
                        SELECT COUNT(*)
                        FROM quick_memos AS other
                        WHERE
                            CASE other.todo_state
                                WHEN 'ACTIVE' THEN 0
                                WHEN 'NONE' THEN 1
                                WHEN 'COMPLETED' THEN 2
                                ELSE 1
                            END < CASE quick_memos.todo_state
                                WHEN 'ACTIVE' THEN 0
                                WHEN 'NONE' THEN 1
                                WHEN 'COMPLETED' THEN 2
                                ELSE 1
                            END
                            OR (
                                CASE other.todo_state
                                    WHEN 'ACTIVE' THEN 0
                                    WHEN 'NONE' THEN 1
                                    WHEN 'COMPLETED' THEN 2
                                    ELSE 1
                                END = CASE quick_memos.todo_state
                                    WHEN 'ACTIVE' THEN 0
                                    WHEN 'NONE' THEN 1
                                    WHEN 'COMPLETED' THEN 2
                                    ELSE 1
                                END
                                AND (
                                    other.updated_at > quick_memos.updated_at
                                    OR (other.updated_at = quick_memos.updated_at AND other.id > quick_memos.id)
                                )
                            )
                    ) * 1000
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_quick_memos_sort_rank ON quick_memos(sort_rank)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE quick_memos ADD COLUMN image_path TEXT")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE events ADD COLUMN code_qr_payload TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
