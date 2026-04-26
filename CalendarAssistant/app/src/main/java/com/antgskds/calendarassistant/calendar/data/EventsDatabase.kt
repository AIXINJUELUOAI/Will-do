package com.antgskds.calendarassistant.calendar.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.antgskds.calendarassistant.calendar.data.dao.EventTypesDao
import com.antgskds.calendarassistant.calendar.data.dao.EventsDao
import com.antgskds.calendarassistant.calendar.helpers.REGULAR_EVENT_TYPE_ID
import com.antgskds.calendarassistant.calendar.models.Event
import com.antgskds.calendarassistant.calendar.models.EventType
import java.util.concurrent.Executors

@Database(entities = [Event::class, EventType::class], version = 3, exportSchema = false)
@TypeConverters(Converters::class)
abstract class EventsDatabase : RoomDatabase() {

    abstract fun eventsDao(): EventsDao
    abstract fun eventTypesDao(): EventTypesDao

    companion object {
        @Volatile
        private var db: EventsDatabase? = null

        fun getInstance(context: Context): EventsDatabase {
            return db ?: synchronized(this) {
                db ?: Room.databaseBuilder(
                    context.applicationContext,
                    EventsDatabase::class.java,
                    "events.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).addCallback(object : Callback() {
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
    }
}
