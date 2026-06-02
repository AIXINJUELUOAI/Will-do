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
import java.util.concurrent.Executors

@Database(entities = [Event::class, EventType::class, EventAttachment::class, NoteEntity::class], version = 7, exportSchema = false)
@TypeConverters(Converters::class)
abstract class EventsDatabase : RoomDatabase() {

    abstract fun eventsDao(): EventsDao
    abstract fun eventTypesDao(): EventTypesDao
    abstract fun eventAttachmentsDao(): EventAttachmentsDao
    abstract fun notesDao(): NotesDao

    companion object {
        @Volatile
        private var db: EventsDatabase? = null

        fun getInstance(context: Context): EventsDatabase {
            return db ?: synchronized(this) {
                db ?: Room.databaseBuilder(
                    context.applicationContext,
                    EventsDatabase::class.java,
                    "events.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7).addCallback(object : Callback() {
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
    }
}
