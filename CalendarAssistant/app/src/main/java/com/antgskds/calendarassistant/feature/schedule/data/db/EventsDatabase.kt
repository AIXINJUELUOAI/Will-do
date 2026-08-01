package com.antgskds.calendarassistant.feature.schedule.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.antgskds.calendarassistant.feature.schedule.data.db.dao.EventAttachmentsDao
import com.antgskds.calendarassistant.feature.schedule.data.db.dao.EventTypesDao
import com.antgskds.calendarassistant.feature.schedule.data.db.dao.EventsDao
import com.antgskds.calendarassistant.feature.cloudsync.data.local.SyncV2AssetLinkEntity
import com.antgskds.calendarassistant.feature.cloudsync.data.local.SyncV2BindingEntity
import com.antgskds.calendarassistant.feature.cloudsync.data.local.SyncV2Dao
import com.antgskds.calendarassistant.feature.cloudsync.data.local.SyncV2MetaEntity
import com.antgskds.calendarassistant.feature.cloudsync.data.local.SyncV2PeerEntity
import com.antgskds.calendarassistant.feature.cloudsync.data.local.SyncV2RevisionEntity
import com.antgskds.calendarassistant.feature.schedule.domain.calendar.REGULAR_EVENT_TYPE_ID
import com.antgskds.calendarassistant.feature.schedule.domain.model.Event
import com.antgskds.calendarassistant.feature.schedule.domain.model.EventAttachment
import com.antgskds.calendarassistant.feature.schedule.domain.model.EventType
import com.antgskds.calendarassistant.feature.note.data.local.NoteEntity
import com.antgskds.calendarassistant.feature.note.data.local.NotesDao
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoDao
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoEntity
import com.antgskds.calendarassistant.feature.quickmemo.data.local.QuickMemoSuggestionEntity
import java.util.concurrent.Executors

@Database(
    entities = [
        Event::class,
        EventType::class,
        EventAttachment::class,
        NoteEntity::class,
        QuickMemoEntity::class,
        QuickMemoSuggestionEntity::class,
        SyncV2BindingEntity::class,
        SyncV2RevisionEntity::class,
        SyncV2AssetLinkEntity::class,
        SyncV2PeerEntity::class,
        SyncV2MetaEntity::class
    ],
    version = 12,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class EventsDatabase : RoomDatabase() {

    abstract fun eventsDao(): EventsDao
    abstract fun eventTypesDao(): EventTypesDao
    abstract fun eventAttachmentsDao(): EventAttachmentsDao
    abstract fun notesDao(): NotesDao
    abstract fun quickMemoDao(): QuickMemoDao
    abstract fun syncV2Dao(): SyncV2Dao

    companion object {
        @Volatile
        private var db: EventsDatabase? = null

        fun getInstance(context: Context): EventsDatabase {
            return db ?: synchronized(this) {
                db ?: Room.databaseBuilder(
                    context.applicationContext,
                    EventsDatabase::class.java,
                    "events.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12).addCallback(object : Callback() {
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

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_v2_bindings (record_key TEXT NOT NULL PRIMARY KEY, entity_type TEXT NOT NULL, sync_uuid TEXT NOT NULL, local_id INTEGER, selected_revision_id TEXT NOT NULL, observed_payload_hash TEXT NOT NULL)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_v2_bindings_entity_type_local_id ON sync_v2_bindings(entity_type, local_id)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sync_v2_bindings_entity_type_sync_uuid ON sync_v2_bindings(entity_type, sync_uuid)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_v2_revisions (revision_id TEXT NOT NULL PRIMARY KEY, record_key TEXT NOT NULL, entity_type TEXT NOT NULL, sync_uuid TEXT NOT NULL, parent_revision_ids_json TEXT NOT NULL, version_vector_json TEXT NOT NULL, modified_at INTEGER NOT NULL, modified_by_device_id TEXT NOT NULL, status TEXT NOT NULL, payload_json TEXT NOT NULL, attachments_json TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_v2_revisions_record_key ON sync_v2_revisions(record_key)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_v2_revisions_sync_uuid ON sync_v2_revisions(sync_uuid)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_v2_asset_links (attachment_uuid TEXT NOT NULL PRIMARY KEY, record_key TEXT NOT NULL, role TEXT NOT NULL, local_attachment_id INTEGER, local_path TEXT NOT NULL, asset_key TEXT NOT NULL, display_name TEXT NOT NULL, mime_type TEXT NOT NULL, plain_size INTEGER NOT NULL, plain_sha256 TEXT NOT NULL, source TEXT NOT NULL, created_at INTEGER NOT NULL, file_last_modified INTEGER NOT NULL, download_pending INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_v2_asset_links_record_key ON sync_v2_asset_links(record_key)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_v2_asset_links_local_attachment_id ON sync_v2_asset_links(local_attachment_id)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_v2_peers (device_id TEXT NOT NULL PRIMARY KEY, etag TEXT NOT NULL, generation INTEGER NOT NULL, last_seen_at INTEGER NOT NULL)")
                db.execSQL("CREATE TABLE IF NOT EXISTS sync_v2_meta (`key` TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)")
            }
        }
    }
}
